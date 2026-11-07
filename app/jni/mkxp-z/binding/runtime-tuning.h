#ifndef RUNTIME_TUNING_H
#define RUNTIME_TUNING_H

/*
 * AnyPlay: per-runtime tuning that does not belong to the Ruby 1.8 compatibility
 * layer (legacy-ruby.h).
 *
 *  - runtimeTuneEnvironment(): environment variables Ruby reads while it starts.
 *    Must be called before ruby_init() / ruby_options(). Values are only defaults
 *    (setenv overwrite=0), a developer can still override them from the environment.
 *      r3 : RUBY_THREAD_MACHINE_STACK_SIZE = 16 MB. Ruby 3.1 threads get a small
 *           machine stack by default; big Essentials games overflow it. (Kirin does
 *           the same, for r3 only.)
 *
 *  - runtimeYjitFirstUpdate(): r34 with YJIT only (-DMKXPZ_YJIT). Ruby 3.4 can switch
 *    YJIT on while it runs (RubyVM::YJIT.enable). It is switched on at the FIRST
 *    Graphics.update, i.e. after all game scripts are loaded and the game is really
 *    running, so YJIT does not spend time compiling start-up code that runs once.
 *    Everywhere else this is an empty inline function.
 */

#include <stdlib.h>
#include <string.h>

#ifndef MKXPZ_RUNTIME_ID
#define MKXPZ_RUNTIME_ID "r3"
#endif

static inline void runtimeTuneEnvironment()
{
    if (strcmp(MKXPZ_RUNTIME_ID, "r3") == 0) {
        setenv("RUBY_THREAD_MACHINE_STACK_SIZE", "16777216", 0);
    }
}

#ifdef MKXPZ_YJIT

#include <ruby.h>

/* Safe to call on every Graphics.update: only the first call does anything. Never
 * raises: a missing or failing YJIT must not stop the game. The result is left in
 * $__anyplay_yjit ("on", "off" or "unavailable: ...") for AnyPlayLog. */
static inline void runtimeYjitFirstUpdate()
{
    static bool done = false;
    if (done) return;
    done = true;

    int state = 0;
    rb_eval_string_protect(
        "begin\n"
        "  if defined?(RubyVM::YJIT) && RubyVM::YJIT.respond_to?(:enable)\n"
        "    RubyVM::YJIT.enable unless RubyVM::YJIT.enabled?\n"
        "    $__anyplay_yjit = RubyVM::YJIT.enabled? ? 'on' : 'off'\n"
        "  else\n"
        "    $__anyplay_yjit = 'unavailable: not compiled in'\n"
        "  end\n"
        "rescue Exception => e\n"
        "  $__anyplay_yjit = \"unavailable: #{e.class}: #{e.message}\"[0, 120]\n"
        "end\n",
        &state);
    if (state) rb_set_errinfo(Qnil);
}

#else

static inline void runtimeYjitFirstUpdate() {}

#endif /* MKXPZ_YJIT */

#endif /* RUNTIME_TUNING_H */
