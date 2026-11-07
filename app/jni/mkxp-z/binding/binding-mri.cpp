/*
** binding-mri.cpp
**
** This file is part of mkxp.
**
** Copyright (C) 2013 Jonas Kulla <Nyocurio@gmail.com>
**
** mkxp is free software: you can redistribute it and/or modify
** it under the terms of the GNU General Public License as published by
** the Free Software Foundation, either version 2 of the License, or
** (at your option) any later version.
**
** mkxp is distributed in the hope that it will be useful,
** but WITHOUT ANY WARRANTY; without even the implied warranty of
** MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
** GNU General Public License for more details.
**
** You should have received a copy of the GNU General Public License
** along with mkxp.  If not, see <http://www.gnu.org/licenses/>.
*/

#include "audio/audio.h"
#include "filesystem/filesystem.h"
#include "display/graphics.h"
#include "display/font.h"
#include "system/system.h"

#include "util/util.h"
#include "util/sdl-util.h"
#include "util/debugwriter.h"
#include "util/boost-hash.h"
#include "util/exception.h"
#include "util/encoding.h"

#include "config.h"

#include "binding-util.h"
#include "binding.h"

#include "sharedstate.h"
#include "eventthread.h"

#include <vector>

extern "C" {
#include <ruby.h>

#if RAPI_FULL >= 190
#include <ruby/encoding.h>
#endif
}

#ifdef __WIN32__
#include "binding-mri-win32.h"
#endif

#include <assert.h>
#include <string>
#include <zlib.h>

#include <SDL_cpuinfo.h>
#include <SDL_filesystem.h>
#include <SDL_loadso.h>
#include <SDL_power.h>

extern const char module_rpg1[];
extern const char module_rpg2[];
extern const char module_rpg3[];

static void mriBindingExecute();
static void mriBindingTerminate();
static void mriBindingReset();

ScriptBinding scriptBindingImpl = {
	mriBindingExecute,
	mriBindingTerminate,
	mriBindingReset
};

ScriptBinding *scriptBinding = &scriptBindingImpl;

void tableBindingInit();
void etcBindingInit();
void fontBindingInit();
void bitmapBindingInit();
void spriteBindingInit();
void viewportBindingInit();
void planeBindingInit();
void windowBindingInit();
void tilemapBindingInit();
void windowVXBindingInit();
void tilemapVXBindingInit();

void inputBindingInit();
void audioBindingInit();
void graphicsBindingInit();

void fileIntBindingInit();

#ifdef MKXPZ_BUILD_ANDROID
void androidBindingInit();
#endif

#ifdef MKXPZ_MINIFFI
void MiniFFIBindingInit();
#endif

#ifdef MKXPZ_STEAM
void CUSLBindingInit();
#endif

void httpBindingInit();

RB_METHOD(mkxpDelta);
RB_METHOD(mriPrint);
RB_METHOD(mriP);
RB_METHOD(mkxpDataDirectory);
RB_METHOD(mkxpSetTitle);
RB_METHOD(mkxpGetTitle);
RB_METHOD(mkxpDesensitize);
RB_METHOD(mkxpPuts);

RB_METHOD(mkxpPlatform);
RB_METHOD(mkxpIsWindowsHost);
RB_METHOD(mkxpIsLinuxHost);
RB_METHOD(mkxpIsMacHost);
RB_METHOD(mkxpIsAndroidHost);
RB_METHOD(mkxpIsUsingWine);
RB_METHOD(mkxpIsUsingRosetta);
RB_METHOD(mkxpIsReallyWindowsHost);
RB_METHOD(mkxpIsReallyLinuxHost);
RB_METHOD(mkxpIsReallyMacHost);

RB_METHOD(mkxpUserLanguage);
RB_METHOD(mkxpUserName);
RB_METHOD(mkxpGameTitle);
RB_METHOD(mkxpPowerState);
RB_METHOD(mkxpSettingsMenu);
RB_METHOD(mkxpCpuCount);
RB_METHOD(mkxpSystemMemory);
RB_METHOD(mkxpReloadPathCache);
RB_METHOD(mkxpAddPath);
RB_METHOD(mkxpRemovePath);
RB_METHOD(mkxpLaunch);

RB_METHOD(mkxpGetJSONSetting);
RB_METHOD(mkxpSetJSONSetting);
RB_METHOD(mkxpGetAllJSONSettings);

RB_METHOD(mkxpSetDefaultFontFamily);

RB_METHOD(mriRgssMain);
RB_METHOD(mriRgssStop);
RB_METHOD(_kernelCaller);

RB_METHOD(mkxpStringToUTF8);
RB_METHOD(mkxpStringToUTF8Bang);

VALUE json2rb(json5pp::value const &v);
json5pp::value rb2json(VALUE v);

static void mriBindingInit()
{
	tableBindingInit();
	etcBindingInit();
	fontBindingInit();
	bitmapBindingInit();
	spriteBindingInit();
	viewportBindingInit();
	planeBindingInit();

	if (rgssVer == 1) {
		windowBindingInit();
		tilemapBindingInit();
	} else {
		windowVXBindingInit();
		tilemapVXBindingInit();
	}

	inputBindingInit();
	audioBindingInit();
	graphicsBindingInit();

	fileIntBindingInit();

#ifdef MKXPZ_BUILD_ANDROID
	androidBindingInit();
#endif

#ifdef MKXPZ_MINIFFI
	MiniFFIBindingInit();
#endif

#ifdef MKXPZ_STEAM
	CUSLBindingInit();
#endif

	httpBindingInit();

	if (rgssVer >= 3) {
		_rb_define_module_function(rb_mKernel, "rgss_main", mriRgssMain);
		_rb_define_module_function(rb_mKernel, "rgss_stop", mriRgssStop);

		_rb_define_module_function(rb_mKernel, "msgbox", mriPrint);
		_rb_define_module_function(rb_mKernel, "msgbox_p", mriP);

		rb_define_global_const("RGSS_VERSION", rb_utf8_str_new_cstr("3.0.1"));
	} else {
		_rb_define_module_function(rb_mKernel, "print", mriPrint);
		_rb_define_module_function(rb_mKernel, "p", mriP);

		rb_define_alias(rb_singleton_class(rb_mKernel), "_mkxp_kernel_caller_alias", "caller");
		_rb_define_module_function(rb_mKernel, "caller", _kernelCaller);
	}

	if (rgssVer == 1)
		rb_eval_string(module_rpg1);
	else if (rgssVer == 2)
		rb_eval_string(module_rpg2);
	else if (rgssVer == 3)
		rb_eval_string(module_rpg3);
	else
		assert(!"unreachable");

	VALUE mod = rb_define_module("System");

	_rb_define_module_function(mod, "delta", mkxpDelta);
	_rb_define_module_function(mod, "uptime", mkxpDelta);
	_rb_define_module_function(mod, "data_directory", mkxpDataDirectory);
	_rb_define_module_function(mod, "set_window_title", mkxpSetTitle);
	_rb_define_module_function(mod, "window_title", mkxpGetTitle);
	_rb_define_module_function(mod, "window_title=", mkxpSetTitle);
	_rb_define_module_function(mod, "show_settings", mkxpSettingsMenu);
	_rb_define_module_function(mod, "puts", mkxpPuts);
	_rb_define_module_function(mod, "desensitize", mkxpDesensitize);

	_rb_define_module_function(mod, "platform", mkxpPlatform);
	_rb_define_module_function(mod, "is_windows?", mkxpIsWindowsHost);
	_rb_define_module_function(mod, "is_linux?", mkxpIsLinuxHost);
	_rb_define_module_function(mod, "is_mac?", mkxpIsMacHost);
	_rb_define_module_function(mod, "is_android?", mkxpIsAndroidHost);
	_rb_define_module_function(mod, "is_wine?", mkxpIsUsingWine);
	_rb_define_module_function(mod, "is_rosetta?", mkxpIsUsingRosetta);
	_rb_define_module_function(mod, "is_really_windows?", mkxpIsReallyWindowsHost);
	_rb_define_module_function(mod, "is_really_linux?", mkxpIsReallyLinuxHost);
	_rb_define_module_function(mod, "is_really_mac?", mkxpIsReallyMacHost);

	_rb_define_module_function(mod, "user_language", mkxpUserLanguage);
	_rb_define_module_function(mod, "user_name", mkxpUserName);
	_rb_define_module_function(mod, "game_title", mkxpGameTitle);
	_rb_define_module_function(mod, "power_state", mkxpPowerState);
	_rb_define_module_function(mod, "nproc", mkxpCpuCount);
	_rb_define_module_function(mod, "memory", mkxpSystemMemory);

	_rb_define_module_function(mod, "reload_cache", mkxpReloadPathCache);
	_rb_define_module_function(mod, "mount", mkxpAddPath);
	_rb_define_module_function(mod, "unmount", mkxpRemovePath);

	_rb_define_module_function(mod, "launch", mkxpLaunch);

	_rb_define_module_function(mod, "default_font_family=", mkxpSetDefaultFontFamily);

	_rb_define_method(rb_cString, "to_utf8", mkxpStringToUTF8);
	_rb_define_method(rb_cString, "to_utf8!", mkxpStringToUTF8Bang);

	VALUE cmod = rb_define_module("CFG");
	_rb_define_module_function(cmod, "[]", mkxpGetJSONSetting);
	_rb_define_module_function(cmod, "[]=", mkxpSetJSONSetting);
	_rb_define_module_function(cmod, "to_hash", mkxpGetAllJSONSettings);

	// Load global constants
	rb_gv_set("MKXP", Qtrue);

	VALUE debug = rb_bool_new(shState->config().editor.debug);
	if (rgssVer == 1)
		rb_gv_set("DEBUG", debug);
	else if (rgssVer >= 2)
		rb_gv_set("TEST", debug);

	rb_gv_set("BTEST", rb_bool_new(shState->config().editor.battleTest));

	VALUE vers = rb_utf8_str_new_cstr(MKXPZ_VERSION);
	rb_str_freeze(vers);
	rb_define_const(mod, "VERSION", vers);

	// Set $stdout and its ilk accordingly on Windows
	// I regret teaching you that word
#ifdef __WIN32__
	if (shState->config().winConsole)
		configureWindowsStreams();
#endif
}

static void showMsg(const std::string &msg)
{
	shState->eThread().showMessageBox(msg.c_str());
}

static void printP(int argc, VALUE *argv, const char *convMethod, const char *sep)
{
	VALUE dispString = rb_str_buf_new(128);
	ID conv = rb_intern(convMethod);

	for (int i = 0; i < argc; ++i) {
		VALUE str = rb_funcall2(argv[i], conv, 0, NULL);
		rb_str_buf_append(dispString, str);

		if (i < argc)
			rb_str_buf_cat2(dispString, sep);
	}

	showMsg(RSTRING_PTR(dispString));
}

RB_METHOD(mriPrint) {
    RB_UNUSED_PARAM;
    
    printP(argc, argv, "to_s", "");
    
    return Qnil;
}

RB_METHOD(mriP) {
    RB_UNUSED_PARAM;
    
    printP(argc, argv, "inspect", "\n");
    
    return Qnil;
}

RB_METHOD(mkxpDelta) {
    RB_UNUSED_PARAM;
    
    return ULL2NUM(shState->runTime());
}

RB_METHOD(mkxpDataDirectory) {
    RB_UNUSED_PARAM;
    
    const std::string &path = shState->config().customDataPath;
    const char *s = path.empty() ? "." : path.c_str();
    
    std::string s_nml = shState->fileSystem().normalize(s, 1, 1);
    VALUE ret = rb_utf8_str_new_cstr(s_nml.c_str());
    
    return ret;
}

RB_METHOD(mkxpSetTitle) {
    RB_UNUSED_PARAM;
    
    VALUE s;
    rb_scan_args(argc, argv, "1", &s);
    SafeStringValue(s);
    
    shState->eThread().requestWindowRename(RSTRING_PTR(s));
    return s;
}

RB_METHOD(mkxpGetTitle) {
    RB_UNUSED_PARAM;
    
    rb_check_argc(argc, 0);
    
    return rb_utf8_str_new_cstr(SDL_GetWindowTitle(shState->sdlWindow()));
}

RB_METHOD(mkxpDesensitize) {
    RB_UNUSED_PARAM;
    
    VALUE filename;
    rb_scan_args(argc, argv, "1", &filename);
    SafeStringValue(filename);
    
    return rb_utf8_str_new_cstr(
                                shState->fileSystem().desensitize(RSTRING_PTR(filename)));
}

RB_METHOD(mkxpPuts) {
    RB_UNUSED_PARAM;
    
    const char *str;
    rb_get_args(argc, argv, "z", &str RB_ARG_END);
    
    Debug() << str;
    
    return Qnil;
}

RB_METHOD(mkxpPlatform)
{
	RB_UNUSED_PARAM;

#if MKXPZ_PLATFORM == MKXPZ_PLATFORM_MACOS
	std::string platform("macOS");

	if (mkxp_sys::isRosetta())
		platform += " (Rosetta)";
#elif MKXPZ_PLATFORM == MKXPZ_PLATFORM_WINDOWS
	std::string platform("Windows");

	if (mkxp_sys::isWine()) {
		platform += " (Wine - ";
		switch (mkxp_sys::getRealHostType())
		{
			case mkxp_sys::WineHostType::Mac:
				platform += "macOS)";
				break;
			default:
				platform += "Linux)";
				break;
		}
	}
#elif MKXPZ_PLATFORM == MKXPZ_PLATFORM_ANDROID
	std::string platform("Android");
#else
	std::string platform("Linux");
#endif

	return rb_utf8_str_new_cstr(platform.c_str());
}

RB_METHOD(mkxpIsWindowsHost)
{
	RB_UNUSED_PARAM;
	return rb_bool_new(MKXPZ_PLATFORM == MKXPZ_PLATFORM_WINDOWS);
}

RB_METHOD(mkxpIsLinuxHost)
{
	RB_UNUSED_PARAM;
	return rb_bool_new(MKXPZ_PLATFORM == MKXPZ_PLATFORM_LINUX || MKXPZ_PLATFORM == MKXPZ_PLATFORM_ANDROID);
}

RB_METHOD(mkxpIsMacHost)
{
	RB_UNUSED_PARAM;
	return rb_bool_new(MKXPZ_PLATFORM == MKXPZ_PLATFORM_MACOS);
}

RB_METHOD(mkxpIsAndroidHost)
{
	RB_UNUSED_PARAM;
	return rb_bool_new(MKXPZ_PLATFORM == MKXPZ_PLATFORM_ANDROID);
}

RB_METHOD(mkxpIsUsingWine)
{
	RB_UNUSED_PARAM;
	return rb_bool_new(mkxp_sys::isWine());
}

RB_METHOD(mkxpIsUsingRosetta)
{
	RB_UNUSED_PARAM;
	return rb_bool_new(mkxp_sys::isRosetta());
}

RB_METHOD(mkxpIsReallyWindowsHost)
{
	RB_UNUSED_PARAM;
	return rb_bool_new(mkxp_sys::getRealHostType() == mkxp_sys::WineHostType::Windows);
}

RB_METHOD(mkxpIsReallyLinuxHost)
{
	RB_UNUSED_PARAM;
	return rb_bool_new(mkxp_sys::getRealHostType() == mkxp_sys::WineHostType::Linux);
}

RB_METHOD(mkxpIsReallyMacHost)
{
	RB_UNUSED_PARAM;
	return rb_bool_new(mkxp_sys::getRealHostType() == mkxp_sys::WineHostType::Mac);
}

RB_METHOD(mkxpUserLanguage)
{
	RB_UNUSED_PARAM;
	return rb_utf8_str_new_cstr(mkxp_sys::getSystemLanguage().c_str());
}

