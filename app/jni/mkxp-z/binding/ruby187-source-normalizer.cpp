/*
** ruby187-source-normalizer.cpp
**
** AnyPlay: source-level repair of Ruby 1.8-era RPG Maker scripts so that a modern
** MRI (Ruby 3.1) can parse and run them. This used to live inside runRMXPScripts()
** in binding-mri.cpp; it is moved here unchanged and is now the FALLBACK layer:
** the r18 runtime (see ruby-legacy/) handles what it can natively (String#[] byte
** numbers, Object#type, RUBY_VERSION, ...), and this normalizer keeps rewriting /
** self-healing the syntax the VM does not accept yet.
**
** Returns an Array with one (possibly rewritten) String per entry of `scriptArray`.
*/

#include "ruby187-source-normalizer.h"

#include "sharedstate.h"
#include "config.h"
#include "binding-util.h"

extern "C" {
#include <ruby.h>
#if RAPI_FULL >= 190
#include <ruby/encoding.h>
#endif
}

#include <stdio.h>

#define SCRIPT_SECTION_FMT (rgssVer >= 3 ? "{%04ld}" : "Section%03ld")

static VALUE newStringUTF8(const char *string, long length) {
    return rb_enc_str_new(string, length, rb_utf8_encoding());
}

VALUE ruby187NormalizeScripts(VALUE scriptArray, long scriptCount, const Config &conf)
{
    VALUE fixedScripts = Qnil;
    // Ruby 1.8-era games running on modern MRI: repair known syntax/API changes,
    // then report scripts that still fail the modern Ruby parser.
    {
        static VALUE compatFix = Qnil;
        if (compatFix == Qnil) {
            rb_eval_string(R"RB(
# Ruby 1.8 accepted `return` inside a top-level `proc{ ... }`; Ruby 3 raises
# LocalJumpError. Rewrite `return` (bare or before if/unless) in such procs to
# a catch/throw pair, without changing line numbers.
$__anyplay_fix_proc_return = lambda { |s|
  next s unless s.include?("return") && s =~ /\b(?:proc|Proc\.new)\s*\{/
  out = +""
  pos = 0
  while (m = s.match(/\b(?:proc|Proc\.new)\s*\{/, pos))
    open = m.end(0)
    depth = 1
    i = open
    n = s.length
    q = nil
    while i < n && depth > 0
      c = s[i]
      if q
        if c == "\\" then i += 1
        elsif c == q then q = nil
        end
      elsif c == '"' || c == "'"
        q = c
      elsif c == "#"
        i += 1 while i < n && s[i] != "\n"
        next
      elsif c == "{"
        depth += 1
      elsif c == "}"
        depth -= 1
      end
      i += 1
    end
    if depth != 0
      out << s[pos...m.end(0)]
      pos = m.end(0)
      next
    end
    close = i - 1
    body = s[open...close]
    out << s[pos...open]
    # only procs registered at top level (statement starts in column 0): inside
    # a method `return` in a proc legitimately returns from that method.
    unless defined?(in_def)
      in_def = []
      stack = []
      s.split("\n", -1).each do |ln|
        in_def << !stack.empty?
        if (md = ln.match(/\A(\s*)def\b/)) && !ln.match?(/\bend\s*\z/)
          stack << md[1].length
        elsif (md = ln.match(/\A(\s*)end\b/)) && stack.last == md[1].length
          stack.pop
        end
      end
    end
    top_level = !in_def[s[0...m.begin(0)].count("\n")]
    if top_level && body =~ /\breturn\b/ && body !~ /\bdef\s/
      last_line = body[/[^\n]*\z/]
      if last_line.include?("#")
        out << body
      else
        params = body[/\A\s*\|[^|]*\|/] || ""
        rest = body[params.length..-1]
        rest = rest.gsub(/\breturn\b(?=[ \t]*(?:if\b|unless\b|;|$|\}))/, "throw :__ap_ret")
        rest = rest.gsub(/^([ \t]*)return[ \t]+(?!if\b|unless\b)(\S)/) { "#{$1}throw :__ap_ret, #{$2}" }
        out << params << " catch(:__ap_ret){ " << rest << (last_line.strip.empty? ? "" : " ") << "}"
      end
    else
      out << body
    end
    pos = close
  end
  out << s[pos..-1]
  out
}
)RB");
            compatFix = rb_eval_string(R"RB(
lambda { |s, name|
  begin
    s = $__anyplay_fix_proc_return.call(s) if $__anyplay_fix_proc_return
    s = s.gsub(/\.new[ \t]+\(/, '.new(')
    # Games written for newer mkxp-z call Audio.bgm_play(file, volume, pitch, position);
    # their own wrapper script only takes three arguments. Accept (and ignore) a fourth.
    if s.include?("def self.bgm_play") || s.include?("def self.bgs_play")
      s = s.gsub(/def self\.(bgm|bgs|me|se)_play\(\s*(\w+)\s*,\s*(\w+)\s*=\s*100\s*,\s*(\w+)\s*=\s*100\s*\)/) do
        "def self.#{$1}_play(#{$2}, #{$3} = 100, #{$4} = 100, __ap_pos = 0)"
      end
    end
    # Ruby 1.8 allowed "when 0:" (colon instead of "then"); Ruby 1.9+ does not.
    s = s.gsub(/^([ \t]*when\b[^\n#]*?[\w)\]"'])[ \t]*:(?!:)(?=[ \t]*(?:#|\r?$))/) { "#{$1} then" }
    s = s.gsub(/^([ \t]*when\b[^\n#]*?[\w)\]"']):[ \t]+(?=[^\s#])/) { "#{$1} then " }
    s = s.gsub('\x7f-\x9f') { '\u007f-\u009f' }
    # Ruby 1.8: "str[i]" gave the byte NUMBER; newer Ruby gives a 1-letter string. Essentials'
    # StringInput#getc relied on that (Pokemon data such as the growth rate came back as text,
    # so "comparison of String with 6 failed" when a Pokemon was created).
    # Same thing for "file.read(1)[0]" (first byte as a number in Ruby 1.8)
    s = s.gsub(/\.read\(\s*(\d+)\s*\)\[0\]/) { ".read(#{$1}).getbyte(0)" } if s.include?(".read(")
    # Ruby 1.8: "x.split('').map" (no block) gave an Array; newer Ruby gives an Enumerator,
    # so x[0] fails. Only after split/scan/chars/... so attributes like "spriteset.map" stay as they are.
    s = s.gsub(/(\.(?:split|scan|each_char|each_byte)\([^()\n]*\)|\.(?:chars|bytes|keys|values|to_a))\.(?:map|collect)(?=[ \t]*(?:;|#[^\n]*)?\r?$)/) { "#{$1}.to_a" } if s.include?("map") || s.include?("collect")
    # BOM check ("line[0]==0xEF") and the MP3 header scan ("rstr[0]==0xFB"): in Ruby 1.8 str[i] was a number,
    # now it is text and never equal. __anyplay_byte gives the number for strings.
    s = s.gsub(/(?<![.\w@$\])])(line|rstr)\[(\w+)\][ \t]*(==|!=|<=|>=|<|>)[ \t]*(0x[0-9A-Fa-f]+)\b/) { "__anyplay_byte(#{$1},#{$2})#{$3}#{$4}" } if s =~ /\]\s*(?:==|!=|<=|>=|<|>)\s*0x/
    # MP3 play-time scan (Essentials AudioUtilities): the 3 bytes after the 0xFF marker are used as numbers
    s = s.gsub(/\brstr\s*=\s*file\.read\(3\)/, 'rstr = file.read(3).unpack("C*")') if s.include?("def getPlayTime2")
    if s.include?("class StringInput")
      s = s.gsub(/^([ \t]*\w+[ \t]*=[ \t]*)@string\[@pos\][ \t]*$/) { "#{$1}@string.getbyte(@pos)" }
    end

    # Self-healing: ask Ruby's parser whether the script is valid. If not, fix the
    # reported lines (only those) for known Ruby 1.8-only syntax and try again.
    if defined?(RubyVM::InstructionSequence)
      kw = %w(if elsif unless while until and or not then do case when return yield rescue in begin else ensure end super defined? puts_ )
      prefix = name.to_s + ":"
      old_verbose = $VERBOSE
      $VERBOSE = nil
      last_err = nil
      9.times do
        last_err = nil
        begin
          RubyVM::InstructionSequence.compile(s, name.to_s)
        rescue SyntaxError => e
          last_err = e.message
        rescue Exception
          break
        end
        break unless last_err
        lines = s.split("\n", -1)
        changed = false
        last_err.split("\n").each do |row|
          next unless row.start_with?(prefix)
          rest = row[prefix.length..-1]
          next unless rest =~ /\A(\d+):\s*(.*)\z/
          idx = $1.to_i - 1
          msg = $2
          next if idx < 0 || idx >= lines.size
          [idx, idx - 1].each do |k|
            next if k < 0
            ln = lines[k]
            orig = ln.dup
            # "foo (a, b)" / "Foo.new (a, b)": remove the space before the bracket
            ln = ln.gsub(/([A-Za-z_][A-Za-z0-9_]*[?!]?)[ \t]+\(/) { kw.include?($1) ? $& : "#{$1}(" }
            # "when 1, 2:" and friends
            if ln =~ /^[ \t]*when\b/ && msg =~ /unexpected ':'|unexpected `when'|unexpected '\n'/
              ln = ln.sub(/([\w)\]"'])[ \t]*:(?!:)(?=[ \t]*(?:#|$|\S))/) { "#{$1} then" }
            end
            # constants assigned inside a method (allowed in Ruby 1.8)
            if msg =~ /dynamic constant assignment/ && ln =~ /\A([ \t]*)([A-Z][A-Za-z0-9_]*)[ \t]*=[ \t]*(.+?)[ \t]*\z/
              ind, cn, val = $1, $2, $3
              ln = "#{ind}Object.const_set(:#{cn}, (#{val}))" unless val.include?("#")
            end
            # "break"/"next"/"redo"/"retry" with no loop or block around it: Ruby 1.8 only failed
            # when that line was actually run; newer Ruby refuses the whole script.
            if msg =~ /\AInvalid (break|next|redo|retry)/
              w = $1
              ln = ln.sub(/\b#{w}\b(?:[ \t]+(?!if\b|unless\b|while\b|until\b)[^#\n;]*?)?(?=[ \t]*(?:\bif\b|\bunless\b|\bend\b|#|;|\}|\)|:|$))/) { "raise(LocalJumpError, '#{w} outside loop')" }
              if ln == orig && ln =~ /\b#{w}\b/
                ln = ln.sub(/\b#{w}\b[^;#\n)]*/) { "raise(LocalJumpError, '#{w} outside loop')" }
              end
            end
            # regex/string with raw high bytes such as \xff (invalid in UTF-8 source)
            if msg =~ /invalid multibyte/
              ln = ln.gsub(/\\x([89a-fA-F][0-9a-fA-F])/) { "\\u00#{$1}" }
            end
            if ln != orig
              lines[k] = ln
              changed = true
            end
          end
        end
        break unless changed
        s = lines.join("\n")
      end
      $VERBOSE = old_verbose
      if last_err
        src_lines = s.split("\n", -1)
        detail = []
        last_err.split("\n").each do |row|
          next unless row.start_with?(prefix)
          detail << row
          if row[prefix.length..-1] =~ /\A(\d+):/
            li = $1.to_i - 1
            detail << "    >> " + src_lines[li].to_s.strip[0, 200] if li >= 0 && li < src_lines.size
          end
        end
        detail = last_err.split("\n").first(8) if detail.empty?
        ($__anyplay_syntax_errors ||= []) << "[#{name}]\n" + detail.first(24).join("\n")
      end
    end
    ($__anyplay_src ||= {})[name.to_s] = s
    s
  rescue Exception
    s
  end
}
)RB");
            rb_gc_register_address(&compatFix);
        }
        rb_eval_string("$__anyplay_syntax_errors = []");
        rb_gv_set("$__anyplay_compatfix", compatFix);
        // The repair pass (regex rewrites + a full parse of every script) costs seconds
        // on a phone and gives the same answer on every launch, so its result is kept in
        // AnyPlay_script_cache.bin, keyed by script name + content checksum.
        VALUE cachedFix = rb_eval_string(R"RB(
$__anyplay_ncache_ver = "n1-v9"
$__anyplay_ncache_t0 = Process.clock_gettime(Process::CLOCK_MONOTONIC)
$__anyplay_nhits = 0
$__anyplay_ncount = 0
$__anyplay_ncache_dirty = false
$__anyplay_ncache = begin
  d = File.exist?("AnyPlay_script_cache.bin") ? Marshal.load(File.binread("AnyPlay_script_cache.bin")) : nil
  (d.is_a?(Hash) && d[:v] == $__anyplay_ncache_ver && d[:s].is_a?(Hash)) ? d : { :v => $__anyplay_ncache_ver, :s => {} }
rescue Exception
  { :v => $__anyplay_ncache_ver, :s => {} }
end
lambda { |s, name|
  begin
    $__anyplay_ncount += 1
    key = [s.bytesize, s.sum(32), (defined?(Zlib) && Zlib.respond_to?(:crc32) ? Zlib.crc32(s) : 0), s[0, 48], s[-48, 48]]
    ent = $__anyplay_ncache[:s][name.to_s]
    if ent && ent[0] == key && ent[1].is_a?(String)
      ($__anyplay_src ||= {})[name.to_s] = ent[1]
      ($__anyplay_syntax_errors ||= []).concat(ent[2]) if ent[2].is_a?(Array) && !ent[2].empty?
      $__anyplay_nhits += 1
      next ent[1]
    end
    before = ($__anyplay_syntax_errors ||= []).size
    out = $__anyplay_compatfix.call(s, name)
    errs = $__anyplay_syntax_errors[before..-1] || []
    $__anyplay_ncache[:s][name.to_s] = [key, out, errs]
    $__anyplay_ncache_dirty = true
    out
  rescue Exception
    $__anyplay_compatfix.call(s, name)
  end
}
)RB");
        rb_gc_register_address(&cachedFix);
        fixedScripts = rb_ary_new();
        for (long i = 0; i < scriptCount; ++i) {
            VALUE script = rb_ary_entry(scriptArray, i);
            VALUE scriptDecoded = rb_ary_entry(script, 3);
            VALUE string =
            newStringUTF8(RSTRING_PTR(scriptDecoded), RSTRING_LEN(scriptDecoded));
            const char *scriptName = RSTRING_PTR(rb_ary_entry(script, 1));
            char nbuf[512];
            int nlen;
            if (conf.useScriptNames)
                nlen = snprintf(nbuf, sizeof(nbuf), "%03ld:%s", i, scriptName);
            else
                nlen = snprintf(nbuf, sizeof(nbuf), SCRIPT_SECTION_FMT, i);
            VALUE nameStr = newStringUTF8(nbuf, nlen);
            VALUE args[2] = { string, nameStr };
            VALUE fixed = rb_funcall2(cachedFix, rb_intern("call"), 2, args);
            rb_ary_push(fixedScripts, fixed == Qnil ? string : fixed);
        }
        rb_eval_string(R"RB(
begin
  if $__anyplay_ncache_dirty
    File.binwrite("AnyPlay_script_cache.bin.tmp", Marshal.dump($__anyplay_ncache))
    File.rename("AnyPlay_script_cache.bin.tmp", "AnyPlay_script_cache.bin")
    $__anyplay_ncache_dirty = false
  end
rescue Exception
end
begin
  ms = ((Process.clock_gettime(Process::CLOCK_MONOTONIC) - $__anyplay_ncache_t0) * 1000).to_i
  AnyPlayLog.write("script prepass: #{$__anyplay_nhits}/#{$__anyplay_ncount} from cache, #{ms}ms") if defined?(AnyPlayLog)
rescue Exception
end
)RB");
        rb_eval_string(R"RB(
begin
  old = "AnyPlay_script_errors.txt"
  File.delete(old) if File.exist?(old)
  errs = $__anyplay_syntax_errors
  AnyPlayLog.script_errors(errs, "v9") if errs && !errs.empty? && defined?(AnyPlayLog)
rescue Exception
end
)RB");
    }
    return fixedScripts;
}