RB_METHOD(mkxpUserName)
{
	RB_UNUSED_PARAM;

#ifdef __WIN32__
	// Using the Windows API isn't working with usernames that involve Unicode
	// characters for some dumb reason.
	VALUE env = rb_const_get(rb_mKernel, rb_intern("ENV"));
	return rb_funcall(env, rb_intern("[]"), 1, rb_str_new_cstr("USERNAME"));
#else
	return rb_utf8_str_new_cstr(mkxp_sys::getUserName().c_str());
#endif
}

RB_METHOD(mkxpGameTitle) {
    RB_UNUSED_PARAM;
    
    return rb_utf8_str_new_cstr(shState->config().game.title.c_str());
}

RB_METHOD(mkxpPowerState) {
    RB_UNUSED_PARAM;
    
    int secs, pct;
    SDL_PowerState ps = SDL_GetPowerInfo(&secs, &pct);
    
    VALUE hash = rb_hash_new();
    
    rb_hash_aset(hash, ID2SYM(rb_intern("seconds")),
                 (secs > -1) ? INT2NUM(secs) : RUBY_Qnil);
    
    rb_hash_aset(hash, ID2SYM(rb_intern("percent")),
                 (pct > -1) ? INT2NUM(pct) : RUBY_Qnil);
    
    rb_hash_aset(hash, ID2SYM(rb_intern("discharging")),
                 rb_bool_new(ps == SDL_POWERSTATE_ON_BATTERY));
    
    return hash;
}

RB_METHOD(mkxpSettingsMenu) {
    RB_UNUSED_PARAM;
    
    shState->eThread().requestSettingsMenu();
    
    return Qnil;
}

RB_METHOD(mkxpCpuCount) {
    RB_UNUSED_PARAM;
    
    return INT2NUM(SDL_GetCPUCount());
}

RB_METHOD(mkxpSystemMemory) {
    RB_UNUSED_PARAM;
    
    return INT2NUM(SDL_GetSystemRAM());
}

RB_METHOD(mkxpReloadPathCache) {
    RB_UNUSED_PARAM;
    
    shState->fileSystem().reloadPathCache();
    return Qnil;
}

RB_METHOD(mkxpAddPath) {
    RB_UNUSED_PARAM;
    
    VALUE path, mountpoint, reload;
    rb_scan_args(argc, argv, "12", &path, &mountpoint, &reload);
    SafeStringValue(path);
    if (mountpoint != Qnil) SafeStringValue(mountpoint);
    
    const char *mp = (mountpoint == Qnil) ? 0 : RSTRING_PTR(mountpoint);
    
    try {
        bool rl = true;
        if (reload != Qnil)
            rb_bool_arg(reload, &rl);
        
        shState->fileSystem().addPath(RSTRING_PTR(path), mp, rl);
    } catch (Exception &e) {
        raiseRbExc(e);
    }
    return path;
}

RB_METHOD(mkxpRemovePath) {
    RB_UNUSED_PARAM;
    
    VALUE path, reload;
    rb_scan_args(argc, argv, "11", &path, &reload);
    SafeStringValue(path);
    
    try {
        bool rl = true;
        if (reload != Qnil)
            rb_bool_arg(reload, &rl);
        
        shState->fileSystem().removePath(RSTRING_PTR(path), rl);
    } catch (Exception &e) {
        raiseRbExc(e);
    }
    return path;
}

RB_METHOD(mkxpSetDefaultFontFamily) {
    RB_UNUSED_PARAM;
    
    VALUE familyV;
    rb_scan_args(argc, argv, "1", &familyV);
    SafeStringValue(familyV);
    
    std::string family(RSTRING_PTR(familyV));
    shState->fontState().setDefaultFontFamily(family);
    
    return Qnil;
}

RB_METHOD(mkxpStringToUTF8) {
    RB_UNUSED_PARAM;
    
    rb_check_argc(argc, 0);
    
    std::string ret(RSTRING_PTR(self), RSTRING_LEN(self));
    GUARD_EXC(ret = Encoding::convertString(ret); );
    
    return rb_utf8_str_new(ret.c_str(), ret.length());
}

RB_METHOD(mkxpStringToUTF8Bang) {
    RB_UNUSED_PARAM;
    
    rb_check_argc(argc, 0);
    
    std::string ret(RSTRING_PTR(self), RSTRING_LEN(self));
    GUARD_EXC(ret = Encoding::convertString(ret); );
    
    rb_str_resize(self, ret.length());
    memcpy(RSTRING_PTR(self), ret.c_str(), RSTRING_LEN(self));
    
#if RAPI_FULL >= 190
    rb_funcall(self, rb_intern("force_encoding"), 1, rb_enc_from_encoding(rb_utf8_encoding()));
#endif
    
    return self;
}

#ifdef __APPLE__
#define OPENCMD "open "
#define OPENARGS "--args"
#elif defined(__linux__)
#define OPENCMD "xdg-open "
#define OPENARGS ""
#else
#define OPENCMD "start /b \"launch\" "
#define OPENARGS ""
#endif

RB_METHOD(mkxpLaunch) {
    RB_UNUSED_PARAM;
    
    VALUE cmdname, args;
    
    rb_scan_args(argc, argv, "11", &cmdname, &args);
    SafeStringValue(cmdname);
    
    std::string command(OPENCMD);
    command += "\""; command += RSTRING_PTR(cmdname); command += "\"";
    
    if (args != RUBY_Qnil) {
#ifndef __linux__
        command += " ";
        command += OPENARGS;
        Check_Type(args, T_ARRAY);
        
        for (int i = 0; i < RARRAY_LEN(args); i++) {
            VALUE arg = rb_ary_entry(args, i);
            SafeStringValue(arg);
            
            if (RSTRING_LEN(arg) <= 0)
                continue;
            
            command += " ";
            command += RSTRING_PTR(arg);
        }
#else
        Debug() << command << ":" << "Arguments are not supported with xdg-open. Ignoring.";
#endif
    }
    
    if (std::system(command.c_str()) != 0) {
        raiseRbExc(Exception(Exception::MKXPError, "Failed to launch \"%s\"", RSTRING_PTR(cmdname)));
    }
    
    return RUBY_Qnil;
}

json5pp::value userSettings;

void loadUserSettings() {
    if (!userSettings.is_null())
        return;
    
    VALUE cpath = rb_utf8_str_new_cstr(shState->config().userConfPath.c_str());
    
    if (rb_funcall(rb_cFile, rb_intern("exists?"), 1, cpath) == Qtrue) {
        VALUE f = rb_funcall(rb_cFile, rb_intern("open"), 2, cpath, rb_str_new("r", 1));
        VALUE data = rb_funcall(f, rb_intern("read"), 0);
        rb_funcall(f, rb_intern("close"), 0);
        userSettings = rb2json(data);
    }
    
    if (!userSettings.is_object())
        userSettings = json5pp::object({});
}

void saveUserSettings() {
    VALUE cpath = rb_utf8_str_new_cstr(shState->config().userConfPath.c_str());
    VALUE f = rb_funcall(rb_cFile, rb_intern("open"), 2, cpath, rb_str_new("w", 1));
    rb_funcall(f, rb_intern("write"), 1, rb_utf8_str_new_cstr(userSettings.stringify5(json5pp::rule::space_indent<>()).c_str()));
    rb_funcall(f, rb_intern("close"), 0);
}

RB_METHOD(mkxpGetJSONSetting) {
    RB_UNUSED_PARAM;
    
    VALUE sname;
    rb_scan_args(argc, argv, "1", &sname);
    SafeStringValue(sname);
    
    loadUserSettings();
    auto &s = userSettings.as_object();
    
    if (s[RSTRING_PTR(sname)].is_null()) {
        return json2rb(shState->config().raw.as_object()[RSTRING_PTR(sname)]);
    }
    
    return json2rb(s[RSTRING_PTR(sname)]);
    
}

RB_METHOD(mkxpSetJSONSetting) {
    RB_UNUSED_PARAM;
    
    VALUE sname, svalue;
    rb_scan_args(argc, argv, "2", &sname, &svalue);
    SafeStringValue(sname);
    
    loadUserSettings();
    userSettings.as_object()[RSTRING_PTR(sname)] = rb2json(svalue);
    
    saveUserSettings();
    return Qnil;
}

RB_METHOD(mkxpGetAllJSONSettings) {
    RB_UNUSED_PARAM;
    
    return json2rb(shState->config().raw);
}

static VALUE rgssMainCb(VALUE block) {
    rb_funcall2(block, rb_intern("call"), 0, 0);
    return Qnil;
}

static VALUE rgssMainRescue(VALUE arg, VALUE exc) {
    VALUE *excRet = (VALUE *)arg;
    
    *excRet = exc;
    
    return Qnil;
}

static void processReset() {
    shState->graphics().reset();
    shState->audio().reset();
    
    shState->rtData().rqReset.clear();
    shState->graphics().repaintWait(shState->rtData().rqResetFinish, false);
}

static void runCustomScript(const std::string &filename);

RB_METHOD(mriRgssMain) {
    RB_UNUSED_PARAM;

    /* Postload scripts run after the game's script pack has been loaded. */
    const Config &conf = shState->rtData().config;
    for (std::vector<std::string>::const_iterator i = conf.postloadScripts.begin();
         i != conf.postloadScripts.end(); ++i) {
        if (shState->rtData().rqTerm)
            break;
        runCustomScript(*i);
    }
    
    while (true) {
        VALUE exc = Qnil;
#if RAPI_FULL < 270
        rb_rescue2((VALUE(*)(ANYARGS))rgssMainCb, rb_block_proc(),
                   (VALUE(*)(ANYARGS))rgssMainRescue, (VALUE)&exc, rb_eException,
                   (VALUE)0);
#else
        rb_rescue2(rgssMainCb, rb_block_proc(), rgssMainRescue, (VALUE)&exc,
                   rb_eException, (VALUE)0);
#endif
        
        if (NIL_P(exc))
            break;
        
        if (rb_obj_class(exc) == getRbData()->exc[Reset])
            processReset();
        else
            rb_exc_raise(exc);
    }
    
    return Qnil;
}

RB_METHOD(mriRgssStop) {
    RB_UNUSED_PARAM;
    
    while (true)
        shState->graphics().update();
    
    return Qnil;
}

RB_METHOD(_kernelCaller) {
    RB_UNUSED_PARAM;
    
    VALUE trace =
    rb_funcall2(rb_mKernel, rb_intern("_mkxp_kernel_caller_alias"), 0, 0);
    
    if (!RB_TYPE_P(trace, RUBY_T_ARRAY))
        return trace;
    
    long len = RARRAY_LEN(trace);
    
    if (len < 2)
        return trace;
    
    /* Remove useless "ruby:1:in 'eval'" */
    rb_ary_pop(trace);
    
    /* Also remove trace of this helper function */
    rb_ary_shift(trace);
    
    len -= 2;
    
    if (len == 0)
        return trace;
    
    /* RMXP does this, not sure if specific or 1.8 related */
    VALUE args[] = {rb_utf8_str_new_cstr(":in `<main>'"), rb_utf8_str_new_cstr("")};
    rb_funcall2(rb_ary_entry(trace, len - 1), rb_intern("gsub!"), 2, args);
    
    return trace;
}

#if RAPI_FULL > 187
static VALUE newStringUTF8(const char *string, long length) {
    return rb_enc_str_new(string, length, rb_utf8_encoding());
}
#else
#define newStringUTF8 rb_str_new
#endif

struct evalArg {
    VALUE string;
    VALUE filename;
};

static VALUE evalHelper(evalArg *arg) {
    VALUE argv[] = {arg->string, Qnil, arg->filename};
    return rb_funcall2(Qnil, rb_intern("eval"), ARRAY_SIZE(argv), argv);
}

static VALUE evalString(VALUE string, VALUE filename, int *state) {
    evalArg arg = {string, filename};
    return rb_protect((VALUE(*)(VALUE))evalHelper, (VALUE)&arg, state);
}

static void runCustomScript(const std::string &filename) {
    std::string scriptData;
    
    if (!readFileSDL(filename.c_str(), scriptData)) {
        showMsg(std::string("Unable to open '") + filename + "'");
        return;
    }
    
    evalString(newStringUTF8(scriptData.c_str(), scriptData.size()),
               newStringUTF8(filename.c_str(), filename.size()), NULL);
}

VALUE kernelLoadDataInt(const char *filename, bool rubyExc, bool raw);

struct BacktraceData {
    /* Maps: Ruby visible filename, To: Actual script name */
    BoostHash<std::string, std::string> scriptNames;
};

bool evalScript(VALUE string, const char *filename)
{
    int state;
    evalString(string, rb_utf8_str_new_cstr(filename), &state);
    if (state) return false;
    return true;
}


#define SCRIPT_SECTION_FMT (rgssVer >= 3 ? "{%04ld}" : "Section%03ld")

static void runRMXPScripts(BacktraceData &btData) {
    const Config &conf = shState->rtData().config;
    const std::string &scriptPack = conf.game.scripts;
    
    if (scriptPack.empty()) {
        showMsg("No game scripts specified (missing Game.ini?)");
        return;
    }
    
    if (!shState->fileSystem().exists(scriptPack.c_str())) {
        showMsg("Unable to open '" + scriptPack + "'");
        return;
    }
    
    VALUE scriptArray;
    
    /* We checked if Scripts.rxdata exists, but something might
     * still go wrong */
    try {
        scriptArray = kernelLoadDataInt(scriptPack.c_str(), false, false);
    } catch (const Exception &e) {
        showMsg(std::string("Failed to read script data: ") + e.msg);
        return;
    }
    
    if (!RB_TYPE_P(scriptArray, RUBY_T_ARRAY)) {
        showMsg("Failed to read script data");
        return;
    }
    
    rb_gv_set("$RGSS_SCRIPTS", scriptArray);
    
    long scriptCount = RARRAY_LEN(scriptArray);
    
    std::string decodeBuffer;
    decodeBuffer.resize(0x1000);
    
    for (long i = 0; i < scriptCount; ++i) {
        VALUE script = rb_ary_entry(scriptArray, i);
        
        if (!RB_TYPE_P(script, RUBY_T_ARRAY))
            continue;
        
        VALUE scriptName = rb_ary_entry(script, 1);
        VALUE scriptString = rb_ary_entry(script, 2);
        
        int result = Z_OK;
        unsigned long bufferLen;
        
        while (true) {
            unsigned char *bufferPtr = reinterpret_cast<unsigned char *>(
                                                                         const_cast<char *>(decodeBuffer.c_str()));
            const unsigned char *sourcePtr =
            reinterpret_cast<const unsigned char *>(RSTRING_PTR(scriptString));
            
            bufferLen = decodeBuffer.length();
            
            result = uncompress(bufferPtr, &bufferLen, sourcePtr,
                                RSTRING_LEN(scriptString));
            
            bufferPtr[bufferLen] = '\0';
            
            if (result != Z_BUF_ERROR)
                break;
            
            decodeBuffer.resize(decodeBuffer.size() * 2);
        }
        
        if (result != Z_OK) {
            static char buffer[256];
            snprintf(buffer, sizeof(buffer), "Error decoding script %ld: '%s'", i,
                     RSTRING_PTR(scriptName));
            
            showMsg(buffer);
            
            break;
        }
        
        rb_ary_store(script, 3, rb_utf8_str_new_cstr(decodeBuffer.c_str()));
    }
    
    /* Execute preloaded scripts */
    for (std::vector<std::string>::const_iterator i = conf.preloadScripts.begin();
         i != conf.preloadScripts.end(); ++i)
        runCustomScript(*i);
    
    VALUE exc = rb_gv_get("$!");
    if (exc != Qnil)
        return;
    
#ifdef __ANDROID__
    // AnyPlay: old RPG Maker games were written for Ruby 1.8. Before running anything,
    // every script is repaired for known Ruby 1.8-only syntax and checked with Ruby's
    // parser. Whatever still can't be parsed is written, ALL at once, to
    // AnyPlay_script_errors.txt in the game folder (so one test run shows every problem).
    VALUE fixedScripts = Qnil;
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
            VALUE fixed = rb_funcall2(compatFix, rb_intern("call"), 2, args);
            rb_ary_push(fixedScripts, fixed == Qnil ? string : fixed);
        }
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
#endif

    while (true) {
        for (long i = 0; i < scriptCount; ++i) {
            VALUE script = rb_ary_entry(scriptArray, i);
            VALUE scriptDecoded = rb_ary_entry(script, 3);
            VALUE string =
            newStringUTF8(RSTRING_PTR(scriptDecoded), RSTRING_LEN(scriptDecoded));
            
            VALUE fname;
            const char *scriptName = RSTRING_PTR(rb_ary_entry(script, 1));
            char buf[512];
            int len;
            
            if (conf.useScriptNames)
                len = snprintf(buf, sizeof(buf), "%03ld:%s", i, scriptName);
            else
                len = snprintf(buf, sizeof(buf), SCRIPT_SECTION_FMT, i);
            
            fname = newStringUTF8(buf, len);
            btData.scriptNames.insert(buf, scriptName);
            
            
            // if the script name starts with |s|, only execute
            // it if "s" is the same first letter as the platform
            // we're running on
            
            // |W| - Windows, |M| - Mac OS X, |L| - Linux
            
            // Adding a 'not' symbol means it WON'T run on that
            // platform (i.e. |!W| won't run on Windows)
            /*
             if (scriptName[0] == '|') {
             int len = strlen(scriptName);
             if (len > 2) {
             if (scriptName[1] == '!' && len > 3 &&
             scriptName[3] == scriptName[0]) {
             if (toupper(scriptName[2]) == platform[0])
             continue;
             }
             if (scriptName[2] == scriptName[0] &&
             toupper(scriptName[1]) != platform[0])
             continue;
             }
             }
             */
            
            int state;
            
#ifdef __ANDROID__
            // AnyPlay: use the script text that was checked / repaired in the pre-pass above
            if (fixedScripts != Qnil) {
                VALUE fixed = rb_ary_entry(fixedScripts, i);
                if (fixed != Qnil)
                    string = fixed;
            }
#endif
            evalString(string, fname, &state);
            if (state)
                break;
        }
        
        VALUE exc = rb_gv_get("$!");
        if (rb_obj_class(exc) != getRbData()->exc[Reset])
            break;
        
        processReset();
    }
}

static void showExc(VALUE exc, const BacktraceData &btData) {
    VALUE bt = rb_funcall2(exc, rb_intern("backtrace"), 0, NULL);
    VALUE msg = rb_funcall2(exc, rb_intern("message"), 0, NULL);
    VALUE bt0 = rb_ary_entry(bt, 0);
    VALUE name = rb_class_path(rb_obj_class(exc));
    
    VALUE ds = rb_sprintf("%" PRIsVALUE ": %" PRIsVALUE " (%" PRIsVALUE ")",
#if RAPI_MAJOR >= 2
                          bt0, exc, name);
#else
    // Ruby 1.9's version of this function needs char*
    RSTRING_PTR(bt0), RSTRING_PTR(exc), RSTRING_PTR(name));
#endif
    /* omit "useless" last entry (from ruby:1:in `eval') */
    for (long i = 1, btlen = RARRAY_LEN(bt) - 1; i < btlen; ++i)
        rb_str_catf(ds, "\n\tfrom %" PRIsVALUE,
#if RAPI_MAJOR >= 2
                    rb_ary_entry(bt, i));
#else
    RSTRING_PTR(rb_ary_entry(bt, i)));
#endif
    Debug() << StringValueCStr(ds);
    
    char *s = RSTRING_PTR(bt0);
    
    char line[16];
    std::string file(512, '\0');
    
    char *p = s + strlen(s);
    char *e;
    
    while (p != s)
        if (*--p == ':')
            break;
    
    e = p;
    
    while (p != s)
        if (*--p == ':')
            break;
    
    /* s         p  e
     * SectionXXX:YY: in 'blabla' */
    
    *e = '\0';
    strncpy(line, *p ? p + 1 : p, sizeof(line));
    line[sizeof(line) - 1] = '\0';
    *e = ':';
    e = p;
    
    /* s         e
     * SectionXXX:YY: in 'blabla' */
    
    *e = '\0';
    strncpy(&file[0], s, file.size());
    *e = ':';
    
    /* Shrink to fit */
    file.resize(strlen(file.c_str()));
    file = btData.scriptNames.value(file, file);
    
    std::string ms(640, '\0');
    snprintf(&ms[0], ms.size(), "Script '%s' line %s: %s occured.\n\n%s",
             file.c_str(), line, RSTRING_PTR(name), RSTRING_PTR(msg));
    
    showMsg(ms);
}

static void mriBindingExecute() {
    Config &conf = shState->rtData().config;
    
#if RAPI_MAJOR >= 2
    /* Normally only a ruby executable would do a sysinit,
     * but not doing it will lead to crashes due to closed
     * stdio streams on some platforms (eg. Windows) */
    int argc = 0;
    char **argv = 0;
    ruby_sysinit(&argc, &argv);
    
    RUBY_INIT_STACK;
    ruby_init();
    
    std::vector<const char*> rubyArgsC{"mkxp-z"};
    rubyArgsC.push_back("-e ");
    void *node;
    if (conf.jit.enabled) {
        std::string verboseLevel("--jit-verbose="); verboseLevel += std::to_string(conf.jit.verboseLevel);
        std::string maxCache("--jit-max-cache="); maxCache += std::to_string(conf.jit.maxCache);
        std::string minCalls("--jit-min-calls="); minCalls += std::to_string(conf.jit.minCalls);
        rubyArgsC.push_back("--jit");
        rubyArgsC.push_back(verboseLevel.c_str());
        rubyArgsC.push_back(maxCache.c_str());
        rubyArgsC.push_back(minCalls.c_str());
        node = ruby_options(rubyArgsC.size(), const_cast<char**>(rubyArgsC.data()));
    } else {
        node = ruby_options(rubyArgsC.size(), const_cast<char**>(rubyArgsC.data()));
    }
    
    int state;
    bool valid = ruby_executable_node(node, &state);
    if (valid)
        state = ruby_exec_node(node);
    if (state || !valid) {
        // The message is formatted for and automatically spits
        // out to the terminal, so let's leave it that way for now
        /*
         VALUE exc = rb_errinfo();
         #if RAPI_FULL >= 250
         VALUE msg = rb_funcall(exc, rb_intern("full_message"), 0);
         #else
         VALUE msg = rb_funcall(exc, rb_intern("message"), 0);
         #endif
         */
        showMsg("An error occurred while initializing Ruby. (Invalid JIT settings?)");
        ruby_cleanup(state);
        shState->rtData().rqTermAck.set();
        return;
    }
    rb_enc_set_default_internal(rb_enc_from_encoding(rb_utf8_encoding()));
    rb_enc_set_default_external(rb_enc_from_encoding(rb_utf8_encoding()));
#else
    ruby_init();
    rb_eval_string("$KCODE='U'");
#ifdef __WIN32__
    if (!conf.winConsole) {
        VALUE iostr = rb_str_new2("NUL");
        // Sysinit isn't a thing yet, so send io to /dev/null instead
        rb_funcall(rb_gv_get("$stderr"), rb_intern("reopen"), 1, iostr);
        rb_funcall(rb_gv_get("$stdout"), rb_intern("reopen"), 1, iostr);
    }
#endif
#endif
    
#if RAPI_FULL > 187
    VALUE rbArgv = rb_get_argv();
#else
    VALUE rbArgv = rb_gv_get("$*");
#endif
    for (const auto &str : conf.launchArgs)
        rb_ary_push(rbArgv, rb_utf8_str_new_cstr(str.c_str()));
    
    // Duplicates get pushed for some reason
    rb_funcall(rbArgv, rb_intern("uniq!"), 0);
    
    VALUE lpaths = rb_gv_get(":");
    rb_ary_clear(lpaths);
    
#if defined(MKXPZ_BUILD_XCODE) && RAPI_MAJOR >= 2
    std::string resPath = mkxp_fs::getResourcePath();
    resPath += "/Ruby/" + std::to_string(RAPI_MAJOR) + "." + std::to_string(RAPI_MINOR) + ".0";
    rb_ary_push(lpaths, rb_str_new(resPath.c_str(), resPath.size()));
#endif
    
    if (!conf.rubyLoadpaths.empty()) {
        /* Setup custom load paths */
        for (size_t i = 0; i < conf.rubyLoadpaths.size(); ++i) {
            std::string &path = conf.rubyLoadpaths[i];
            
            VALUE pathv = rb_str_new(path.c_str(), path.size());
            rb_ary_push(lpaths, pathv);
        }
    }
#ifndef WORKDIR_CURRENT
    else {
        rb_ary_push(lpaths, rb_utf8_str_new_cstr(mkxp_fs::getCurrentDirectory().c_str()));
    }
#endif
    
    RbData rbData;
    shState->setBindingData(&rbData);
    BacktraceData btData;
    
    mriBindingInit();
    
#ifdef __ANDROID__
    // AnyPlay: RPG Maker XP/VX/Ace ship with Zlib (and a few other standard
    // libraries) already loaded, so games use them without "require".
    // Load them up front; if one is missing, just carry on.
    {
        int reqState = 0;
        rb_eval_string_protect(R"RB(
['zlib', 'stringio'].each { |l| begin; require l; rescue LoadError; end }

# Things Ruby 1.8 had and modern Ruby removed, which old RPG Maker scripts still use.
class Thread
  @__anyplay_critical = false
  class << self
    unless respond_to?(:critical)
      def critical; @__anyplay_critical; end
      def critical=(v); @__anyplay_critical = v; end
    end
  end
end
class Array
  def nitems; count { |x| !x.nil? }; end unless method_defined?(:nitems)
end
class << File
  alias_method :exists?, :exist? unless respond_to?(:exists?)
end
class << Dir
  alias_method :exists?, :exist? unless respond_to?(:exists?)
end
Fixnum = Integer unless defined?(Fixnum)
Bignum = Integer unless defined?(Bignum)
)RB", &reqState);
        if (reqState) {
#if RAPI_FULL > 187
            rb_set_errinfo(Qnil);
#else
            ruby_errinfo = Qnil;
#endif
        }

        // AnyPlay: safe dispose, full-screen fit, tap-to-walk (see anyplay_prelude.rb)
        {
            int preState = 0;
            rb_eval_string_protect(R"RB(# AnyPlay RGSS plugin: runs before the game's own scripts (Android only).
# 1) Plane/Sprite/... #dispose no longer crashes for subclasses that skip `super` in initialize
# 2) game screen is resized to the game's own size (fills the screen)
# 3) touch: tap = walk there / talk to NPC / confirm
module AnyPlayCompat
  def self.safe_dispose_patch
    [:Plane, :Sprite, :Viewport, :Window, :Tilemap].each do |n|
      begin
        k = Object.const_get(n)
        next unless k.method_defined?(:dispose)
        next if k.method_defined?(:__anyplay_dispose)
        k.class_eval do
          alias_method :__anyplay_dispose, :dispose
          def dispose
            __anyplay_dispose
          rescue Exception => e
            raise unless e.message.to_s.include?("No instance data")
            nil
          end
        end
      rescue Exception
      end
    end
  end
end
AnyPlayCompat.safe_dispose_patch

# Windows mouse calls (GetCursorPos / ScreenToClient / GetClientRect / GetWindowRect) do nothing on
# Android, so games that read the mouse position (Mouse.getMousePos) got nil and crashed.
# Answer them from the engine's own mouse/touch position instead.
module AnyPlayWin
  FUNCS = %w(getcursorpos screentoclient getclientrect getwindowrect)
  def self.mouse
    [Input.mouse_x.to_i, Input.mouse_y.to_i]
  rescue Exception
    [0, 0]
  end
  def self.size
    [Graphics.width.to_i, Graphics.height.to_i]
  rescue Exception
    [0, 0]
  end
  module Shim
    def call(*a)
      case @__ap_fn
      when "getcursorpos"
        x, y = AnyPlayWin.mouse
        a[0].replace([x, y].pack("ll")) if a[0].is_a?(String) && !a[0].frozen?
        1
      when "screentoclient"
        1
      else   # getclientrect / getwindowrect: [left, top, right, bottom]
        w, h = AnyPlayWin.size
        a[1].replace([0, 0, w, h].pack("l4")) if a[1].is_a?(String) && !a[1].frozen?
        1
      end
    rescue Exception
      0
    end
  end
end
begin
  if defined?(Win32API) && Win32API.is_a?(Class) && Input.respond_to?(:mouse_x)
    class << Win32API
      alias_method :__anyplay_new, :new
      def new(lib, fn, *rest)
        o = __anyplay_new(lib, fn, *rest)
        name = fn.to_s.downcase.sub(/[aw]\z/, "")
        if AnyPlayWin::FUNCS.include?(name) || AnyPlayWin::FUNCS.include?(fn.to_s.downcase)
          o.instance_variable_set(:@__ap_fn, AnyPlayWin::FUNCS.include?(fn.to_s.downcase) ? fn.to_s.downcase : name)
          o.extend(AnyPlayWin::Shim)
        end
        o
      end
    end
  end
rescue Exception
end

# Some Essentials games (MKXP Compatibility Fix script) call
# Graphics.mkxp_snap_to_bitmap, the old name of snap_to_bitmap.
begin
  if Graphics.respond_to?(:snap_to_bitmap) && !Graphics.respond_to?(:mkxp_snap_to_bitmap)
    class << Graphics
      alias_method :mkxp_snap_to_bitmap, :snap_to_bitmap
    end
  end
rescue Exception
end


# Windows environment variables that games expect (TEMP for temp files, APPDATA
# as a fallback save folder). On Android they are missing, so scripts crashed
# with "no implicit conversion of nil into String".
begin
  __ap_base = "/data/data/com.tyranor.runtime.rgss"
  __ap_tmp = __ap_base + "/cache"
  __ap_files = __ap_base + "/files"
  [__ap_tmp, __ap_files].each { |d| (Dir.mkdir(d) unless File.directory?(d)) rescue nil }
  %w(TEMP TMP TMPDIR).each { |k| ENV[k] = __ap_tmp if ENV[k].nil? || ENV[k].empty? }
  %w(APPDATA LOCALAPPDATA).each { |k| ENV[k] = __ap_files if ENV[k].nil? || ENV[k].empty? }
rescue Exception
end


# Small helpers that Ruby 1.8 had and modern Ruby removed. Only added when missing.
begin
  class Array
    def nitems; count { |x| !x.nil? }; end unless method_defined?(:nitems)
    def choice; sample; end unless method_defined?(:choice)
  end
  class Hash
    def index(v); key(v); end unless method_defined?(:index)
  end
  module Enumerable
    def enum_with_index(*a, &b); each_with_index(*a, &b); end unless method_defined?(:enum_with_index)
  end
  # "require 'jcode'" (Japanese string helpers) no longer exists; the game does not need it.
  module Kernel
    alias_method :__anyplay_require, :require
    def require(name)
      __anyplay_require(name)
    rescue LoadError
      raise unless %w(jcode kconv nkf).include?(name.to_s)
      false
    end
    private :require
  end
rescue Exception
end


# Helper for the compat layer: byte value of str[i] (Ruby 1.8 gave a number, new Ruby gives text).
module Kernel
  def __anyplay_byte(x, i)
    x.is_a?(String) ? x.getbyte(i) : x[i]
  end
  private :__anyplay_byte
end

# Ruby 1.8 accepted a lone "%" in sprintf formats such as sprintf("%d%", 50); newer Ruby raises
# "incomplete format specifier". Retry once with the stray "%" written as "%%".
begin
  module Kernel
    alias_method :__anyplay_sprintf, :sprintf
    def sprintf(fmt, *args)
      __anyplay_sprintf(fmt, *args)
    rescue ArgumentError => e
      raise unless fmt.is_a?(String) && e.message.include?("incomplete format specifier")
      __anyplay_sprintf(fmt.gsub(/%(?![-+ 0#*\d.<{\[a-zA-Z%])/, "%%"), *args)
    end
    alias_method :format, :sprintf
    module_function :sprintf, :format
  end
  class String
    alias_method :__anyplay_percent, :%
    def %(arg)
      __anyplay_percent(arg)
    rescue ArgumentError => e
      raise unless e.message.include?("incomplete format specifier")
      self.gsub(/%(?![-+ 0#*\d.<{\[a-zA-Z%])/, "%%").__anyplay_percent(arg)
    end
  end
rescue Exception
end

# Windows raises EACCES when you open a folder as a file; Linux raises EISDIR later
# on read. Many scripts only rescue the Windows errors, so behave like Windows.
class << File
  alias_method :__anyplay_open, :open
  def open(path, *args, &blk)
    if path.is_a?(String) && File.directory?(path)
      mode = args[0]
      if mode.nil? || (mode.is_a?(String) && mode.start_with?("r"))
        raise Errno::EACCES, path
      end
    end
    __anyplay_open(path, *args, &blk)
  end
end

# Games written for newer mkxp-z ask for the music position when pausing.
if defined?(Audio)
  Audio.define_singleton_method(:bgm_position) { 0.0 } unless Audio.respond_to?(:bgm_position)
  Audio.define_singleton_method(:bgs_position) { 0.0 } unless Audio.respond_to?(:bgs_position)
end

# Speed: many games ask "does this picture / sound exist?" hundreds of times
# while a map loads (every name is tried with several endings), and most answers
# are "no". On a phone every such question is a slow trip to the storage. Instead
# the folder is listed once (one trip) and the answers come from that list.
# The list is refreshed every 10 minutes, so files that appear later are found.
# Only the read-only resource folders are handled this way.
if CFG["fastPathEnum"]
module AnyPlayFastFiles
  RES = /\A(?:\.\/)?(?:Graphics|Audio|Fonts)\//i
  TTL = 600
  @lists = 0
  @list_ms = 0.0
  @dirs = {}     # "Graphics/Battlers" => [time, {name => true}]
  @yes = {}      # "Graphics/Battlers/1.png" => answer of the real call
  class << self
    attr_reader :dirs, :yes
    def take_stats
      r = "dirlists=#{@lists} dirlist_ms=#{@list_ms.to_i}"
      @lists = 0
      @list_ms = 0.0
      r
    end
    def count_list(ms)
      @lists += 1
      @list_ms += ms
    end
  end
  def self.now
    Process.clock_gettime(Process::CLOCK_MONOTONIC)
  end
  def self.listing(dir)
    e = @dirs[dir]
    return e[1] if e && (now - e[0]) < TTL
    names = {}
    t0 = now
    begin
      Dir.children(dir).each { |n| names[n] = true }
    rescue Exception
      names = {}
    end
    count_list((now - t0) * 1000.0)
    @dirs.clear if @dirs.size > 400
    @dirs[dir] = [now, names]
    names
  end
  # true / false when the list can answer, nil when the real call must be used
  def self.known(path)
    return nil unless path.is_a?(String) && path.bytesize < 400 && path =~ RES
    return nil if path.include?("..") || path.end_with?("/")
    base = File.basename(path)
    return nil unless base.ascii_only?
    dir = File.dirname(path)
    return nil if dir.empty?
    names = listing(dir)
    names.key?(base) ? :maybe : false
  rescue Exception
    nil
  end
  # Everything else (Data/..., folders, ...): games sometimes ask the same question every frame.
  # On slow storage each call is several milliseconds. Remember the answer very briefly and
  # forget it as soon as the game creates / deletes / renames something.
  @short = {}
  @slowfs = {}
  class << self
    attr_reader :short
    def forget; @short.clear; end
    def timed(path, orig)
      t0 = AnyPlayFastFiles.now
      v = orig.call(path)
      ms = (AnyPlayFastFiles.now - t0) * 1000.0
      if ms > 0.4
        @slowfs.clear if @slowfs.size > 300
        e = (@slowfs[path] ||= [0, 0.0])
        e[0] += 1
        e[1] += ms
      end
      v
    end
    def slowfs_report
      top = @slowfs.select { |_, e| e[1] > 30.0 }.sort_by { |_, e| -e[1] }.first(3)
      @slowfs = {}
      top.empty? ? "" : " slowfs=" + top.map { |p, e| "#{p[0, 70]}(x#{e[0]},#{e[1].to_i}ms)" }.join(",")
    end
    def short_answer(tag, path, pos_ttl, neg_ttl, orig)
      return orig.call(path) unless path.is_a?(String) && path.bytesize < 400
      key = tag + path
      e = @short[key]
      t = now
      return e[1] if e && (t - e[0]) < (e[1] ? pos_ttl : neg_ttl)
      v = timed(path, orig)
      @short.clear if @short.size > 20_000
      @short[key] = [t, v]
      v
    end
  end
  begin
    singles = [[File, :exist?, 1.0, 0.3], [File, :file?, 1.0, 0.3], [FileTest, :exist?, 1.0, 0.3], [FileTest, :file?, 1.0, 0.3],
               [File, :directory?, 2.0, 0.5], [FileTest, :directory?, 2.0, 0.5], [Dir, :exist?, 2.0, 0.5]]
    [[File, :exists?], [FileTest, :exists?], [Dir, :exists?]].each do |m, n|
      singles << [m, n, 1.0, 0.3] if m.respond_to?(n)
    end
    singles.each do |mod, meth, pos, neg|
      orig = mod.method(meth)
      tag = "#{mod.name}.#{meth}:"
      mod.define_singleton_method(meth) do |path|
        if path.is_a?(String) && path =~ AnyPlayFastFiles::RES && !%i(directory?).include?(meth) && meth != :exists? && !(mod == Dir)
          orig.call(path)   # Graphics / Audio / Fonts: handled by the folder lists below
        else
          AnyPlayFastFiles.short_answer(tag, path, pos, neg, orig)
        end
      end
    end
    # anything that changes the disk makes the remembered answers stale
    [[File, :delete], [File, :unlink], [File, :rename], [Dir, :mkdir], [Dir, :rmdir], [Dir, :delete], [Dir, :unlink],
     [File, :write], [File, :binwrite], [IO, :write], [IO, :binwrite]].each do |mod, meth|
      next unless mod.respond_to?(meth)
      orig = mod.method(meth)
      mod.define_singleton_method(meth) do |*a, **kw, &b|
        AnyPlayFastFiles.forget
        kw.empty? ? orig.call(*a, &b) : orig.call(*a, **kw, &b)
      end
    end
    fopen = File.method(:open)
    File.define_singleton_method(:open) do |*a, **kw, &b|
      m = a[1]
      m = kw[:mode] if m.nil?
      AnyPlayFastFiles.forget if m.is_a?(Integer) || (m.is_a?(String) && m =~ /[wa+]/)
      kw.empty? ? fopen.call(*a, &b) : fopen.call(*a, **kw, &b)
    end
  rescue Exception
  end

  [[File, :exist?], [File, :file?], [FileTest, :exist?], [FileTest, :file?]].each do |mod, meth|
    orig = mod.method(meth)
    tag = "#{mod.name}.#{meth}:"
    mod.define_singleton_method(meth) do |path|
      k = AnyPlayFastFiles.known(path)
      if k.nil?
        orig.call(path)
      elsif k == false
        false
      else
        key = tag + path
        yes = AnyPlayFastFiles.yes
        v = yes[key]
        if v.nil?
          v = orig.call(path)
          yes.clear if yes.size > 30_000
          yes[key] = v
        end
        v
      end
    end
  end
end
else
  module AnyPlayFastFiles
    def self.take_stats; ""; end
    def self.slowfs_report; ""; end
  end
end

# ---- AnyPlayLog: one file, "AnyPlayLog.txt" in the game folder -----------------
# Created only when something goes wrong.  It lists (1) scripts that could not be
# parsed, (2) every kind of Ruby error the game hits (also the ones the game
# catches itself - those are marked), (3) the final error that stopped the game
# with the full call chain, and (4) a few notes (touch / slow frames) for context.
module AnyPlayLog
  VERSION   = "0.1.40"
  FILE      = begin
    dir = ENV["TYN_RGSS_DIAGNOSTICS_DIR"]
    dir && !dir.empty? ? File.join(dir, "AnyPlayLog.txt") : "AnyPlayLog.txt"
  rescue Exception
    "AnyPlayLog.txt"
  end
  MAX_BYTES = 250_000
  CAPS      = { error: 200, note: 400 }
  @bytes = 0
  @started = false
  @full = false
  @cnt = Hash.new(0)
  @seen = {}
  @ring = []        # recent exceptions of any kind (written when the game stops)
  @notes = []       # notes kept in memory until a real error shows up
  @slow = 0
  @internal = 0
  @watching = false
  @t0 = Process.clock_gettime(Process::CLOCK_MONOTONIC)
  # every launch starts with a clean file, so it only describes the last run
  (File.delete(FILE) if File.exist?(FILE)) rescue nil
  IGNORE = /\A(SystemExit|Interrupt|StopIteration|SignalException|Reset|RGSSReset)\z/

  class << self
    attr_accessor :internal

    def stamp
      "%s +%ds" % [Time.now.strftime("%H:%M:%S"), (Process.clock_gettime(Process::CLOCK_MONOTONIC) - @t0).to_i]
    end

    # run code without our own error watcher noticing exceptions it rescues
    def quiet
      @internal += 1
      yield
    ensure
      @internal -= 1
    end

    def clean(x, n = 400)
      x.to_s.dup.force_encoding("UTF-8").scrub("?")[0, n]
    rescue Exception
      "?"
    end

    def script_name(i)
      ($RGSS_SCRIPTS[i][1]).to_s
    rescue Exception
      "?"
    end

    # "Section051:1158:in 'x'" -> "Section051 <AudioUtilities>:1158:in 'x'"
    def label(l)
      l = clean(l, 300)
      if l =~ /\ASection(\d+)(:.*)\z/m
        "Section#{$1} <#{script_name($1.to_i)}>#{$2}"
      else
        l
      end
    end

    # the game's own code line (and the one before it) for "Section044 <X>:749:in ..."
    def snippet(l)
      l = l.to_s
      src = $__anyplay_src
      return nil unless src
      key = nil
      src.each_key { |k| key = k if l.start_with?(k + ":") && (key.nil? || k.length > key.length) }
      return nil unless key
      return nil unless l[(key.length + 1)..-1] =~ /\A(\d+)/
      n = $1.to_i
      rows = src[key].split("\n", -1)
      return nil if n < 1 || n > rows.size
      out = []
      out << "        #{n - 1}| " + clean(rows[n - 2].to_s.strip, 160) if n >= 2
      out << "      > #{n}| " + clean(rows[n - 1].to_s.strip, 200)
      out.join("\n")
    rescue Exception
      nil
    end

    def raw(line)
      return if @full
      File.__anyplay_open(FILE, "a") { |f| f.puts(line) }
      @bytes += line.to_s.bytesize + 1
      if @bytes > MAX_BYTES
        @full = true
        File.__anyplay_open(FILE, "a") { |f| f.puts("-- log is full, later problems are not written --") }
      end
    rescue Exception
    end

    def header
      quiet { header_now }
    end

    def header_now
      eng = (TyranorCheats.engine rescue "?")
      gt  = (System.game_title rescue "?") rescue "?"
      raw("==== AnyPlay log (plugin #{VERSION}) #{Time.now.strftime('%Y-%m-%d %H:%M:%S')} ====")
      raw("game=#{clean(gt, 80)} folder=#{File.basename(Dir.pwd)} engine=#{eng} ruby=#{RUBY_VERSION} #{RUBY_PLATFORM}")
      raw("screen=#{(Graphics.width rescue '?')}x#{(Graphics.height rescue '?')} fps=#{(Graphics.frame_rate rescue '?')} scripts=#{($RGSS_SCRIPTS.size rescue '?')}")
    end

    def start
      return if @started
      @started = true
      header
      unless @notes.empty?
        raw("-- notes from before the first error --")
        @notes.each { |n| raw(n) }
        @notes = []
      end
    end

    def context
      quiet { context_now }
    end

    def context_now
      scene = (defined?(SceneManager) && SceneManager.respond_to?(:scene) ? SceneManager.scene : $scene).class rescue "?"
      map = ($game_map.map_id rescue "-")
      "scene=#{scene} map=#{map} frame=#{(Graphics.frame_count rescue '?')} resize=#{($ResizeFactor rescue '?')}"
    end

    # Context notes (touch / slow frames). They do not create the file by
    # themselves; they are written once a real error shows up.
    def write(msg, kind = :note)
      line = "[#{stamp}] #{clean(msg, 600)}"
      if @started
        return if (@cnt[:note] += 1) > CAPS[:note]
        raw(line)
      else
        @notes << line
        @notes.shift while @notes.size > 60
      end
    end

    def slow(msg, weight = 1)
      @slow += weight
      write("slow: " + msg)
      start if @slow == 15   # slowness that keeps happening is worth a file
    end

    # A real problem. Always creates the file.
    def error(msg)
      return if (@cnt[:error] += 1) > CAPS[:error]
      start
      raw("[#{stamp}] #{clean(msg, 1500)}")
    end

    # menu / tap debugging lines (own cap, always creates the file)
    def menu(msg)
      return if (@cnt[:menu] += 1) > 90
      start
      raw("[#{stamp}] #{clean(msg, 700)}")
    end

    def script_errors(errs, compat = "")
      return if errs.nil? || errs.empty?
      start
      raw("[#{stamp}] #{errs.size} script(s) could not be parsed (compat #{compat}):")
      raw(clean(errs.join("\n\n"), 60_000))
    end

    BUG = [NoMethodError, TypeError, ArgumentError, ZeroDivisionError, FloatDomainError,
           RangeError, IndexError, EncodingError, NotImplementedError, LocalJumpError, SyntaxError]

    def bug?(e)
      return true if BUG.any? { |k| e.is_a?(k) }
      return true if Object.const_defined?(:RGSSError) && e.is_a?(Object.const_get(:RGSSError))
      false
    end

    def on_raise(tp)
      return if @internal > 0
      e = tp.raised_exception
      cname = e.class.name.to_s
      return if cname =~ IGNORE
      bt = (e.backtrace || [])
      first = bt.first.to_s
      msg = clean(e.message.to_s.sub(/ for #<([\w:]+)[: ].*\z/m, ' for #<\1>'), 300)
      key = "#{cname}|#{msg[0, 80].gsub(/\d+/, 'N')}|#{first}"
      c = (@seen[key] = (@seen[key] || 0) + 1)
      line = "#{cname}: #{msg} @ #{label(first)}"
      @ring << "[#{stamp}] #{line}" if c == 1
      @ring.shift while @ring.size > 14
      return unless bug?(e)
      if c == 1
        err = "#{line}  (#{quiet { context }})"
        sn = snippet(first)
        err << "\n" + sn if sn
        bt[1, 5].to_a.each_with_index do |l, i|
          err << "\n      from #{label(l)}"
          if i < 2 && (s2 = snippet(l))
            err << "\n" + s2
          end
        end
        err << "\n      (the game may have handled this one itself)"
        error(err)
      elsif [10, 100, 1000, 10000].include?(c)
        error("#{line} -- happened #{c} times")
      end
    rescue Exception
    end

    # Called once, from the Graphics.update wrapper (after the game has loaded).
    def watch
      return if @watching
      @watching = true
      @tp = TracePoint.new(:raise) { |tp| on_raise(tp) }
      @tp.enable
    rescue Exception
    end

    # The error that stopped the game (called from the engine).
    def fatal(exc)
      @internal = 1
      start
      raw("")
      raw("[#{stamp}] ##### GAME STOPPED BY AN ERROR #####")
      raw("#{exc.class}: #{clean(exc.message, 800)}")
      raw(context)
      (exc.backtrace || []).first(40).each_with_index do |l, i|
        raw("   at #{label(l)}")
        if i < 4 && (sn = snippet(l))
          raw(sn)
        end
      end
      if exc.message.to_s =~ /mega/i
        begin
          ObjectSpace.each_object(Bitmap) { |b| raw("   mega bitmap: #{b.width}x#{b.height}") if !b.disposed? && b.mega? }
        rescue Exception
        end
      end
      unless @ring.empty?
        raw("-- recent errors before it --")
        @ring.each { |l| raw(l) }
      end
    rescue Exception
    end
  end
end

# Tap on a menu: pick the tapped entry instead of confirming whatever is highlighted.
#  - list windows (Window_Selectable / SpriteWindow_Selectable): set the window's index, then confirm
#  - sprite panels with "selected" (Essentials load screen): press UP/DOWN to the tapped panel, then confirm
module AnyPlayMenu
  class << self
    def to_game(mx, my)
      f = ($ResizeFactor || 1.0).to_f
      f = 1.0 if f <= 0
      [mx / f - ($ResizeOffsetX || 0).to_f, my / f - ($ResizeOffsetY || 0).to_f]
    end

    def vp_off(o)
      vp = o.viewport
      return [0, 0] if vp.nil? || (vp.disposed? rescue false)
      [vp.rect.x - vp.ox, vp.rect.y - vp.oy]
    rescue Exception
      [0, 0]
    end

    def pad(w)
      if w.respond_to?(:startX) && w.respond_to?(:startY)
        [w.startX, w.startY]
      elsif w.respond_to?(:standard_padding)
        [w.standard_padding, w.standard_padding]
      elsif w.respond_to?(:borderX) && w.respond_to?(:borderY)
        [w.borderX / 2, w.borderY / 2]
      else
        [16, 16]
      end
    rescue Exception
      [16, 16]
    end

    def each_obj(names)
      seen = {}
      names.each do |n|
        next unless Object.const_defined?(n)
        k = Object.const_get(n)
        next unless k.is_a?(Class)
        ObjectSpace.each_object(k) do |o|
          next if seen[o.object_id]
          seen[o.object_id] = true
          yield o
        end
      end
    end

    def item_rect(w, i)
      if w.respond_to?(:itemRect)
        [w.itemRect(i), true]
      elsif w.respond_to?(:item_rect)
        [w.item_rect(i), true]
      else
        cm = (w.instance_variable_get(:@column_max) || 1).to_i
        cm = 1 if cm < 1
        cw = w.width / cm - 32
        [Rect.new(i % cm * (cw + 32), i / cm * 32 - w.oy, cw, 32), false]
      end
    end

    def item_count(w)
      n = (w.item_max rescue nil)
      n = (w.itemCount rescue nil) unless n.is_a?(Integer)
      n.is_a?(Integer) && n > 0 && n <= 300 ? n : nil
    end

    def window_at(gx, gy)
      best = nil
      each_obj(%w(Window SpriteWindow)) do |w|
        begin
          next if w.disposed?
          next unless w.visible && w.active
          next unless w.respond_to?(:index) && w.respond_to?(:index=) && item_count(w)
          vx, vy = vp_off(w)
          wx = w.x + vx
          wy = w.y + vy
          next unless gx >= wx && gx < wx + w.width && gy >= wy && gy < wy + w.height
          best = w if best.nil? || w.z >= best.z
        rescue Exception
        end
      end
      best
    end

    def pick_item(w, gx, gy)
      n = item_count(w)
      return nil unless n
      px, py = pad(w)
      vx, vy = vp_off(w)
      ox = (w.ox rescue 0).to_i
      oy = (w.oy rescue 0).to_i
      n.times do |i|
        r, scrolled = item_rect(w, i)
        next if r.nil? || r.width <= 0 || r.height <= 0
        x = w.x + vx + px + r.x - (scrolled ? ox : 0)
        y = w.y + vy + py + r.y - (scrolled ? oy : 0)
        return i if gx >= x && gx < x + r.width && gy >= y && gy < y + r.height
      end
      nil
    end

    # Sprites that look like menu panels (they have "selected"): [sprite, x, y, w, h], top to bottom.
    def collect_panels
      panels = []
      each_obj(%w(Sprite SpriteWrapper)) do |s|
        begin
          next unless s.respond_to?(:selected) && s.respond_to?(:selected=)
          next if s.disposed?
          next unless s.visible
          bm = s.bitmap
          next if bm.nil? || bm.disposed?
          vx, vy = vp_off(s)
          zx = (s.zoom_x rescue 1.0)
          zy = (s.zoom_y rescue 1.0)
          sr = (s.src_rect rescue nil)
          sw = sr && sr.width > 0 ? sr.width : bm.width
          sh = sr && sr.height > 0 ? sr.height : bm.height
          panels << [s, s.x - s.ox * zx + vx, s.y - s.oy * zy + vy, sw * zx, sh * zy]
        rescue Exception
        end
      end
      panels.sort_by { |p| [p[2], p[1]] }
    end

    # Does the tap land on a visible (not see-through) pixel of this panel?
    # (the panel bitmaps can be taller than what is drawn, so the box alone is not enough)
    def opaque_at?(p, gx, gy)
      s = p[0]
      bm = s.bitmap
      zx = (s.zoom_x rescue 1.0)
      zy = (s.zoom_y rescue 1.0)
      sr = (s.src_rect rescue nil)
      lx = ((gx - p[1]) / zx).to_i + (sr ? sr.x : 0)
      ly = ((gy - p[2]) / zy).to_i + (sr ? sr.y : 0)
      return false if lx < 0 || ly < 0 || lx >= bm.width || ly >= bm.height
      bm.get_pixel(lx, ly).alpha > 0
    rescue Exception
      true
    end

    def panel_vertical?(panels)
      (panels.last[2] - panels.first[2]).abs >= (panels.last[1] - panels.first[1]).abs
    end

    # One step of the "walk the highlight to the tapped panel" loop (called once per frame).
    # Returns a button to press, :done (now confirm) or :abort.
    def drive(st)
      panels = collect_panels
      return :abort if panels.size < 2
      ti = panels.index { |p| p[0].equal?(st[:target]) }
      ci = panels.index { |p| (p[0].selected rescue false) }
      return :abort unless ti && ci
      return :done if ti == ci
      st[:steps] += 1
      return :abort if st[:steps] > 30
      st[:stall] = (st[:last] == ci ? st[:stall] + 1 : 0)
      st[:last] = ci
      return :abort if st[:stall] > 12
      v = st[:vertical]
      ti > ci ? (v ? Input::DOWN : Input::RIGHT) : (v ? Input::UP : Input::LEFT)
    end

    def say(msg)
      AnyPlayLog.write(msg) if @dbg
    end

    def windows_dump
      out = []
      each_obj(%w(Window SpriteWindow)) do |w|
        begin
          next if w.disposed? || !w.visible
          out << "#{w.class}@#{w.x.to_i},#{w.y.to_i} #{w.width.to_i}x#{w.height.to_i} act=#{(w.active rescue '?')} idx=#{(w.index rescue '-')} n=#{(item_count(w) rescue '-')}"
        rescue Exception
        end
      end
      out.first(8)
    end

    # Is a list / command window on screen and ready for input (anywhere, not only under the finger)?
    def selectable_active?
      found = false
      each_obj(%w(Window SpriteWindow)) do |w|
        begin
          next if found || w.disposed?
          found = true if w.visible && w.active && w.respond_to?(:index) && w.respond_to?(:index=) && item_count(w)
        rescue Exception
        end
      end
      found
    end

    # Is a typing message box ("press a button to go on") showing?
    def message_visible?
      found = false
      each_obj(%w(Window SpriteWindow)) do |w|
        begin
          next if found || w.disposed?
          found = true if w.visible && w.class.name.to_s =~ /AdvancedText|MessageWindow|Window_Message/
        rescue Exception
        end
      end
      found
    end

    def debug(mx, my, gx, gy, panels)
      list = panels.map { |p| "#{p[0].class}@#{p[1].to_i},#{p[2].to_i} #{p[3].to_i}x#{p[4].to_i}#{(p[0].selected rescue false) ? '*' : ''}" }
      say("  menu-debug: tap=#{mx},#{my} game=#{gx.to_i},#{gy.to_i} gfx=#{(Graphics.width rescue '?')}x#{(Graphics.height rescue '?')} resize=#{$ResizeFactor.inspect} off=#{$ResizeOffsetX.inspect},#{$ResizeOffsetY.inspect} panels=#{list.inspect}")
    rescue Exception
    end

    # nil = nothing found (caller just confirms), :confirm = confirm now, :ignore = do nothing,
    # Hash = walk the highlight to a panel first (see drive)
    # called right before the confirm key is handed to the game: make sure the tapped entry is still the highlighted one
    def apply_pin
      pin, @pin = @pin, nil
      return "" unless pin
      w, i = pin
      w.index = i unless (w.index == i rescue true)
      " index=#{(w.index rescue '?')}"
    rescue Exception
      ""
    end

    def tap(mx, my)
      @pin = nil
      gx, gy = to_game(mx, my)
      panels = collect_panels
      sig = windows_dump.map { |s| s.sub(/ act=.*/, "") }.join(";") + "|P#{panels.size}"
      @sigs ||= {}
      @dbg = !@sigs[sig] && @sigs.size < 30
      @sigs[sig] = true
      say("  menu-tap #{mx},#{my} windows=#{windows_dump.inspect}") if @dbg
      # 1) a list / command window under the finger: pick that entry
      w = window_at(gx, gy)
      if w
        i = pick_item(w, gx, gy)
        say("  menu: window #{w.class} index=#{(w.index rescue '?')} tapped_item=#{i.inspect}")
        return :ignore unless i
        w.index = i unless w.index == i
        @pin = [w, i]
        return :confirm
      end
      # 2) a list is waiting for a choice but the tap is not on it: do nothing (no accidental Enter)
      if selectable_active?
        say("  menu: tap outside the active list, ignored")
        return :ignore
      end
      # 3) a message box is showing: tap = go on
      if message_visible?
        say("  menu: message showing, confirm")
        return nil
      end
      # 4) panels (load screen, party ...)
      debug(mx, my, gx, gy, panels)
      return nil if panels.size < 2
      hits = panels.select { |p| gx >= p[1] && gx < p[1] + p[3] && gy >= p[2] && gy < p[2] + p[4] && opaque_at?(p, gx, gy) }
      if hits.empty?
        say("  menu: tap is not on a panel, ignored")
        return :ignore
      end
      hit = hits.max_by { |p| [(p[0].z rescue 0), panels.index(p)] }
      ci = panels.index { |p| (p[0].selected rescue false) }
      ti = panels.index(hit)
      say("  menu: panel #{ti} tapped, highlighted=#{ci.inspect}")
      return nil unless ci
      return :confirm if ci == ti
      { target: hit[0], vertical: panel_vertical?(panels), steps: 0, stall: 0, last: nil }
    rescue Exception => e
      AnyPlayLog.write("  menu failed: #{e.class} #{e.message[0, 80]}")
      nil
    end
  end
end

module AnyPlayTouch
  DIRS = { 2 => [0, 1], 4 => [-1, 0], 6 => [1, 0], 8 => [0, -1] }
  @route = []          # list of directions still to walk
  @face = nil          # direction to face at the end (to talk to an NPC)
  @dir = nil           # direction handed to the game this frame
  @press_c = 0
  @keys = []
  @key_frame = -1
  @key_age = 0
  @tap_seq = nil
  @stuck = 0
  @hooked = false
  @errors = 0
  @sized = false
  @native_resize = nil
  class << self
    attr_accessor :dir, :press_c, :keys

    # buttons queued by a menu tap, handed to the game one per frame
    def next_key(b)
      k = @keys.first
      return false if k.nil? || k != b
      fr = (Graphics.frame_count rescue 0)
      return false if fr == @key_frame
      @key_frame = fr
      @keys.shift
      true
    end
    attr_writer :native_resize

    def tile_w
      (Game_Map::TILEWIDTH rescue 32)
    end

    def tile_h
      (Game_Map::TILEHEIGHT rescue 32)
    end

    def scene_is_map?
      sc = (defined?(SceneManager) ? SceneManager.scene : $scene)
      return false if sc.nil?
      return true if defined?(Scene_Map) && sc.is_a?(Scene_Map)
      sc.class.name.to_s.include?("Map") || sc.instance_variable_defined?(:@spriteset)
    rescue Exception
      false
    end

    def map_free?
      return false unless scene_is_map?
      return false if ($game_temp.message_window_showing rescue false)
      return false if ($game_message.visible rescue false)
      return false if ($game_system.map_interpreter.running? rescue false)
      return false if ($game_map.interpreter.running? rescue false)
      true
    end

    def mouse_down?
      @gaks ||= Win32API.new('user32', 'GetAsyncKeyState', 'i', 'i')
      (@gaks.call(1) & 0x8000) != 0
    rescue Exception
      false
    end

    def hook_input
      return if @hooked
      return unless defined?(Input) && Input.respond_to?(:dir4)
      @hooked = true
      class << Input
        alias_method :__ap_dir4, :dir4
        def dir4
          d = AnyPlayTouch.dir
          return d if d
          __ap_dir4
        end
        alias_method :__ap_trigger, :trigger?
        def trigger?(b)
          if AnyPlayTouch.press_c > 0 && (b == Input::C)
            AnyPlayTouch.press_c = 0
            AnyPlayLog.write("  confirm sent" + AnyPlayMenu.apply_pin.to_s)
            return true
          end
          return true if AnyPlayTouch.next_key(b)
          __ap_trigger(b)
        end
        if Input.respond_to?(:repeat?)
          alias_method :__ap_repeat, :repeat?
          def repeat?(b)
            if AnyPlayTouch.press_c > 0 && (b == Input::C)
              AnyPlayTouch.press_c = 0
              return true
            end
            return true if AnyPlayTouch.next_key(b)
            __ap_repeat(b)
          end
        end
      end
    end

    def screen_to_tile(mx, my)
      f = ($ResizeFactor || 1.0).to_f
      f = 1.0 if f <= 0
      ox = ($ResizeOffsetX || 0).to_f
      oy = ($ResizeOffsetY || 0).to_f
      tw = tile_w * f
      th = tile_h * f
      psx = ($game_player.screen_x + ox) * f
      psy = ($game_player.screen_y + oy) * f - th / 2.0
      [$game_player.x + ((mx - psx) / tw).round, $game_player.y + ((my - psy) / th).round]
    end

    def event_at(x, y)
      evs = $game_map.events
      list = evs.respond_to?(:values) ? evs.values : evs
      list.find do |e|
        next false unless e.x == x && e.y == y && !e.through
        e.respond_to?(:list) ? (e.list && e.list.length > 1) : true
      end
    rescue Exception
      nil
    end

    def can_step?(x, y, d)
      $game_player.passable?(x, y, d)
    rescue Exception
      false
    end

    def plan(tx, ty)
      sx = $game_player.x
      sy = $game_player.y
      start = [sx, sy]
      prev = { start => nil }
      queue = [start]
      i = 0
      while i < queue.length && prev.length < 5000
        cur = queue[i]; i += 1
        DIRS.each do |d, (dx, dy)|
          nx = cur[0] + dx
          ny = cur[1] + dy
          next if prev.key?([nx, ny])
          next unless $game_map.valid?(nx, ny)
          next unless can_step?(cur[0], cur[1], d)
          prev[[nx, ny]] = [cur, d]
          queue << [nx, ny]
        end
      end
      ev = event_at(tx, ty)
      goal = nil
      face = nil
      if ev && prev.length > 1
        # walk next to the NPC/object and face it
        best = nil
        DIRS.each do |d, (dx, dy)|
          n = [tx - dx, ty - dy]
          next unless prev.key?(n)
          len = path_len(prev, n)
          best = [n, d, len] if best.nil? || len < best[2]
        end
        if best
          goal = best[0]; face = best[1]
        end
      end
      if goal.nil?
        if prev.key?([tx, ty])
          goal = [tx, ty]
        else
          goal = queue.min_by { |n| [(n[0] - tx).abs + (n[1] - ty).abs, path_len(prev, n)] }
        end
      end
      dirs = []
      n = goal
      while prev[n]
        dirs.unshift(prev[n][1])
        n = prev[n][0]
      end
      [dirs, face]
    end

    def path_len(prev, n)
      c = 0
      while prev[n]
        n = prev[n][0]; c += 1
      end
      c
    end

    def turn(d)
      case d
      when 2 then $game_player.turn_down
      when 4 then $game_player.turn_left
      when 6 then $game_player.turn_right
      when 8 then $game_player.turn_up
      end
    rescue Exception
    end

    # Writes WHY the map is not free and where the game is waiting (the Ruby
    # call stack of the code that called Graphics.update). Only the first taps.
    def diag_not_free
      @diag = (@diag || 0) + 1
      return if @diag > 4
      why = []
      why << "scene_not_map" unless scene_is_map?
      why << "msgwin" if ($game_temp.message_window_showing rescue false)
      why << "gmsg" if ($game_message.visible rescue false)
      why << "interp" if (($game_system.map_interpreter.running? rescue false) || ($game_map.interpreter.running? rescue false))
      AnyPlayLog.write("  not free: #{why.join(',')} frame=#{(Graphics.frame_count rescue '?')}")
      caller(0).reject { |l| l =~ /\(eval|anyplay/i }.first(6).each { |l| AnyPlayLog.write("    at #{l}") }
      diag_interp
    rescue Exception
    end

    # What is the map interpreter waiting for? (helps find stuck events)
    def diag_interp
      it = ($game_system.map_interpreter rescue nil) || ($game_map.interpreter rescue nil)
      return unless it
      ev = it.instance_variable_get(:@event_id)
      idx = it.instance_variable_get(:@index)
      list = it.instance_variable_get(:@list)
      cmd = (list && idx && list[idx]) ? "code=#{list[idx].code} params=#{list[idx].parameters.inspect[0, 120]}" : "none"
      AnyPlayLog.write("  interp: event=#{ev.inspect} index=#{idx.inspect} #{cmd}")
      %w(@wait_count @move_route_waiting @message_waiting @button_input_variable_id @waiting_for_move_route @wait_for_move).each do |v|
        next unless it.instance_variable_defined?(v)
        AnyPlayLog.write("    #{v}=#{it.instance_variable_get(v).inspect}")
      end
      child = it.instance_variable_get(:@child_interpreter)
      AnyPlayLog.write("    child_interpreter=#{child ? 'yes' : 'no'}") if it.instance_variable_defined?(:@child_interpreter)
      ids = ($game_map.events.values.select { |e| e.respond_to?(:move_route_forcing) && e.move_route_forcing }.map { |e| e.id } rescue [])
      AnyPlayLog.write("    forcing_move_route_events=#{ids.inspect} player_forcing=#{($game_player.move_route_forcing rescue '?')} player_moving=#{($game_player.moving? rescue '?')}")
      diag_route($game_player, "player")
      diag_route(($game_map.events[ev] rescue nil), "event#{ev}")
    rescue Exception => e
      AnyPlayLog.write("  diag_interp failed: #{e.class} #{e.message[0, 60]}")
    end

    # Why is a forced move route not finishing?
    def diag_route(ch, label)
      return unless ch && ch.instance_variable_get(:@move_route_forcing)
      r = ch.instance_variable_get(:@move_route)
      i = ch.instance_variable_get(:@move_route_index)
      c = r && r.list[i]
      d = ch.direction
      dx = { 2 => 0, 4 => -1, 6 => 1, 8 => 0 }[d].to_i
      dy = { 2 => 1, 4 => 0, 6 => 0, 8 => -1 }[d].to_i
      AnyPlayLog.write("    #{label}: pos=#{ch.x},#{ch.y} dir=#{d} idx=#{i}/#{r && r.list.size} cmd=#{c ? c.code : 'nil'} #{c ? c.parameters.inspect[0, 60] : ''} skippable=#{r && r.skippable} repeat=#{r && r.repeat}")
      AnyPlayLog.write("    #{label}: wait=#{ch.instance_variable_get(:@wait_count).inspect} stop=#{ch.instance_variable_get(:@stop_count).inspect} moving=#{ch.moving?} jumping=#{ch.jumping?} through=#{ch.through} passable_dir=#{(ch.passable?(ch.x, ch.y, d) rescue 'err')}")
      evs = ($game_map.events.values.select { |e| e.x == ch.x + dx && e.y == ch.y + dy }.map { |e| "#{e.id}(through=#{e.through})" } rescue [])
      AnyPlayLog.write("    #{label}: front=#{ch.x + dx},#{ch.y + dy} events_in_front=#{evs.inspect} map=#{($game_map.map_id rescue '?')}")
    rescue Exception => e
      AnyPlayLog.write("  diag_route failed: #{e.class} #{e.message[0, 60]}")
    end

    def on_tap(mx, my)
      @route = []; @face = nil; @dir = nil; @expect = nil; @keys = []; @pan = nil
      AnyPlayLog.write("tap #{mx},#{my} scene=#{(defined?(SceneManager) ? SceneManager.scene : $scene).class} free=#{map_free?} player=#{($game_player.x rescue '?')},#{($game_player.y rescue '?')}")
      unless map_free?
        diag_not_free
        r = AnyPlayMenu.tap(mx, my)     # menus: pick the tapped entry first
        if r.is_a?(Hash)
          @pan = r            # walk the highlight to that panel, then confirm (see tick)
        elsif r != :ignore
          @press_c = 4        # dialogs, text, title: tap = confirm
        end
        return
      end
      tx, ty = screen_to_tile(mx, my)
      if tx == $game_player.x && ty == $game_player.y
        @press_c = 4
        return
      end
      route, face = plan(tx, ty)
      AnyPlayLog.write("  target=#{tx},#{ty} route=#{route.length} face=#{face.inspect}")
      if route.empty?
        if face
          turn(face); @press_c = 4
        elsif (ev = event_at(tx, ty))
          @press_c = 4
        end
        return
      end
      @route = route
      @face = face
      @stuck = 0
    rescue Exception
      @route = []
    end

    def walk
      if !map_free?
        @route = []; @face = nil; @dir = nil; @expect = nil
        return
      end
      if @route.empty? && !@expect
        @dir = nil
        return
      end
      if $game_player.moving?
        @dir = nil
        return
      end
      if @expect
        if $game_player.x == @expect[0] && $game_player.y == @expect[1]
          @route.shift
          @stuck = 0
        else
          @stuck += 1
        end
        @expect = nil
        if @stuck > 15
          @route = []; @face = nil; @dir = nil; @stuck = 0
          return
        end
      end
      if @route.empty?
        @dir = nil
        if @face
          turn(@face); @face = nil; @press_c = 4
        end
        return
      end
      d = @route.first
      @dir = d
      @expect = [$game_player.x + DIRS[d][0], $game_player.y + DIRS[d][1]]
    end

    def tick
      hook_input
      if @press_c > 0
        @press_c -= 1
      end
      unless @keys.empty?
        @key_age += 1
        @keys = [] if @key_age > 90
      end
      if @pan && @keys.empty?
        k = AnyPlayMenu.drive(@pan)
        if k == :done
          @pan = nil
          @press_c = 4
        elsif k == :abort
          AnyPlayLog.write("  menu: could not reach the tapped panel, gave up")
          @pan = nil
        else
          @keys = [k]
          @key_age = 0
        end
      end
      if Input.respond_to?(:anyplay_tap_seq)
        seq = Input.anyplay_tap_seq
        if @tap_seq.nil?
          @tap_seq = seq
        elsif seq != @tap_seq
          @tap_seq = seq
          on_tap(Input.anyplay_tap_x, Input.anyplay_tap_y)
        end
      end
      if defined?($game_player) && $game_player
        # any real direction key cancels the walk
        if @route.any? && !@dir
          real = (Input.__ap_dir4 rescue 0)
          if real && real > 0
            @route = []; @face = nil
          end
        end
        walk
      else
        @dir = nil
      end
    end

    def fit_screen
      return if @sized
      return unless defined?(DEFAULTSCREENWIDTH) && defined?(DEFAULTSCREENHEIGHT)
      @sized = true
      return unless @native_resize
      @native_resize.call(DEFAULTSCREENWIDTH, DEFAULTSCREENHEIGHT)
    rescue Exception
    end

    def frame
      return if @errors > 20
      fit_screen
      tick
    rescue Exception
      @errors += 1
      @route = []; @dir = nil
    end
  end
end

# ---- AnyPlay cheats (RPG Maker XP / VX / Ace and Pokemon Essentials) --------
# The Android overlay writes one command per file into the app cache folder,
# this module reads it (every few frames), runs it, and writes a one line
# answer back.  Checkbox cheats are just flags that are applied every frame.
module TyranorCheats
  DIR   = "/data/data/com.tyranor.runtime.rgss/cache"
  CMD   = DIR + "/tyranor_cheat_cmd.txt"
  REPLY = DIR + "/tyranor_cheat_reply.txt"
  NOPE  = "This cheat doesn't work in this type of game."
  NEED_BATTLE = "You are not in a battle right now."

  @on = {}
  @speed = 2.0
  @engine = nil
  @ess21 = false
  @tick = 0
  @errors = 0
  @hooked = false
  @item_snap = nil
  @old_textspeed = nil
  @through_forced = false
  @battle = nil

  class << self
    attr_accessor :battle
    attr_reader :speed

    # ---------------------------------------------------------------- engine
    # :essentials = Pokemon Essentials (v17-v19 style, and v20/v21 style),
    # :xp / :vx / :ace = plain RPG Maker XP / VX / VX Ace games.
    def engine
      return @engine if @engine
      if defined?(PokeBattle_Battle) && defined?(PokemonBag) && (defined?(PokeBattle_Pokemon) || defined?(Pokemon))
        @engine = :essentials
        @ess21 = false
      elsif defined?(Battle) && defined?(Pokemon) && defined?(PokemonBag) && defined?(GameData)
        @engine = :essentials
        @ess21 = true
      elsif defined?(Game_Actor) && defined?(Game_Party)
        m = Game_Actor.instance_methods
        @engine = if m.include?(:tp) && m.include?(:mp) then :ace
                  elsif m.include?(:mp) then :vx
                  else :xp end
      else
        return :unknown
      end
      @engine
    rescue Exception
      :unknown
    end

    def ess?; engine == :essentials; end
    def ess21?; ess? && @ess21; end
    # Items and species are looked up by name (symbols) in Essentials v19 and
    # newer, and by number (PBItems / PBSpecies) in v17 and older.
    def gd?
      return @gd unless @gd.nil?
      @gd = (ess? && defined?(GameData::Item) && GameData::Item.respond_to?(:each)) ? true : false
    rescue Exception
      false
    end
    def rpgm?; [:xp, :vx, :ace].include?(engine); end

    def on?(name); @on[name] ? true : false; end
    def speed_on?; @on['speed'] ? true : false; end
    def speed_bonus
      m = @speed.to_f
      m = 1.0 if m <= 0
      Math.log(m) / Math.log(2)
    end

    # ------------------------------------------------------- party helpers
    def actors
      p = $game_party
      return [] unless p
      list = if p.respond_to?(:members) then p.members
             elsif p.respond_to?(:actors) then p.actors
             else [] end
      list.compact
    rescue Exception
      []
    end

    # Essentials: the player object, the bag, and the party (v17 and v21 names)
    def trainer; $player || $Trainer; end
    def bag; $bag || $PokemonBag; end
    def bag_add(id, q)
      bag.respond_to?(:add) ? bag.add(id, q) : bag.pbStoreItem(id, q)
    end
    def bag_qty(id)
      bag.respond_to?(:quantity) ? bag.quantity(id) : bag.pbQuantity(id)
    end
    def key_item?(i)
      if i.respond_to?(:is_key_item?) then i.is_key_item?
      else (pbIsKeyItem?(i) rescue false) end
    end

    def pokes
      t = trainer
      return [] unless t && t.respond_to?(:party)
      (t.party || []).compact
    rescue Exception
      []
    end

    def in_battle?
      case engine
      when :essentials then !@battle.nil?
      when :ace then ($game_party && $game_party.respond_to?(:in_battle?) && $game_party.in_battle?) ? true : false
      else ($game_temp && $game_temp.respond_to?(:in_battle) && $game_temp.in_battle) ? true : false
      end
    rescue Exception
      false
    end

    def maxhp_of(a); a.respond_to?(:mhp) ? a.mhp : a.maxhp; end
    def maxmp_of(a)
      return a.mmp if a.respond_to?(:mmp)
      return a.maxsp if a.respond_to?(:maxsp)
      a.maxmp
    end

    # ---- Essentials helpers that differ between versions
    def foe?(battle, idx)
      battle.respond_to?(:opposes?) ? battle.opposes?(idx) : battle.pbIsOpposing?(idx)
    end

    def no_status?(st)
      st.nil? || st == 0 || st.to_s == "NONE" || st.to_s == "0"
    end

    def mon_heal_status(p)
      if p.respond_to?(:heal_status) then p.heal_status else p.healStatus end
    end

    def mon_heal_pp(p)
      if p.respond_to?(:heal_PP) then p.heal_PP else p.healPP end
    end

    def mon_calc(p)
      if p.respond_to?(:calc_stats) then p.calc_stats else p.calcStats end
    end

    def max_level
      if gd?
        (GameData::GrowthRate.max_level rescue Settings::MAXIMUM_LEVEL)
      else
        PBExperience::MAXLEVEL
      end
    end

    def max_exp_of(p)
      if gd?
        p.growth_rate.maximum_exp
      else
        PBExperience.pbGetMaxExperience(p.growthrate)
      end
    end

    # ------------------------------------------------------------- commands
    # Returns the text that is shown to the player as a toast.
    def handle(cmd, a = "", b = "")
      case cmd
      when 'restore' then restore(a, b)
      when 'toggle'  then toggle(a, b == '1')
      when 'value'   then set_value(a, b)
      when 'give'    then give(a, b)
      when 'run'     then run_action(a)
      when 'list'    then list_items(a)
      else "Unknown cheat command."
      end
    rescue Exception => e
      where = (e.backtrace || []).find { |l| l !~ /anyplay|\(eval/i }.to_s[0, 60]
      AnyPlayLog.error("cheat #{cmd} #{a} #{b} failed: #{e.class}: #{e.message}\n  #{(e.backtrace || []).first(8).join("\n  ")}") rescue nil
      "Cheat failed: #{e.class}: #{e.message[0, 70]} (#{where})"
    end

    def restore(speed, names)
      s = speed.to_f
      @speed = s if s > 0
      @on = {}
      names.to_s.split(',').each { |n| @on[n] = true unless n.empty? }
      @item_snap = nil
      ""
    end

    def toggle(name, enable)
      unless toggle_supported?(name)
        @on.delete(name)
        return "#{NOPE}"
      end
      if enable
        @on[name] = true
        @item_snap = nil if name == 'items'
      else
        @on.delete(name)
        toggle_off(name)
      end
      ""
    end

    def toggle_supported?(name)
      case name
      when 'tp'                         then engine == :ace
      when 'fastText'                   then ess?
      when 'oneHit', 'invincible', 'crit', 'escape', 'freeSkill' then ess?
      when 'hpmp', 'gold', 'status', 'items', 'noclip', 'noEncounter', 'speed'
        ess? || rpgm?
      else false
      end
    end

    def toggle_off(name)
      case name
      when 'noclip'
        $game_player.through = false if $game_player && @through_forced
        @through_forced = false
      when 'fastText'
        if @old_textspeed && defined?($PokemonSystem) && $PokemonSystem
          $PokemonSystem.textspeed = @old_textspeed
        end
        if @fast_text_set && defined?(MessageConfig) && MessageConfig.respond_to?(:pbSetTextSpeed)
          (MessageConfig.pbSetTextSpeed(MessageConfig.pbSettingToTextSpeed(@old_textspeed)) rescue nil)
        end
        @fast_text_set = false
        @old_textspeed = nil
      when 'items'
        @item_snap = nil
      end
    end

    # ------------------------------------------------------------- values
    def set_value(name, v)
      if name == 'speed'
        s = v.to_f
        return "Enter a number above 0." unless s > 0
        @speed = [[s, 0.25].max, 8.0].min
        @on['speed'] = true
        return "Speed x#{@speed}"
      end
      n = v.to_s.strip
      return "Enter a number first." if n.empty?
      n = n.to_i
      return "Enter a number first." if n < 0
      case engine
      when :essentials then ess_value(name, n)
      when :xp, :vx, :ace then rpgm_value(name, n)
      else NOPE
      end
    end

    def ess_value(name, n)
      case name
      when 'gold'
        t = trainer
        return "Trainer data isn't ready yet. Start or load a game first." unless t
        t.money = n
        "Money set to #{t.money}"
      when 'level'
        ps = pokes
        return "You have no Pokemon yet." if ps.empty?
        lv = [[n, 1].max, max_level].min
        ps.each do |p|
          next if p.egg?
          p.level = lv
          mon_calc(p)
        end
        "All Pokemon set to level #{lv}"
      when 'exp'
        ps = pokes
        return "You have no Pokemon yet." if ps.empty?
        ps.each do |p|
          next if p.egg?
          p.exp = [n, max_exp_of(p)].min
          mon_calc(p)
        end
        "EXP set for all Pokemon"
      else
        NOPE
      end
    end

    def rpgm_value(name, n)
      return "Start or load a game first." unless $game_party
      case name
      when 'gold'
        $game_party.instance_variable_set(:@gold, [n, 99_999_999].min)
        "Gold set to #{n}"
      when 'level'
        ac = actors
        return "Your party is empty." if ac.empty?
        ac.each do |a|
          if a.respond_to?(:change_level)
            a.change_level(n, false)
          else
            a.level = n
          end
        end
        "Party level set to #{n}"
      when 'exp'
        ac = actors
        return "Your party is empty." if ac.empty?
        ac.each do |a|
          if a.respond_to?(:change_exp)
            a.change_exp(n, false)
          else
            a.exp = n
          end
        end
        "Party EXP set to #{n}"
      when 'atk', 'def', 'mat', 'mdf', 'agi', 'luk'
        set_stat(name, n)
      else
        NOPE
      end
    end

    def set_stat(name, n)
      ac = actors
      return "Your party is empty." if ac.empty?
      done = false
      ac.each do |a|
        if engine == :ace
          id = { 'atk' => 2, 'def' => 3, 'mat' => 4, 'mdf' => 5, 'agi' => 6, 'luk' => 7 }[name]
          a.add_param(id, n - a.param(id))
          done = true
        elsif engine == :vx
          k = { 'atk' => :atk, 'def' => :def, 'mat' => :spi, 'agi' => :agi }[name]
          next unless k
          cur = a.send(k)
          a.send("#{k}_plus=", a.send("#{k}_plus") + (n - cur))
          done = true
        else
          k = { 'atk' => :str, 'mat' => :int, 'agi' => :agi, 'luk' => :dex }[name]
          next unless k
          cur = a.send(k)
          a.send("#{k}_plus=", a.send("#{k}_plus") + (n - cur))
          done = true
        end
      end
      done ? "Stat set to #{n} for the whole party" : NOPE
    end

    # -------------------------------------------------------------- items
    # kind = item / weapon / armor / pokemon,  arg = "key<TAB>amount"
    # (key is the number, or for newer Essentials the item/species name).
    def give(kind, arg)
      key, qty_s = arg.to_s.split("\t", 2)
      key = key.to_s.strip
      qty = qty_s.to_s.strip.empty? ? 1 : qty_s.to_i
      return "Pick something first (use Pick)." if key.empty?
      return "Amount must be 1 or more." if qty < 1
      case engine
      when :essentials then ess_give(kind, key, qty)
      when :xp, :vx, :ace then rpgm_give(kind, key.to_i, qty)
      else NOPE
      end
    end

    def ess_give(kind, key, qty)
      case kind
      when 'item'
        return "Bag isn't ready yet. Start or load a game first." unless bag
        if gd?
          sym = key.to_sym
          ok = bag_add(sym, qty)
          ok ? "Added #{qty} x #{GameData::Item.get(sym).name}" : "Your bag is full or doesn't accept this item."
        else
          id = key.to_i
          ok = bag.pbStoreItem(id, qty)
          ok ? "Added #{qty} x #{PBItems.getName(id)}" : "Your bag is full or doesn't accept this item."
        end
      when 'pokemon'
        return "Trainer data isn't ready yet. Start or load a game first." unless trainer
        lv = [[qty, 1].max, max_level].min
        sp = gd? ? key.to_sym : key.to_i
        r = pbAddPokemonSilent(sp, lv)
        r ? "Pokemon added (level #{lv})" : "Couldn't add it. Your party and storage may be full."
      else
        NOPE
      end
    end

    def rpgm_give(kind, id, qty)
      return "Start or load a game first." unless $game_party
      return "Pick something first (use Pick)." if id <= 0
      return NOPE unless %w[item weapon armor].include?(kind)
      if engine == :xp
        case kind
        when 'item'   then $game_party.gain_item(id, qty)
        when 'weapon' then $game_party.gain_weapon(id, qty)
        when 'armor'  then $game_party.gain_armor(id, qty)
        end
      else
        obj = table_for(kind)[id] rescue nil
        return "That ID doesn't exist in this game." unless obj
        $game_party.gain_item(obj, qty)
      end
      "Added #{qty} x #{item_name(kind, id)}"
    end

    def table_for(kind)
      case kind
      when 'item'   then $data_items
      when 'weapon' then $data_weapons
      when 'armor'  then $data_armors
      end
    end

    def item_name(kind, id)
      t = table_for(kind)
      (t && t[id]) ? t[id].name.to_s : "item #{id}"
    rescue Exception
      "item #{id}"
    end

    # Answer is "kind<TAB>key<TAB>name" lines, one per thing the player can pick.
    def list_items(_ignored = "")
      out = []
      case engine
      when :essentials
        if gd?
          GameData::Item.each do |i|
            n = i.name.to_s
            out << "item\t#{i.id}\t#{n}" unless n.empty?
          end
          GameData::Species.each do |s|
            next if s.form != 0
            n = s.name.to_s
            out << "pokemon\t#{s.species}\t#{n}" unless n.empty?
          end
        else
          (1..(PBItems.maxValue rescue 0)).each do |i|
            n = (PBItems.getName(i) rescue "").to_s
            out << "item\t#{i}\t#{n}" unless n.empty?
          end
          (1..(PBSpecies.maxValue rescue 0)).each do |i|
            n = (PBSpecies.getName(i) rescue "").to_s
            out << "pokemon\t#{i}\t#{n}" unless n.empty?
          end
        end
      when :xp, :vx, :ace
        %w[item weapon armor].each do |kind|
          t = table_for(kind)
          next unless t
          (1...t.size).each do |i|
            o = t[i]
            next unless o
            n = o.name.to_s
            out << "#{kind}\t#{i}\t#{n}" unless n.empty?
          end
        end
      end
      out.join("\n")
    end

    # ------------------------------------------------------ one-click actions
    def run_action(name)
      case name
      when 'maxExp'        then max_exp
      when 'instantWin'    then instant_win
      when 'fullInventory' then full_inventory
      else "Unknown action."
      end
    end

    def max_exp
      case engine
      when :essentials
        ps = pokes
        return "You have no Pokemon yet." if ps.empty?
        ps.each do |p|
          next if p.egg?
          p.level = max_level
          mon_calc(p)
        end
        "All Pokemon are now at the maximum level"
      when :xp, :vx, :ace
        ac = actors
        return "Your party is empty." if ac.empty?
        ac.each do |a|
          if a.respond_to?(:change_level) && a.respond_to?(:max_level)
            a.change_level(a.max_level, false)
          elsif a.respond_to?(:change_exp)
            a.change_exp(99_999_999, false)
          else
            a.exp = 99_999_999
          end
        end
        "Party is at the maximum level"
      else
        NOPE
      end
    end

    def instant_win
      case engine
      when :essentials
        b = @battle
        return NEED_BATTLE unless b
        foes = b.battlers.compact.select { |x| foe?(b, x.index) && !x.fainted? }
        foes.each do |f|
          f.pbReduceHP(f.hp, false)
          f.pbFaint(true)
        end
        foe_party = b.respond_to?(:party2) ? b.party2 : b.pbParty(1)
        (foe_party || []).each { |p| p.hp = 0 if p }
        b.pbJudge
        "Foes defeated. Choose any command to finish the battle."
      when :xp, :vx, :ace
        return NEED_BATTLE unless in_battle?
        foes = if $game_troop.respond_to?(:enemies) then $game_troop.enemies
               else $game_troop.members end
        foes.each { |e| e.hp = 0 }
        "Enemies defeated. Finish the current turn to win."
      else
        NOPE
      end
    end

    def full_inventory
      case engine
      when :essentials
        return "Bag isn't ready yet. Start or load a game first." unless bag
        if gd?
          GameData::Item.each do |i|
            next if i.name.to_s.empty? || key_item?(i)
            have = (bag_qty(i.id) rescue 0)
            bag_add(i.id, 99 - have) if have < 99
          end
        else
          (1..(PBItems.maxValue rescue 0)).each do |i|
            next if (PBItems.getName(i) rescue "").to_s.empty?
            next if (pbIsKeyItem?(i) rescue false)
            have = (bag.pbQuantity(i) rescue 0)
            bag.pbStoreItem(i, 99 - have) if have < 99
          end
        end
        "Bag filled (key items skipped)"
      when :xp, :vx, :ace
        return "Start or load a game first." unless $game_party
        { 'item' => $data_items, 'weapon' => $data_weapons, 'armor' => $data_armors }.each do |kind, t|
          next unless t
          (1...t.size).each do |i|
            o = t[i]
            next unless o && !o.name.to_s.empty?
            if engine == :xp
              have = $game_party.send("#{kind}_number", i)
              $game_party.send("gain_#{kind}", i, 99 - have) if have < 99
            else
              have = $game_party.item_number(o)
              $game_party.gain_item(o, 99 - have) if have < 99
            end
          end
        end
        "Everything added (99 each)"
      else
        NOPE
      end
    end

    # ---------------------------------------------------------- per frame
    def frame
      return if @errors > 30
      install_hooks
      @tick += 1
      poll if (@tick % 6) == 0
      apply unless @on.empty?
    rescue Exception => e
      @errors += 1
      AnyPlayLog.error("cheats: #{e.class}: #{e.message}") rescue nil
    end

    def poll
      return unless File.exist?(CMD)
      txt = (File.read(CMD) rescue nil)
      File.delete(CMD) rescue nil
      return if txt.nil? || txt.empty?
      txt = txt.dup.force_encoding("UTF-8")
      id, cmd, a, b = txt.chomp.split("\t", 4)
      reply = handle(cmd.to_s, a.to_s, b.to_s)
      File.open(REPLY + ".tmp", "wb") { |f| f.write("#{id}\t#{reply}") }
      File.rename(REPLY + ".tmp", REPLY)
    rescue Exception => e
      AnyPlayLog.error("cheats poll: #{e.class}: #{e.message}") rescue nil
    end

    def apply
      case engine
      when :essentials then apply_ess
      when :xp, :vx, :ace then apply_rpgm
      end
    end

    def apply_ess
      t = trainer
      if on?('hpmp') && t
        pokes.each do |p|
          next if p.egg?
          p.hp = p.totalhp if p.hp != p.totalhp
          (mon_heal_pp(p) rescue nil)
        end
        if @battle
          @battle.battlers.compact.each do |x|
            next if foe?(@battle, x.index) || x.fainted?
            x.hp = x.totalhp if x.hp != x.totalhp
            (x.moves || []).each do |m|
              next unless m
              tp = m.respond_to?(:total_pp) ? m.total_pp : m.totalpp
              m.pp = tp if m.pp < tp
            end rescue nil
          end
        end
      end
      if on?('status') && t
        pokes.each { |p| mon_heal_status(p) if !p.egg? && !no_status?(p.status) }
        if @battle
          @battle.battlers.compact.each do |x|
            next if foe?(@battle, x.index) || x.fainted?
            x.pbCureStatus(false) unless no_status?(x.status)
          end
        end
      end
      if on?('gold') && t
        t.money = 999_999 if t.money < 999_999
      end
      keep_items if on?('items')
      if on?('fastText') && defined?($PokemonSystem) && $PokemonSystem
        @old_textspeed = $PokemonSystem.textspeed if @old_textspeed.nil?
        $PokemonSystem.textspeed = 2 if $PokemonSystem.textspeed != 2
        # The game keeps its own copy of the speed (MessageConfig), so set that too.
        if !@fast_text_set && defined?(MessageConfig) && MessageConfig.respond_to?(:pbSetTextSpeed)
          MessageConfig.pbSetTextSpeed(MessageConfig.pbSettingToTextSpeed(2))
          @fast_text_set = true
        end
      end
      common_map_cheats
    end

    def apply_rpgm
      return unless $game_party
      if on?('hpmp') || on?('tp') || on?('status')
        actors.each do |a|
          if on?('hpmp')
            a.hp = maxhp_of(a)
            mp = maxmp_of(a)
            if a.respond_to?(:mp=) then a.mp = mp
            elsif a.respond_to?(:sp=) then a.sp = mp end
          end
          a.tp = a.max_tp if on?('tp') && a.respond_to?(:tp=) && a.respond_to?(:max_tp)
          if on?('status')
            if a.respond_to?(:clear_states)
              a.clear_states
            elsif a.respond_to?(:remove_state) && a.respond_to?(:states)
              a.states.dup.each { |s| a.remove_state(s.respond_to?(:id) ? s.id : s) }
            end
          end
        end
      end
      if on?('gold')
        g = $game_party.instance_variable_get(:@gold).to_i
        $game_party.instance_variable_set(:@gold, 99_999_999) if g < 99_999_999
      end
      keep_items if on?('items')
      common_map_cheats
    end

    def common_map_cheats
      if on?('noclip') && $game_player
        $game_player.through = true
        @through_forced = true
      end
      if on?('noEncounter')
        $game_system.encounter_disabled = true if $game_system && $game_system.respond_to?(:encounter_disabled=)
        if $game_player && $game_player.instance_variable_defined?(:@encounter_count)
          $game_player.instance_variable_set(:@encounter_count, 99_999)
        end
      end
    end

    # Unlimited items: remember the biggest amount seen of every item and
    # put it back whenever the game takes some away.
    def keep_items
      @item_snap ||= {}
      if ess?
        return unless bag
        have = {}
        bag.pockets.each do |pk|
          next unless pk
          pk.each { |it| have[it[0]] = it[1] if it && it[0] }
        end
        @item_snap.each do |id, q|
          cur = have[id].to_i
          next if cur >= q
          (bag_add(id, q - cur) rescue nil)
        end
        have.each { |id, q| @item_snap[id] = q if q > (@item_snap[id] || 0) }
      else
        h = $game_party.instance_variable_get(:@items)
        return unless h.is_a?(Hash)
        @item_snap.each do |id, q|
          cur = h[id].to_i
          h[id] = q if cur < q
        end
        h.each { |id, q| @item_snap[id] = q if q.to_i > (@item_snap[id] || 0) }
      end
    end

    # ------------------------------------------------------------- hooks
    def install_hooks
      return if @hooked
      return if engine == :unknown
      @hooked = true

      if defined?(Game_Character) && Game_Character.method_defined?(:update)
        Game_Character.class_eval do
          alias_method :__ap_cheat_update, :update
          def update(*args)
            if TyranorCheats.speed_on? && $game_player && self.equal?($game_player)
              m = TyranorCheats.speed.to_f
              m = 1.0 if m <= 0
              # Games store the walking speed in different ways, so scale
              # whichever one this game actually uses, for this frame only.
              if instance_variable_defined?(:@move_speed_real) && @move_speed_real.is_a?(Numeric)
                key = :@move_speed_real; saved = @move_speed_real; scaled = saved * m
              elsif instance_variable_defined?(:@move_time) && @move_time.is_a?(Numeric)
                key = :@move_time; saved = @move_time; scaled = saved / m
              elsif @move_speed.is_a?(Numeric)
                key = :@move_speed; saved = @move_speed; scaled = saved + TyranorCheats.speed_bonus
              end
              if key
                instance_variable_set(key, scaled)
                begin
                  return __ap_cheat_update(*args)
                ensure
                  # keep a value the game set by itself during this frame
                  instance_variable_set(key, saved) if instance_variable_get(key) == scaled
                end
              end
            end
            __ap_cheat_update(*args)
          end
        end
      end

      return unless engine == :essentials

      # No random battles: Essentials v19+ asks encounter_triggered?, v17 asks
      # pbCanEncounter? (which already respects the game's own switch).
      if defined?(PokemonEncounters)
        PokemonEncounters.class_eval do
          if method_defined?(:encounter_triggered?)
            alias_method :__ap_cheat_enc, :encounter_triggered?
            def encounter_triggered?(enc_type, repel_active = false, triggered_by_step = true)
              return false if triggered_by_step && TyranorCheats.on?('noEncounter')
              __ap_cheat_enc(enc_type, repel_active, triggered_by_step)
            end
          end
        end
      end

      # class names differ: PokeBattle_* (older) / Battle::* (v20+)
      move_c   = ess21? ? Battle::Move    : (defined?(PokeBattle_Move) ? PokeBattle_Move : nil)
      battler_c = ess21? ? Battle::Battler : (defined?(PokeBattle_Battler) ? PokeBattle_Battler : nil)
      battle_c = ess21? ? Battle           : (defined?(PokeBattle_Battle) ? PokeBattle_Battle : nil)

      if move_c && move_c.method_defined?(:pbIsCritical?)
        move_c.class_eval do
          alias_method :__ap_cheat_crit, :pbIsCritical?
          def pbIsCritical?(attacker, opponent)
            if TyranorCheats.on?('crit') && attacker && !TyranorCheats.foe?(@battle, attacker.index)
              return true
            end
            __ap_cheat_crit(attacker, opponent)
          end
        end
      end

      if battler_c
        battler_c.class_eval do
          if method_defined?(:pbReduceHP)
            alias_method :__ap_cheat_reduce, :pbReduceHP
            def pbReduceHP(amt, *rest)
              foe = TyranorCheats.foe?(@battle, @index)
              return 0 if !foe && TyranorCheats.on?('invincible')
              amt = self.hp if foe && TyranorCheats.on?('oneHit') && amt > 0
              __ap_cheat_reduce(amt, *rest)
            end
          end
          if method_defined?(:pbReducePP)
            alias_method :__ap_cheat_pp, :pbReducePP
            def pbReducePP(move)
              return true if TyranorCheats.on?('freeSkill') && !TyranorCheats.foe?(@battle, @index)
              __ap_cheat_pp(move)
            end
          end
        end
      end

      if battle_c
        battle_c.class_eval do
          if method_defined?(:pbCommandPhase)
            alias_method :__ap_cheat_cmd, :pbCommandPhase
            def pbCommandPhase(*a)
              TyranorCheats.battle = self
              __ap_cheat_cmd(*a)
            end
          end
          if method_defined?(:pbEndOfBattle)
            alias_method :__ap_cheat_end, :pbEndOfBattle
            def pbEndOfBattle(*a)
              begin
                __ap_cheat_end(*a)
              ensure
                TyranorCheats.battle = nil
              end
            end
          end
          if method_defined?(:pbCanRun?)
            alias_method :__ap_cheat_canrun, :pbCanRun?
            def pbCanRun?(idx)
              if TyranorCheats.on?('escape') && !@opponent && !(@cantescape || @cantRun) && !TyranorCheats.foe?(self, idx)
                return true
              end
              __ap_cheat_canrun(idx)
            end
          end
          if method_defined?(:pbRun)
            alias_method :__ap_cheat_run, :pbRun
            def pbRun(idx, duringBattle = false)
              if TyranorCheats.on?('escape') && !@opponent && !(@cantescape || @cantRun) && !TyranorCheats.foe?(self, idx)
                pbSEPlay("Battle flee") rescue nil
                pbDisplayPaused(_INTL("Got away safely!"))
                @decision = 3
                return 1
              end
              __ap_cheat_run(idx, duringBattle)
            end
          end
        end
      end
    end
  end
end

AnyPlayTouch.native_resize = (Graphics.method(:resize_screen) rescue nil)
# Frame-time watcher: writes AnyPlay_perf_log.txt with one line for every frame
# that took very long, saying whether the time went into the game's own scripts,
# into the engine (drawing / reading files), into Ruby's garbage collector, or
# into the AnyPlay layers.
module AnyPlayPerf
  @n = 0
  @last_end = nil
  @gc_count = 0
  @gc_ms = 0
  @t0 = nil
  @w_frames = 0; @w_time = 0.0; @w_script = 0.0; @w_engine = 0.0
  @w_o50 = 0; @w_o100 = 0; @w_max = 0.0; @w_start = nil; @prev_t1 = nil
  @w_gc = 0; @w_gcms = 0
  @samples = Hash.new(0)
  def self.now
    Process.clock_gettime(Process::CLOCK_MONOTONIC)
  end
  # A tiny sampling profiler: ~25 times a second it looks at which game script
  # line is running. The summary line then names the busiest places.
  def self.start_sampler
    return if @sampler
    main = Thread.current
    @sampler = Thread.new do
      loop do
        sleep 0.04
        begin
          bt = main.backtrace(0, 14)
          l = bt && bt.find { |x| x =~ /\ASection\d|\A\d{3}:/ }
          @samples[AnyPlayLog.label(l)] += 1 if l && @samples.size < 400
        rescue Exception
        end
      end
    end
  rescue Exception
  end
  def self.top_samples
    list = @samples.sort_by { |_, v| -v }
    total = list.inject(0) { |a, (_, v)| a + v }
    @samples = Hash.new(0)
    out = "busiest(#{total} samples): " + list.first(6).map { |k, v| "#{v}x #{k}" }.join(" | ")
    list.first(2).each do |k, _|
      sn = (AnyPlayLog.snippet(k) rescue nil)
      out << "\n" + sn if sn
    end
    out
  end
  def self.note(script_ms, engine_ms, layer_ms, where)
    return if @n > 150
    @n += 1
    gc = GC.stat
    gcn = gc[:count] - @gc_count
    gct = (gc[:time] || 0) - @gc_ms
    @gc_count = gc[:count]
    @gc_ms = gc[:time] || 0
    line = "t=#{((now - @t0)).round(1)}s script=#{script_ms}ms engine=#{engine_ms}ms layers=#{layer_ms}ms gc_runs=#{gcn} gc_ms=#{gct} at=#{where}"
    AnyPlayLog.slow(line)
  rescue Exception
  end
  # Every ~600 frames: one summary line when the game ran below its target speed.
  # Shows how many frames were slow and whether the time went into the game's
  # scripts, into the engine (drawing / waiting), or into garbage collection.
  def self.window(t1, script_ms, engine_ms)
    if @prev_t1
      dt = (t1 - @prev_t1) * 1000.0
      @w_frames += 1
      @w_time += dt
      @w_script += script_ms
      @w_engine += engine_ms
      @w_o50 += 1 if dt > 50
      @w_o100 += 1 if dt > 100
      @w_max = dt if dt > @w_max
    end
    @prev_t1 = t1
    return if @w_frames < 600
    fps = @w_frames * 1000.0 / @w_time
    target = (Graphics.frame_rate rescue 40).to_f
    gc = GC.stat
    gcn = gc[:count] - @w_gc
    gct = (gc[:time] || 0) - @w_gcms
    @w_gc = gc[:count]; @w_gcms = gc[:time] || 0
    if fps < target * 0.9 || @w_o50 > 8
      AnyPlayLog.slow("window: fps=#{fps.round(1)}/#{target.to_i} frames>50ms=#{@w_o50} frames>100ms=#{@w_o100} worst=#{@w_max.to_i}ms script_avg=#{(@w_script / @w_frames).round(1)}ms engine_avg=#{(@w_engine / @w_frames).round(1)}ms gc_runs=#{gcn} gc_ms=#{gct} #{AnyPlayFastFiles.take_stats}#{AnyPlayFastFiles.slowfs_report} #{AnyPlayLog.context}", 5)
      AnyPlayLog.slow(top_samples, 0)
    else
      @samples = Hash.new(0)
    end
    @w_frames = 0; @w_time = 0.0; @w_script = 0.0; @w_engine = 0.0
    @w_o50 = 0; @w_o100 = 0; @w_max = 0.0
  rescue Exception
  end
  # Things that can only be decided once the game's scripts have loaded.
  def self.late_shims
    return if @late
    @late = true
    AnyPlayLog.quiet do
      # Ruby 1.8: StringInput#getc gave the byte NUMBER; newer Ruby gives a 1-letter string
      # (fgetdw / fgetw then fail with "String can't be coerced into Integer").
      if defined?(StringInput) && StringInput.method_defined?(:getc) && !StringInput.method_defined?(:__anyplay_getc)
        StringInput.class_eval do
          alias_method :__anyplay_getc, :getc
          def getc
            c = __anyplay_getc
            c.is_a?(String) ? c.getbyte(0) : c
          end
        end
        # A tileset taller than the GPU's texture limit becomes a "mega surface"; the game's own Ruby
      # tilemap cannot draw it (Operation not supported for mega surfaces). The engine's native
      # tilemap can, so switch to it when that happens.
      if defined?(TilemapLoader) && defined?(SynchronizedTilemap) && TilemapLoader.method_defined?(:tileset=) &&
         !TilemapLoader.method_defined?(:__anyplay_set_tileset)
        TilemapLoader.class_eval do
          alias_method :__anyplay_set_tileset, :tileset=
          def tileset=(v)
            if v && (v.mega? rescue false) && !@tilemap.is_a?(SynchronizedTilemap)
              AnyPlayLog.write("tileset #{v.width}x#{v.height} is too big for the GPU: using the native tilemap")
              (setClass(SynchronizedTilemap) rescue AnyPlayLog.write("native tilemap switch failed"))
            end
            self.__anyplay_set_tileset(v)
          end
        end
      end
      # Ruby 3.x Marshal.load(port) needs getbyte + read on the port object
        unless StringInput.method_defined?(:getbyte)
          StringInput.class_eval { def getbyte; getc; end }
        end
      end
      # Ruby 1.8 had Object#id; games for Essentials v17 and older rely on
      # "nil.id" quietly giving a number instead of an error.
      if defined?(PBMoves) && !(defined?(GameData) && defined?(GameData::Move)) && !nil.respond_to?(:id)
        NilClass.class_eval { def id; 4; end }
      end
    end
  rescue Exception
  end
  def self.run(orig)
    t1 = now
    @t0 ||= t1
    script_ms = @last_end ? ((t1 - @last_end) * 1000).to_i : 0
    where = nil
    where = (caller(2, 3) || []).map { |c| AnyPlayLog.label(c) }.join(" < ") if script_ms > 120
    AnyPlayLog.watch
    start_sampler
    late_shims
    r = orig.call
    t2 = now
    AnyPlayLog.internal += 1
    begin
      AnyPlayTouch.frame
      TyranorCheats.frame
    ensure
      AnyPlayLog.internal -= 1
    end
    t3 = now
    engine_ms = ((t2 - t1) * 1000).to_i
    layer_ms = ((t3 - t2) * 1000).to_i
    note(script_ms, engine_ms, layer_ms, where || "-") if script_ms > 120 || engine_ms > 120 || layer_ms > 30
    window(t1, script_ms, engine_ms)
    @last_end = t3
    r
  end
end
class << Graphics
  alias_method :__anyplay_update, :update
  def update(*a)
    AnyPlayPerf.run(lambda { __anyplay_update(*a) })
  end
end)RB", &preState);
            if (preState) {
#if RAPI_FULL > 187
                rb_set_errinfo(Qnil);
#else
                ruby_errinfo = Qnil;
#endif
            }
        }
    }
#endif
    
    std::string &customScript = conf.customScript;
    if (!customScript.empty())
        runCustomScript(customScript);
    else
        runRMXPScripts(btData);
    
#if RAPI_FULL > 187
    VALUE exc = rb_errinfo();
#else
    VALUE exc = rb_gv_get("$!");
#endif
    if (!NIL_P(exc) && !rb_obj_is_kind_of(exc, rb_eSystemExit)) {
#ifdef __ANDROID__
        // AnyPlay: write the final error (with its call chain) to AnyPlayLog.txt first
        rb_gv_set("$__anyplay_fatal", exc);
        int fatalState = 0;
        rb_eval_string_protect("AnyPlayLog.fatal($__anyplay_fatal) if defined?(AnyPlayLog)", &fatalState);
        if (fatalState) {
#if RAPI_FULL > 187
            rb_set_errinfo(Qnil);
#else
            ruby_errinfo = Qnil;
#endif
        }
#endif
        showExc(exc, btData);
    }
    
    ruby_cleanup(0);
    
    shState->rtData().rqTermAck.set();
}

static void mriBindingTerminate() { rb_raise(rb_eSystemExit, " "); }

static void mriBindingReset() { rb_raise(getRbData()->exc[Reset], " "); }
