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
#include <cstdlib>

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

static bool tynVerboseRuntimeLogs() {
    const char *enabled = std::getenv("TYN_RGSS_VERBOSE_LOG");
    return enabled && enabled[0] == '1' && enabled[1] == '\0';
}

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
RB_METHOD(mkxpNativeBitmapTextTranslation);
RB_METHOD(mkxpReloadPathCache);
RB_METHOD(mkxpAddPath);
RB_METHOD(mkxpRemovePath);
RB_METHOD(mkxpFileExists);
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
	_rb_define_module_function(mod, "native_bitmap_text_translation?", mkxpNativeBitmapTextTranslation);

	_rb_define_module_function(mod, "reload_cache", mkxpReloadPathCache);
	_rb_define_module_function(mod, "mount", mkxpAddPath);
	_rb_define_module_function(mod, "unmount", mkxpRemovePath);
	_rb_define_module_function(mod, "file_exist?", mkxpFileExists);

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
	Debug() << "Ruby message:" << msg;
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

RB_METHOD(mkxpNativeBitmapTextTranslation) {
    RB_UNUSED_PARAM;
    return Qtrue;
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

RB_METHOD(mkxpFileExists) {
    RB_UNUSED_PARAM;

    VALUE path;
    rb_scan_args(argc, argv, "1", &path);
    SafeStringValue(path);

    return shState->fileSystem().exists(RSTRING_PTR(path)) ? Qtrue : Qfalse;
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

static bool runCustomScript(const std::string &filename, const char *phase);
static void logRubyExecutionFailure(const char *phase, const std::string &name, int state);

RB_METHOD(mriRgssMain) {
    RB_UNUSED_PARAM;

    /* Postload scripts run after the game's script pack has been loaded. */
    const Config &conf = shState->rtData().config;
    if (tynVerboseRuntimeLogs())
        Debug() << "[ruby] rgss_main entered; postload script count:" << conf.postloadScripts.size();
    for (std::vector<std::string>::const_iterator i = conf.postloadScripts.begin();
         i != conf.postloadScripts.end(); ++i) {
        if (shState->rtData().rqTerm)
            break;
        if (!runCustomScript(*i, "postload"))
            break;
    }
    if (tynVerboseRuntimeLogs())
        Debug() << "[ruby] entering RGSS game main callback loop";
    
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
    if (tynVerboseRuntimeLogs())
        Debug() << "[ruby] RGSS game main callback loop returned";
    
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

static VALUE currentRubyException() {
#if RAPI_FULL > 187
    return rb_errinfo();
#else
    return rb_gv_get("$!");
#endif
}

static void logRubyExecutionFailure(const char *phase, const std::string &name, int state) {
    VALUE exc = currentRubyException();
    Debug() << "[ruby] execution failed; phase:" << phase << "script:" << name << "protect state:" << state;
    if (NIL_P(exc))
        return;

    VALUE className = rb_class_path(rb_obj_class(exc));
    VALUE message = rb_funcall2(exc, rb_intern("message"), 0, NULL);
    Debug() << "[ruby] exception:" << RSTRING_PTR(className) << ":" << RSTRING_PTR(message);

    VALUE backtrace = rb_funcall2(exc, rb_intern("backtrace"), 0, NULL);
    if (!RB_TYPE_P(backtrace, RUBY_T_ARRAY))
        return;
    long length = RARRAY_LEN(backtrace);
    if (length > 12)
        length = 12;
    for (long i = 0; i < length; ++i) {
        VALUE line = rb_ary_entry(backtrace, i);
        if (RB_TYPE_P(line, RUBY_T_STRING))
            Debug() << "[ruby]   at" << RSTRING_PTR(line);
    }
}

static void clearCurrentRubyException() {
#if RAPI_FULL > 187
    rb_set_errinfo(Qnil);
#else
    ruby_errinfo = Qnil;
#endif
}

static void reportRubyStartupFailure(const char *phase, const std::string &name, int state) {
    logRubyExecutionFailure(phase, name, state);

    VALUE exc = currentRubyException();
    std::string errorClass = "RubyError";
    std::string errorLine = "The embedded Ruby initialization failed.";
    if (!NIL_P(exc)) {
        VALUE className = rb_class_path(rb_obj_class(exc));
        VALUE message = rb_funcall2(exc, rb_intern("message"), 0, NULL);
        if (RB_TYPE_P(className, RUBY_T_STRING))
            errorClass = RSTRING_PTR(className);
        if (RB_TYPE_P(message, RUBY_T_STRING)) {
            std::string detail(RSTRING_PTR(message));
            size_t lineEnd = detail.find('\n');
            errorLine = detail.substr(0, lineEnd);
            size_t syntaxAt = detail.find("syntax error");
            if (syntaxAt != std::string::npos) {
                size_t lineStart = detail.rfind('\n', syntaxAt);
                lineStart = lineStart == std::string::npos ? 0 : lineStart + 1;
                lineEnd = detail.find('\n', syntaxAt);
                errorLine = detail.substr(lineStart, lineEnd == std::string::npos
                                                        ? std::string::npos
                                                        : lineEnd - lineStart);
            }
        }
    }

    std::string dialog = "RGSS startup failed before game scripts ran.\nPhase: ";
    dialog += phase;
    dialog += "\nScript: ";
    dialog += name;
    dialog += "\nError: ";
    dialog += errorClass;
    dialog += ": ";
    dialog += errorLine;
    dialog += "\n\nFull details are in the TYN-RGSS diagnostics export.\nPress OK to return to TYN.";
    Debug() << "[ruby] displaying startup failure dialog; phase:" << phase;
    showMsg(dialog);
}

static void abortRubyStartup(const char *phase, const std::string &name, int state) {
    reportRubyStartupFailure(phase, name, state);
    clearCurrentRubyException();
    ruby_cleanup(0);
    shState->rtData().rqTermAck.set();
}

static bool runCustomScript(const std::string &filename, const char *phase) {
    std::string scriptData;
    if (tynVerboseRuntimeLogs())
        Debug() << "[ruby] begin" << phase << "script:" << filename;
    
    if (!readFileSDL(filename.c_str(), scriptData)) {
        Debug() << "[ruby] could not read" << phase << "script:" << filename;
        showMsg(std::string("Unable to open '") + filename + "'");
        return false;
    }
    
    int state = 0;
    evalString(newStringUTF8(scriptData.c_str(), scriptData.size()),
               newStringUTF8(filename.c_str(), filename.size()), &state);
    if (state) {
        logRubyExecutionFailure(phase, filename, state);
        return false;
    }
    if (tynVerboseRuntimeLogs())
        Debug() << "[ruby] completed" << phase << "script:" << filename << "bytes:" << scriptData.size();
    return true;
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
        Debug() << "[ruby] no game script pack configured";
        showMsg("No game scripts specified (missing Game.ini?)");
        return;
    }
    
    if (tynVerboseRuntimeLogs())
        Debug() << "[ruby] opening game script pack:" << scriptPack;
    if (!shState->fileSystem().exists(scriptPack.c_str())) {
        Debug() << "[ruby] game script pack does not exist:" << scriptPack;
        showMsg("Unable to open '" + scriptPack + "'");
        return;
    }
    
    VALUE scriptArray;
    
    /* We checked if Scripts.rxdata exists, but something might
     * still go wrong */
    try {
        scriptArray = kernelLoadDataInt(scriptPack.c_str(), false, false);
    } catch (const Exception &e) {
        Debug() << "[ruby] failed to deserialize game script pack:" << scriptPack << e.msg;
        showMsg(std::string("Failed to read script data: ") + e.msg);
        return;
    }
    
    if (!RB_TYPE_P(scriptArray, RUBY_T_ARRAY)) {
        Debug() << "[ruby] deserialized game script pack is not an Array:" << scriptPack;
        showMsg("Failed to read script data");
        return;
    }
    
    rb_gv_set("$RGSS_SCRIPTS", scriptArray);
    
    long scriptCount = RARRAY_LEN(scriptArray);
    Debug() << "[ruby] loaded game script pack:" << scriptPack << "entries:" << scriptCount;
    long decodedCount = 0;
    
    std::string decodeBuffer;
    decodeBuffer.resize(0x1000);
    
    for (long i = 0; i < scriptCount; ++i) {
        VALUE script = rb_ary_entry(scriptArray, i);
        
        if (!RB_TYPE_P(script, RUBY_T_ARRAY))
            continue;
        
        VALUE scriptName = rb_ary_entry(script, 1);
        VALUE scriptString = rb_ary_entry(script, 2);
        if (tynVerboseRuntimeLogs())
            Debug() << "[ruby] decompressing game script" << (i + 1) << "/" << scriptCount << ":" << RSTRING_PTR(scriptName);
        
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
            Debug() << "[ruby] script decompression failed; index:" << i << "name:" << RSTRING_PTR(scriptName) << "zlib result:" << result;
            showMsg(buffer);
            
            break;
        }
        
        rb_ary_store(script, 3, rb_utf8_str_new_cstr(decodeBuffer.c_str()));
        ++decodedCount;
    }
    if (tynVerboseRuntimeLogs())
        Debug() << "[ruby] script pack decompression complete:" << decodedCount << "/" << scriptCount;
    
    /* Execute preloaded scripts */
    if (tynVerboseRuntimeLogs())
        Debug() << "[ruby] starting preload scripts; count:" << conf.preloadScripts.size();
    for (std::vector<std::string>::const_iterator i = conf.preloadScripts.begin();
         i != conf.preloadScripts.end(); ++i) {
        if (!runCustomScript(*i, "preload"))
            break;
    }
    
    VALUE exc = currentRubyException();
    if (exc != Qnil) {
        VALUE className = rb_class_path(rb_obj_class(exc));
        VALUE message = rb_funcall2(exc, rb_intern("message"), 0, NULL);
        Debug() << "[ruby] stopping before compatibility prepass; preload exception:"
                << RSTRING_PTR(className) << RSTRING_PTR(message);
        return;
    }
    if (tynVerboseRuntimeLogs())
        Debug() << "[ruby] preload scripts complete";
    
#ifdef __ANDROID__
    // AnyPlay: old RPG Maker games were written for Ruby 1.8. Before running anything,
    // every script is repaired for known Ruby 1.8-only syntax and checked with Ruby's
    // parser. Whatever still can't be parsed is written, ALL at once, to
    // AnyPlay_script_errors.txt in the game folder (so one test run shows every problem).
    VALUE fixedScripts = Qnil;
    {
        static VALUE compatFix = Qnil;
        if (compatFix == Qnil) {
            if (tynVerboseRuntimeLogs())
                Debug() << "[ruby] compatibility fixer initialization begin";
            int compatInitState = 0;
            rb_eval_string_protect(R"RB(
# Ruby 1.8 accepted `return` inside a top-level `proc{ ... }`; Ruby 3 raises
# LocalJumpError. Rewrite `return` (bare or before if/unless) in such procs to
# a catch/throw pair, without changing line numbers.
$__anyplay_fix_proc_return = lambda { |s|
  next s unless s.include?("return") && s =~ /\b(?:proc|Proc\.new)\s*\{/
  # String unary plus is not available in Ruby 1.8; strings are mutable there.
  out = ""
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
)RB", &compatInitState);
            if (compatInitState) {
                reportRubyStartupFailure("Ruby 1.8 compatibility fixer prelude", "embedded compatibility code", compatInitState);
                clearCurrentRubyException();
                return;
            }
            compatFix = rb_eval_string_protect(R"RB(
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
    # now it is text and never equal. __anyplay_byte gives the number for strings. Ruby 1.8 has no lookbehind,
    # so capture and restore the preceding character instead of using a negative lookbehind assertion.
    s = s.gsub(/(^|[^.\w@$\]\)])(line|rstr)\[(\w+)\][ \t]*(==|!=|<=|>=|<|>)[ \t]*(0x[0-9A-Fa-f]+)\b/) { "#{$1}__anyplay_byte(#{$2},#{$3})#{$4}#{$5}" } if s =~ /\]\s*(?:==|!=|<=|>=|<|>)\s*0x/
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
)RB", &compatInitState);
            if (compatInitState) {
                reportRubyStartupFailure("Ruby 1.8 compatibility fixer", "embedded compatibility code", compatInitState);
                clearCurrentRubyException();
                return;
            }
            rb_gc_register_address(&compatFix);
            if (tynVerboseRuntimeLogs())
                Debug() << "[ruby] compatibility fixer initialization complete";
        }
        if (tynVerboseRuntimeLogs())
            Debug() << "[ruby] Ruby 1.8 compatibility prepass begin; scripts:" << scriptCount;
        rb_eval_string("$__anyplay_syntax_errors = []");
        fixedScripts = rb_ary_new();
        for (long i = 0; i < scriptCount; ++i) {
            if (tynVerboseRuntimeLogs() && (i == 0 || (i + 1) % 25 == 0 || i + 1 == scriptCount))
                Debug() << "[ruby] compatibility prepass progress:" << (i + 1) << "/" << scriptCount;
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
        VALUE syntaxErrors = rb_gv_get("$__anyplay_syntax_errors");
        long syntaxErrorCount = RB_TYPE_P(syntaxErrors, RUBY_T_ARRAY) ? RARRAY_LEN(syntaxErrors) : 0;
        if (tynVerboseRuntimeLogs())
            Debug() << "[ruby] Ruby 1.8 compatibility prepass complete; syntax errors:" << syntaxErrorCount;
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
        if (tynVerboseRuntimeLogs())
            Debug() << "[ruby] game script execution pass begin; count:" << scriptCount;
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
            if (tynVerboseRuntimeLogs())
                Debug() << "[ruby] executing game script" << (i + 1) << "/" << scriptCount << ":" << scriptName;
            
            
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
            if (state) {
                std::string failedName = scriptName ? scriptName : buf;
                logRubyExecutionFailure("game", failedName, state);
                break;
            }
            if (tynVerboseRuntimeLogs() && ((i + 1) % 25 == 0 || i + 1 == scriptCount))
                Debug() << "[ruby] game script execution progress:" << (i + 1) << "/" << scriptCount;
        }
        
        VALUE exc = currentRubyException();
        if (rb_obj_class(exc) != getRbData()->exc[Reset]) {
            if (tynVerboseRuntimeLogs() && NIL_P(exc))
                Debug() << "[ruby] game script execution pass complete";
            break;
        }
        
        if (tynVerboseRuntimeLogs())
            Debug() << "[ruby] game script execution requested RGSS reset";
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

    if (std::string(RSTRING_PTR(name)) == "Errno::ENOENT") {
        const char *rtpHint = std::getenv("TYN_RGSS_MISSING_RESOURCE_HINT");
        if (rtpHint && *rtpHint) {
            ms.resize(strlen(ms.c_str()));
            ms += "\n\n";
            ms += rtpHint;
        }
    }
    
    showMsg(ms);
}

static void mriBindingExecute() {
    Config &conf = shState->rtData().config;
	Debug() << "Embedded Ruby startup:" << RAPI_MAJOR << "." << RAPI_MINOR << "." << RAPI_TEENY << "RGSS generation:" << rgssVer;
    
#if RAPI_MAJOR >= 2
    /* Normally only a ruby executable would do a sysinit,
     * but not doing it will lead to crashes due to closed
     * stdio streams on some platforms (eg. Windows) */
    int argc = 0;
    char **argv = 0;
	Debug() << "Ruby sysinit begin";
    ruby_sysinit(&argc, &argv);
	Debug() << "Ruby sysinit complete";
    
	Debug() << "Ruby main-thread stack initialization begin";
    RUBY_INIT_STACK;
	Debug() << "Ruby main-thread stack initialization complete";
	Debug() << "Ruby VM initialization begin";
    ruby_init();
	Debug() << "Ruby VM initialization complete";
    
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
	Debug() << "Ruby options/static extension initialization complete";
    
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
#if RAPI_FULL >= 190
    // Ruby 1.9's embedded startup needs the same process initialization used by
    // its executable before initializing the VM.
    int argc = 0;
    char **argv = 0;
	Debug() << "Ruby sysinit begin";
    ruby_sysinit(&argc, &argv);
	Debug() << "Ruby sysinit complete";
#endif
    // The VM needs its native stack bounds before the first Ruby allocation.
	Debug() << "Ruby main-thread stack initialization begin";
    RUBY_INIT_STACK;
	Debug() << "Ruby main-thread stack initialization complete";
	Debug() << "Ruby VM initialization begin";
    ruby_init();
	Debug() << "Ruby VM initialization complete";
#if RAPI_FULL >= 190
    // Ruby 1.9 initializes static extensions while processing its normal
    // command-line options. Init_ext is private to libruby and cannot be
    // linked from this engine shared library.
	Debug() << "Ruby options/static extension initialization begin";
    std::vector<const char*> rubyArgsC{"mkxp-z", "-e "};
    void *node = ruby_options(rubyArgsC.size(), const_cast<char**>(rubyArgsC.data()));
    int state = 0;
    bool valid = ruby_executable_node(node, &state);
    if (valid)
        state = ruby_exec_node(node);
    if (state || !valid) {
		Debug() << "Ruby options/static extension initialization failed:" << state;
        showMsg("An error occurred while initializing the Ruby 1.9 runtime.");
        ruby_cleanup(state);
        shState->rtData().rqTermAck.set();
        return;
    }
	Debug() << "Ruby options/static extension initialization complete";
#endif
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
	Debug() << "Embedded Ruby initialized:" << RAPI_MAJOR << "." << RAPI_MINOR << "." << RAPI_TEENY;
    
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
    Debug() << "[ruby] RGSS binding registration complete";
    
#ifdef __ANDROID__
    // AnyPlay: RPG Maker XP/VX/Ace ship with Zlib (and a few other standard
    // libraries) already loaded, so games use them without "require".
    // Load them up front; if one is missing, just carry on.
    {
        if (tynVerboseRuntimeLogs())
            Debug() << "[ruby] Android Ruby standard-library compatibility bootstrap begin";
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
  alias_method :exists?, :exist? if respond_to?(:exist?) && !respond_to?(:exists?)
end
class << Dir
  alias_method :exists?, :exist? if respond_to?(:exist?) && !respond_to?(:exists?)
end
Fixnum = Integer unless defined?(Fixnum)
Bignum = Integer unless defined?(Bignum)
)RB", &reqState);
        if (reqState) {
            abortRubyStartup("Android standard-library bootstrap", "embedded compatibility code", reqState);
            return;
        } else if (tynVerboseRuntimeLogs())
            Debug() << "[ruby] Android Ruby standard-library compatibility bootstrap complete";

        // AnyPlay: safe dispose, full-screen fit, tap-to-walk (see anyplay_prelude.rb)
        {
            if (tynVerboseRuntimeLogs())
                Debug() << "[ruby] Android RGSS compatibility prelude begin";
            int preState = 0;
            rb_eval_string_protect(R"RB(# AnyPlay RGSS plugin: runs before the game's own scripts (Android only).
# 1) Plane/Sprite/... #dispose no longer crashes for subclasses that skip `super` in initialize
# 2) game screen is resized to the game's own size (fills the screen)
# 3) touch: tap = walk there / talk to NPC / confirm
module AnyPlayCompat
  # define_singleton_method was added after Ruby 1.8. Define methods on the
  # singleton class so the same prelude works with all three embedded Rubies.
  def self.define_singleton_method_compat(object, name, &block)
    singleton = class << object; self; end
    singleton.send(:define_method, name, &block)
  end

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
    def sample; empty? ? nil : self[rand(length)]; end unless method_defined?(:sample)
    def choice; sample; end unless method_defined?(:choice)
  end
  class Hash
    def index(v); key(v); end unless method_defined?(:index)
  end
  module Enumerable
    def enum_with_index(*a, &b); each_with_index(*a, &b); end unless method_defined?(:enum_with_index)
    def max_by
      first = true
      best_item = nil
      best_key = nil
      each do |item|
        key = yield(item)
        if first || (key <=> best_key) > 0
          first = false
          best_item = item
          best_key = key
        end
      end
      best_item
    end unless method_defined?(:max_by)
    def min_by
      first = true
      best_item = nil
      best_key = nil
      each do |item|
        key = yield(item)
        if first || (key <=> best_key) < 0
          first = false
          best_item = item
          best_key = key
        end
      end
      best_item
    end unless method_defined?(:min_by)
  end
  class String
    def bytesize; length; end unless method_defined?(:bytesize)
    def getbyte(index); self[index]; end unless method_defined?(:getbyte)
    def ascii_only?
      each_byte { |byte| return false if byte > 127 }
      true
    end unless method_defined?(:ascii_only?)
    def start_with?(*prefixes)
      prefixes.any? { |prefix| self[0, prefix.length] == prefix }
    end unless method_defined?(:start_with?)
    def end_with?(*suffixes)
      suffixes.any? { |suffix| self[-suffix.length, suffix.length] == suffix }
    end unless method_defined?(:end_with?)
    def match?(pattern); !!(self =~ pattern); end unless method_defined?(:match?)
  end
  class << Dir
    def children(path)
      entries(path).reject { |entry| entry == "." || entry == ".." }
    end unless respond_to?(:children)
  end
  unless Process.respond_to?(:clock_gettime)
    Process.const_set(:CLOCK_MONOTONIC, 1) unless Process.const_defined?(:CLOCK_MONOTONIC)
    class << Process
      def clock_gettime(clock); Time.now.to_f; end
    end
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
  AnyPlayCompat.define_singleton_method_compat(Audio, :bgm_position) { 0.0 } unless Audio.respond_to?(:bgm_position)
  AnyPlayCompat.define_singleton_method_compat(Audio, :bgs_position) { 0.0 } unless Audio.respond_to?(:bgs_position)
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
      AnyPlayCompat.define_singleton_method_compat(mod, meth) do |path|
        if path.is_a?(String) && path =~ AnyPlayFastFiles::RES && ![:directory?].include?(meth) && meth != :exists? && !(mod == Dir)
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
      if RUBY_VERSION >= "2.0"
        eval(<<'TYN_RUBY')
          AnyPlayCompat.define_singleton_method_compat(mod, meth) do |*a, **kw, &b|
            AnyPlayFastFiles.forget
            kw.empty? ? orig.call(*a, &b) : orig.call(*a, **kw, &b)
          end
TYN_RUBY
      else
        AnyPlayCompat.define_singleton_method_compat(mod, meth) do |*a, &b|
          AnyPlayFastFiles.forget
          orig.call(*a, &b)
        end
      end
    end
    fopen = File.method(:open)
    if RUBY_VERSION >= "2.0"
      eval(<<'TYN_RUBY')
        AnyPlayCompat.define_singleton_method_compat(File, :open) do |*a, **kw, &b|
          m = a[1]
          m = kw[:mode] if m.nil?
          AnyPlayFastFiles.forget if m.is_a?(Integer) || (m.is_a?(String) && m =~ /[wa+]/)
          kw.empty? ? fopen.call(*a, &b) : fopen.call(*a, **kw, &b)
        end
TYN_RUBY
    else
      AnyPlayCompat.define_singleton_method_compat(File, :open) do |*a, &b|
        m = a[1]
        AnyPlayFastFiles.forget if m.is_a?(Integer) || (m.is_a?(String) && m =~ /[wa+]/)
        fopen.call(*a, &b)
      end
    end
  rescue Exception
  end

  [[File, :exist?], [File, :file?], [FileTest, :exist?], [FileTest, :file?]].each do |mod, meth|
    orig = mod.method(meth)
    tag = "#{mod.name}.#{meth}:"
    AnyPlayCompat.define_singleton_method_compat(mod, meth) do |path|
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

# ---- AnyPlayLog: one file, "AnyPlayLog.txt" in the private TYN session ---------
# It records runtime start and scene/map changes, scripts that could not be parsed,
# Ruby errors (including ones the game catches), fatal call chains, and context notes.
module AnyPlayLog
  VERSION   = "0.1.40"
  FILE      = begin
    dir = ENV["TYN_RGSS_DIAGNOSTICS_DIR"]
    dir && !dir.empty? ? File.join(dir, "AnyPlayLog.txt") : "AnyPlayLog.txt"
  rescue Exception
    "AnyPlayLog.txt"
  end
  MAX_BYTES = 250_000
  CAPS      = { :error => 200, :note => 400 }
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
  @last_scene_map = nil
  @t0 = Process.clock_gettime(Process::CLOCK_MONOTONIC)
  # TYN gives every launch its own private file; keep the legacy fallback clean too.
  (File.delete(FILE) if File.exist?(FILE)) rescue nil
  IGNORE = /\A(SystemExit|Interrupt|StopIteration|SignalException|Reset|RGSSReset)\z/

  class << self
    attr_accessor :internal

    def stamp
      now = Time.now
      "%s.%03d +%ds" % [now.strftime("%Y-%m-%d %H:%M:%S"), now.usec / 1000,
        (Process.clock_gettime(Process::CLOCK_MONOTONIC) - @t0).to_i]
    end

    # run code without our own error watcher noticing exceptions it rescues
    def quiet
      @internal += 1
      yield
    ensure
      @internal -= 1
    end

    def clean(x, n = 400)
      text = x.to_s.dup
      text.force_encoding("UTF-8") if text.respond_to?(:force_encoding)
      text = text.scrub("?") if text.respond_to?(:scrub)
      text[0, n]
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
      raw("==== AnyPlay runtime log (plugin #{VERSION}) started=#{Time.now.strftime('%Y-%m-%d %H:%M:%S')} ====")
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
      game_map = $game_map
      map = game_map && game_map.respond_to?(:map_id) ? game_map.map_id : "-"
      "scene=#{scene} map=#{map} frame=#{(Graphics.frame_count rescue '?')} resize=#{($ResizeFactor rescue '?')}"
    end

    # Keep a compact execution timeline without writing once per frame.
    def runtime_checkpoint
      scene = (defined?(SceneManager) && SceneManager.respond_to?(:scene) ? SceneManager.scene : $scene).class rescue "?"
      game_map = $game_map
      map = game_map && game_map.respond_to?(:map_id) ? game_map.map_id : "-"
      current = "scene=#{scene} map=#{map}"
      return if current == @last_scene_map
      @last_scene_map = current
      write("runtime checkpoint: #{current} frame=#{(Graphics.frame_count rescue '?')}")
    rescue Exception
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
           RangeError, IndexError, NotImplementedError, LocalJumpError, SyntaxError]
    BUG << Object.const_get(:EncodingError) if Object.const_defined?(:EncodingError)

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
      start
      write("Ruby update loop entered; #{context}")
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

if ENV["TYN_RGSS_VERBOSE_LOG"] == "1"
  begin
    AnyPlayLog.start
    AnyPlayLog.write("Verbose startup diagnostics enabled; embedded Ruby prelude completed")
  rescue Exception
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
      unless n.is_a?(Integer) && n > 0 && n <= 300
        n = (w.instance_variable_get(:@item_max) rescue nil)
      end
      unless n.is_a?(Integer) && n > 0 && n <= 300
        commands = (w.instance_variable_get(:@commands) rescue nil)
        n = commands.length if commands.respond_to?(:length)
      end
      unless n.is_a?(Integer) && n > 0 && n <= 300
        list = (w.instance_variable_get(:@list) rescue nil)
        n = list.length if list.respond_to?(:length)
      end
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
      { :target => hit[0], :vertical => panel_vertical?(panels), :steps => 0, :stall => 0, :last => nil }
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
            AnyPlayLog.write("  confirm consumed by Input.trigger? frame=#{(Graphics.frame_count rescue '?')} pending=#{AnyPlayTouch.press_c}")
            AnyPlayTouch.press_c = 0
            AnyPlayLog.write("  confirm sent" + AnyPlayMenu.apply_pin.to_s)
            return true
          end
          return true if AnyPlayTouch.next_key(b)
          __ap_trigger(b)
        end
        if Input.respond_to?(:press?)
          alias_method :__ap_press, :press?
          def press?(b)
            if AnyPlayTouch.press_c > 0 && (b == Input::C)
              AnyPlayLog.write("  confirm observed by Input.press? frame=#{(Graphics.frame_count rescue '?')} pending=#{AnyPlayTouch.press_c}")
              return true
            end
            __ap_press(b)
          end
        end
        if Input.respond_to?(:repeat?)
          alias_method :__ap_repeat, :repeat?
          def repeat?(b)
            if AnyPlayTouch.press_c > 0 && (b == Input::C)
              AnyPlayLog.write("  confirm consumed by Input.repeat? frame=#{(Graphics.frame_count rescue '?')} pending=#{AnyPlayTouch.press_c}")
              AnyPlayTouch.press_c = 0
              return true
            end
            return true if AnyPlayTouch.next_key(b)
            __ap_repeat(b)
          end
        end
      end
      AnyPlayLog.write("  touch input hook installed (dir4/press?/trigger?/repeat?)")
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
        AnyPlayLog.write("  tap menu result=#{r.inspect}")
        if r.is_a?(Hash)
          @pan = r            # walk the highlight to that panel, then confirm (see tick)
        elsif r != :ignore
          @press_c = 4        # dialogs, text, title: tap = confirm
          AnyPlayLog.write("  confirm queued from tap; press_c=#{@press_c}")
        else
          AnyPlayLog.write("  tap ignored by menu handler")
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
        AnyPlayLog.write("  queued touch confirm expired before Input consumed it frame=#{(Graphics.frame_count rescue '?')}") if @press_c == 0
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

# The cheat subsystem is evaluated as an isolated optional layer. A syntax or
# initialization error in cheats must never abort the core Android prelude and
# prevent unrelated games from starting.
begin
  eval(<<'TYRANOR_CHEATS_LAYER')
# ---- AnyPlay cheats (RPG Maker XP / VX / Ace and Pokemon Essentials) --------
# The Android overlay writes one command per file into the app cache folder,
# this module reads it (every few frames), runs it, and writes a one line
# answer back.  Checkbox cheats are just flags that are applied every frame.
module TyranorCheats
  DIR   = "/data/data/com.tyranor.runtime.rgss/cache"
  CMD   = DIR + "/tyranor_cheat_cmd.txt"
  REPLY = DIR + "/tyranor_cheat_reply.txt"
  STATE = DIR + "/tyranor_cheat_state.txt"
  WINDOW_STATE = DIR + "/tyranor_cheat_window_state.txt"
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
  @points_snap = nil
  @coins_snap = nil
  @old_textspeed = nil
  @through_forced = false
  @old_player_through = nil
  @encounter_saved = false
  @old_encounter_disabled = nil
  @old_encounter_count = nil
  @battle = nil
  @xp_critical_attacker = nil
  @free_skill_cost_actor = nil

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
    def ess_move_class
      return nil unless ess?
      return Battle::Move if ess21? && defined?(Battle::Move)
      defined?(PokeBattle_Move) ? PokeBattle_Move : nil
    rescue Exception
      nil
    end
    def ess_battler_class
      return nil unless ess?
      return Battle::Battler if ess21? && defined?(Battle::Battler)
      defined?(PokeBattle_Battler) ? PokeBattle_Battler : nil
    rescue Exception
      nil
    end
    def ess_battle_class
      return nil unless ess?
      return Battle if ess21? && defined?(Battle)
      defined?(PokeBattle_Battle) ? PokeBattle_Battle : nil
    rescue Exception
      nil
    end
    def class_method?(klass, name)
      klass && (klass.method_defined?(name) || klass.private_method_defined?(name) ||
                klass.protected_method_defined?(name))
    rescue Exception
      false
    end

    # Ruby 3 separates keyword arguments from a positional hash. Mark the
    # compatibility wrappers that forward *args so keywords reach the game's
    # original method; older Ruby versions simply skip this.
    def preserve_keyword_forwarding(klass, method_name)
      return unless klass && klass.respond_to?(:ruby2_keywords, true)
      klass.send(:ruby2_keywords, method_name)
    rescue Exception
      nil
    end

    def fast_text_supported?
      return true if ess? && defined?($PokemonSystem) && $PokemonSystem && $PokemonSystem.respond_to?(:textspeed=)
      return false unless defined?(Window_Message)
      return true if class_method?(Window_Message, :update_message)
      engine == :ace && class_method?(Window_Message, :update_show_fast) &&
        class_method?(Window_Message, :wait_for_one_character)
    rescue Exception
      false
    end

    def message_skip_supported?
      if ess? && defined?(Window_AdvancedTextPokemon)
        klass = Window_AdvancedTextPokemon
        return class_method?(klass, :update) && class_method?(klass, :textspeed=) &&
          class_method?(klass, :waitcount=) && class_method?(klass, :busy?) &&
          class_method?(klass, :pausing?) && class_method?(klass, :resume)
      end
      return false unless defined?(Window_Message)
      case engine
      when :xp
        class_method?(Window_Message, :update) && class_method?(Window_Message, :terminate_message)
      when :vx
        class_method?(Window_Message, :update_message) && class_method?(Window_Message, :input_pause) &&
          class_method?(Window_Message, :terminate_message)
      when :ace
        class_method?(Window_Message, :input_pause) && class_method?(Window_Message, :update_show_fast) &&
          class_method?(Window_Message, :wait_for_one_character) && class_method?(Window_Message, :wait)
      else
        class_method?(Window_Message, :update_message) && class_method?(Window_Message, :update_input)
      end
    rescue Exception
      false
    end

    def message_choice_active?
      if defined?($game_temp) && $game_temp && $game_temp.respond_to?(:choice_max)
        return true if $game_temp.choice_max.to_i > 0
      end
      if defined?($game_message) && $game_message && $game_message.respond_to?(:choice?)
        return true if $game_message.choice?
      end
      false
    rescue Exception
      false
    end
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
    def write_state
      File.open(STATE + ".tmp", "wb") do |file|
        file.write("#{@speed}\t#{@on.keys.join(',')}")
      end
      File.rename(STATE + ".tmp", STATE)
    rescue Exception
    end
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
    def coin_resource
      return nil unless ess? && defined?($PokemonGlobal) && $PokemonGlobal
      return nil unless $PokemonGlobal.respond_to?(:coins) && $PokemonGlobal.respond_to?(:coins=)
      return nil unless $PokemonGlobal.coins.is_a?(Numeric)
      $PokemonGlobal
    rescue Exception
      nil
    end
    def coins_supported?; !coin_resource.nil?; end
    def maintain_coins
      return unless on?('coins')
      resource = coin_resource
      return unless resource
      if @coins_snap.nil?
        @coins_snap = resource.coins
      elsif resource.coins > @coins_snap
        @coins_snap = resource.coins
      elsif resource.coins < @coins_snap
        resource.coins = @coins_snap
      end
    rescue Exception
    end
    def set_coins(value)
      resource = coin_resource
      return nil unless resource
      maximum = if defined?(MAXCOINS) then MAXCOINS.to_i else 999_999 end
      target = [[value.to_i, 0].max, [maximum, 0].max].min
      resource.coins = target
      @coins_snap = target if on?('coins')
      target
    rescue Exception
      nil
    end
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

    def party_battler?(battler)
      actors.include?(battler)
    rescue Exception
      false
    end

    def enemy_battler?(battler)
      return false unless $game_troop
      list = $game_troop.respond_to?(:enemies) ? $game_troop.enemies :
             ($game_troop.respond_to?(:members) ? $game_troop.members : [])
      list.include?(battler)
    rescue Exception
      false
    end

    def xp_critical_attacker
      @xp_critical_attacker
    end

    def with_xp_critical_attacker(attacker)
      return yield unless engine == :xp && on?('crit') && party_battler?(attacker)
      previous = @xp_critical_attacker
      @xp_critical_attacker = attacker
      begin
        yield
      ensure
        @xp_critical_attacker = previous
      end
    end

    # XP reads RPG::Skill#sp_cost while checking and consuming a skill. Keep
    # the zero-cost override scoped to the party member whose action is being
    # processed, so enemy skills and unrelated script code retain their costs.
    def with_free_skill_cost(actor)
      return yield unless on?('freeSkill') && party_battler?(actor)
      previous = @free_skill_cost_actor
      @free_skill_cost_actor = actor
      begin
        yield
      ensure
        @free_skill_cost_actor = previous
      end
    end

    def free_skill_cost_scope?
      on?('freeSkill') && party_battler?(@free_skill_cost_actor)
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
      when 'system_switch' then set_system_switch(a, b)
      when 'system_variable' then set_system_variable(a, b)
      when 'quantity' then set_quantity(a, b)
      when 'pokemon_value' then set_pokemon_value(a, b)
      when 'pokemon_recover' then recover_pokemon(a)
      when 'pokemon_move' then set_pokemon_move(a, b)
      when 'pokemon_status' then set_pokemon_status(a, b)
      when 'pokemon_item' then set_pokemon_item(a, b)
      when 'actor_value' then set_actor_value(a, b)
      when 'actor_recover' then recover_actor(a)
      when 'actor_collection' then set_actor_collection(a, b)
      when 'actor_equip' then set_actor_equip(a, b)
      when 'event_switch' then set_event_switch(a, b)
      when 'battle_enemy_hp' then set_enemy_hp(a, b)
      when 'save_slot' then select_save_slot(a)
      when 'delete_save' then delete_save_slot(a)
      when 'give'    then give(a, b)
      when 'run'     then run_action(a, b)
      when 'list'    then list_data(a, b)
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
      @points_snap = nil
      @coins_snap = nil
      write_state
      ""
    end

    def toggle(name, enable)
      unless toggle_supported?(name)
        @on.delete(name)
        write_state
        return "#{NOPE}"
      end
      if enable
        save_encounter_state if name == 'noEncounter'
        @on[name] = true
        @item_snap = nil if name == 'items'
        @points_snap = {} if name == 'points'
      else
        @on.delete(name)
        toggle_off(name)
      end
      write_state
      ""
    end

    def toggle_supported?(name)
      case name
      when 'tp'                         then engine == :ace
      when 'points'                     then custom_points_supported?
      when 'coins'                      then coins_supported?
      when 'fastText'                   then fast_text_supported?
      when 'msgSkip'                    then message_skip_supported?
      when 'eventSpeed'                 then defined?(Game_Interpreter) && class_method?(Game_Interpreter, :update)
      when 'oneHit', 'invincible'
        ess? ? (ess_battler_class && class_method?(ess_battler_class, :pbReduceHP)) :
          (rpgm? && defined?(Game_Battler) && class_method?(Game_Battler, :hp=))
      when 'crit'
        if ess?
          ess_move_class && (class_method?(ess_move_class, :pbCritRate?) || class_method?(ess_move_class, :pbIsCritical?))
        elsif engine == :ace
          defined?(Game_Battler) && class_method?(Game_Battler, :item_cri) &&
            class_method?(Game_Battler, :make_damage_value)
        elsif engine == :xp
          defined?(Game_Battler) && class_method?(Game_Battler, :attack_effect) &&
            class_method?(Game_Battler, :hp=)
        elsif engine == :vx
          defined?(Game_Battler) && class_method?(Game_Battler, :make_attack_damage_value)
        else
          false
        end
      when 'escape'
        if ess?
          ess_battle_class && (class_method?(ess_battle_class, :pbCanRun?) || class_method?(ess_battle_class, :pbRun))
        elsif engine == :xp
          (defined?(BattleManager) && BattleManager.respond_to?(:process_escape)) ||
            (defined?(Scene_Battle) && class_method?(Scene_Battle, :process_escape)) ||
            (defined?(Scene_Battle) && class_method?(Scene_Battle, :update_phase2_escape) && class_method?(Scene_Battle, :battle_end))
        elsif engine == :ace
          defined?(BattleManager) && BattleManager.respond_to?(:process_escape)
        elsif engine == :vx
          (defined?(BattleManager) && BattleManager.respond_to?(:process_escape)) ||
            (defined?(Scene_Battle) && class_method?(Scene_Battle, :process_escape))
        else
          false
        end
      when 'freeSkill'
        if ess?
          ess_battler_class && class_method?(ess_battler_class, :pbReducePP)
        elsif engine == :ace
          defined?(Game_BattlerBase) && class_method?(Game_BattlerBase, :skill_mp_cost)
        elsif engine == :vx
          defined?(Game_Battler) && class_method?(Game_Battler, :calc_mp_cost)
        elsif engine == :xp
          defined?(RPG::Skill) && class_method?(RPG::Skill, :sp_cost) &&
            defined?(Game_Battler) && class_method?(Game_Battler, :skill_can_use?) &&
            defined?(Scene_Battle) && class_method?(Scene_Battle, :make_skill_action_result)
        else
          false
        end
      when 'hpmp', 'gold', 'status', 'items', 'noclip', 'noEncounter', 'speed'
        ess? || rpgm?
      else false
      end
    end

    # Custom game resources have no engine-wide API. Treat a public numeric
    # Game_Actor#points / #points= pair as a conventional custom points field;
    # unrelated private ivars are deliberately not guessed or modified.
    def custom_points_supported?
      return false unless rpgm? && defined?(Game_Actor)
      current_actors = actors
      return false if current_actors.empty?
      current_actors.all? do |actor|
        actor.respond_to?(:points) && actor.respond_to?(:points=) && actor.points.is_a?(Numeric)
      end
    rescue Exception
      false
    end

    def custom_points_key(actor)
      actor.object_id.to_s
    rescue Exception
      actor.to_s
    end

    def maintain_custom_points(actor)
      return unless on?('points') && custom_points_supported?
      return unless actor && actor.respond_to?(:points) && actor.respond_to?(:points=)
      current = actor.points
      return unless current.is_a?(Numeric)
      @points_snap ||= {}
      key = custom_points_key(actor)
      if @points_snap.has_key?(key)
        if current > @points_snap[key]
          @points_snap[key] = current
        elsif current < @points_snap[key]
          actor.points = @points_snap[key]
        end
      else
        @points_snap[key] = current
      end
    rescue Exception
    end

    def toggle_off(name)
      case name
      when 'noclip'
        if $game_player && @through_forced
          set_player_through($game_player, @old_player_through.nil? ? false : @old_player_through)
        end
        @through_forced = false
        @old_player_through = nil
      when 'noEncounter'
        if @encounter_saved
          if $game_system && $game_system.respond_to?(:encounter_disabled=)
            $game_system.encounter_disabled = @old_encounter_disabled
          end
          if $game_player && !@old_encounter_count.nil? && $game_player.instance_variable_defined?(:@encounter_count)
            $game_player.instance_variable_set(:@encounter_count, @old_encounter_count)
          end
        end
        @encounter_saved = false
        @old_encounter_disabled = nil
        @old_encounter_count = nil
      when 'fastText'
        if !@old_textspeed.nil? && defined?($PokemonSystem) && $PokemonSystem
          $PokemonSystem.textspeed = @old_textspeed
        end
        if @fast_text_set && !@old_textspeed.nil? && defined?(MessageConfig) && MessageConfig.respond_to?(:pbSetTextSpeed)
          (MessageConfig.pbSetTextSpeed(MessageConfig.pbSettingToTextSpeed(@old_textspeed)) rescue nil)
        end
        @fast_text_set = false
        @old_textspeed = nil
      when 'items'
        @item_snap = nil
      when 'points'
        @points_snap = nil
      when 'coins'
        @coins_snap = nil
      end
    end

    def player_through(player)
      return player.through if player.respond_to?(:through)
      return player.instance_variable_get(:@through) if player.instance_variable_defined?(:@through)
      nil
    rescue Exception
      nil
    end

    def set_player_through(player, value)
      return false unless player
      if player.respond_to?(:through=)
        player.through = value
      elsif player.respond_to?(:through) || player.instance_variable_defined?(:@through)
        # Some XP games keep the standard @through state but remove the writer.
        # Updating the backing ivar preserves Game_Character#passable? behavior.
        player.instance_variable_set(:@through, value)
        unless @through_writer_fallback_logged
          AnyPlayLog.write("noclip: #{player.class} has no through=; updated @through directly")
          @through_writer_fallback_logged = true
        end
      else
        unless @noclip_unsupported_logged
          AnyPlayLog.error("noclip unavailable: #{player.class} exposes neither through= nor @through")
          @noclip_unsupported_logged = true
        end
        return false
      end
      true
    rescue Exception => e
      AnyPlayLog.error("noclip write failed: #{e.class}: #{e.message}") rescue nil
      false
    end

    def save_encounter_state
      return if @encounter_saved
      @old_encounter_disabled = if $game_system && $game_system.respond_to?(:encounter_disabled)
                                 $game_system.encounter_disabled
                               else
                                 nil
                               end
      @old_encounter_count = if $game_player && $game_player.instance_variable_defined?(:@encounter_count)
                               $game_player.instance_variable_get(:@encounter_count)
                             else
                               nil
                             end
      @encounter_saved = true
    rescue Exception
      @encounter_saved = true
    end

    # ------------------------------------------------------------- values
    def set_value(name, v)
      if name == 'speed'
        s = v.to_f
        return "Enter a number above 0." unless s > 0
        @speed = [[s, 0.25].max, 8.0].min
        @on['speed'] = true
        write_state
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
        t.money = [[n, 0].max, ess_money_limit].min
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
      when 'coins'
        value = set_coins(n)
        return NOPE if value.nil?
        "Coins set to #{value}"
      else
        NOPE
      end
    end

    def ess_money_limit
      maximum = if defined?(Settings::MAX_MONEY)
                  Settings::MAX_MONEY.to_i
                else
                  999_999
                end
      [maximum, 0].max
    rescue Exception
      999_999
    end

    def rpgm_value(name, n)
      return "Start or load a game first." unless $game_party
      case name
      when 'gold'
        value = set_rpgm_gold(n)
        "Gold set to #{value}"
      when 'level'
        ac = actors
        return "Your party is empty." if ac.empty?
        ac.each do |a|
          result = set_actor_level(a, n)
          return result if result.is_a?(String)
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
      when 'points'
        return NOPE unless custom_points_supported?
        ac = actors
        return "Your party is empty." if ac.empty?
        ac.each do |actor|
          return NOPE unless set_custom_points(actor, n)
        end
        "Party points set to #{n}"
      when 'atk', 'def', 'mat', 'mdf', 'agi', 'luk', 'dex'
        set_stat(name, n)
      else
        NOPE
      end
    end

    def rpgm_gold_limit
      return $game_party.max_gold.to_i if $game_party && $game_party.respond_to?(:max_gold)
      engine == :xp ? 999_999 : 99_999_999
    rescue Exception
      engine == :xp ? 999_999 : 99_999_999
    end

    def rpgm_gold_value
      return $game_party.gold.to_i if $game_party && $game_party.respond_to?(:gold)
      ($game_party.instance_variable_get(:@gold).to_i rescue 0)
    rescue Exception
      0
    end

    def set_rpgm_gold(value)
      return 0 unless $game_party
      maximum = [rpgm_gold_limit, 0].max
      target = [[value.to_i, 0].max, maximum].min
      current = rpgm_gold_value
      if $game_party.respond_to?(:gain_gold)
        $game_party.gain_gold(target - current)
      elsif $game_party.respond_to?(:gold=)
        $game_party.gold = target
      else
        $game_party.instance_variable_set(:@gold, target)
      end
      rpgm_gold_value
    end

    def set_stat(name, n)
      ac = actors
      return "Your party is empty." if ac.empty?
      done = false
      ac.each do |a|
        if engine == :ace
          id = { 'atk' => 2, 'def' => 3, 'mat' => 4, 'mdf' => 5, 'agi' => 6, 'luk' => 7 }[name]
          next unless id
          a.add_param(id, n - a.param(id))
          done = true
        elsif engine == :vx
          k = { 'atk' => :atk, 'def' => :def, 'mat' => :spi, 'agi' => :agi }[name]
          next unless k
          cur = a.send(k)
          a.send("#{k}_plus=", a.send("#{k}_plus") + (n - cur))
          done = true
        else
          k = { 'atk' => :str, 'def' => :pdef, 'mat' => :int, 'mdf' => :mdef, 'agi' => :agi, 'dex' => :dex }[name]
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
    def list_data(kind = "", arg = "")
      return list_system if kind == 'system'
      return list_gold if kind == 'gold'
      return list_maps if kind == 'maps'
      return list_pokemon if kind == 'pokemon'
      return list_pokemon_moves(arg) if kind == 'pokemon_moves'
      return list_pokemon_move_candidates if kind == 'pokemon_move_candidates'
      return list_pokemon_statuses(arg) if kind == 'pokemon_statuses'
      return list_pokemon_stats(arg) if kind == 'pokemon_stats'
      return list_actors if kind == 'actors'
      return list_events if kind == 'events'
      return list_minimap if kind == 'minimap'
      return list_battle if kind == 'battle'
      return list_battle_actors if kind == 'battle_actors'
      return list_save_slots if kind == 'saves'
      return list_actor_collection(arg, 'skills') if kind == 'actor_skills'
      return list_actor_collection(arg, 'states') if kind == 'actor_states'
      return list_actor_equips(arg) if kind == 'actor_equips'
      return list_equip_candidates(arg) if kind == 'equip_candidates'
      return list_capabilities if kind == 'capabilities'
      list_items(kind)
    end

    def wire_text(value)
      value.to_s.gsub("\t", " ").gsub("\r", " ").gsub("\n", " ")
    rescue Exception
      ""
    end

    def system_database
      if ess? && defined?($cache) && $cache && $cache.respond_to?(:RXsystem)
        value = ($cache.RXsystem rescue nil)
        return value if value
      end
      return $data_system if defined?($data_system) && $data_system
      nil
    rescue Exception
      nil
    end

    def system_names(database, name)
      return nil unless database
      value = database.respond_to?(name) ? (database.send(name) rescue nil) : nil
      value ||= (database.instance_variable_get("@#{name}") rescue nil)
      value.is_a?(Array) ? value : nil
    rescue Exception
      nil
    end

    def json_module
      # mkxp-z exposes this parser from its native JSON binding in every
      # bundled Ruby runtime. Prefer it so legacy Ruby builds do not depend on
      # an optional JSON gem being present in the game's load path.
      return HTTPLite::JSON if defined?(HTTPLite::JSON) && HTTPLite::JSON.respond_to?(:parse)
      return JSON if defined?(JSON)
      require 'json'
      defined?(JSON) ? JSON : nil
    rescue Exception
      nil
    end

    def json_parse_value(parser, text)
      parser.parse(text)
    end

    def json_generate_value(parser, value)
      return parser.stringify(value) if parser.respond_to?(:stringify)
      return parser.generate(value) if parser.respond_to?(:generate)
      nil
    rescue Exception
      nil
    end

    def list_capabilities
      out = ["E\t#{engine}"]
      toggles = if ess?
                  %w[hpmp gold coins status items noclip noEncounter speed fastText eventSpeed oneHit invincible msgSkip]
                elsif rpgm?
                  %w[hpmp gold status items noclip noEncounter speed fastText eventSpeed oneHit invincible msgSkip]
                else
                  []
                end
      toggles.each { |name| out << "T\t#{name}" if toggle_supported?(name) }
      %w[crit escape freeSkill].each { |name| out << "T\t#{name}" if toggle_supported?(name) }
      out << "T\ttp" if engine == :ace
      out << "T\tpoints" if custom_points_supported?
      values = case engine
               when :essentials then %w[gold level exp]
               when :xp then %w[gold level exp atk def mat mdf agi dex]
               when :vx then %w[gold level exp atk def mat agi]
               when :ace then %w[gold level exp atk def mat mdf agi luk]
               else []
               end
      values << 'coins' if coins_supported?
      values << 'points' if custom_points_supported?
      values.each { |name| out << "V\t#{name}" }
      out << "V\tspeed" if engine != :unknown
      out << "F\tevents" if $game_map && $game_map.respond_to?(:events)
      out << "F\teventSelfSwitches" if event_switches && event_switches.respond_to?(:[]=)
      out << "F\tminimap" if $game_map && $game_map.respond_to?(:width) && $game_map.respond_to?(:height)
      out << "F\tbattle" if (ess? && @battle) || $game_troop
      out << "F\tbattleEscape" if toggle_supported?('escape')
      out << "F\tsaveSlots" if save_slots_supported?
      out << "F\tpokemonParty" if ess? && trainer && trainer.respond_to?(:party)
      party_pokemon = pokes
      pokemon_class = if party_pokemon.first
                        party_pokemon.first.class
                      elsif defined?(PokeBattle_Pokemon)
                        PokeBattle_Pokemon
                      elsif defined?(Pokemon)
                        Pokemon
                      else
                        nil
                      end
      can_learn_pokemon_moves = class_method?(pokemon_class, :pbLearnMoveAtIndex) || defined?(PBMove)
      out << "F\tpokemonMoves" if ess? && can_learn_pokemon_moves &&
        class_method?(pokemon_class, :moves) && class_method?(pokemon_class, :moves=) &&
        !pokemon_move_catalog.empty?
      out << "F\tpokemonStatus" if ess? && class_method?(pokemon_class, :status=)
      out << "F\tpokemonItem" if ess? && (class_method?(pokemon_class, :item=) || class_method?(pokemon_class, :setItem))
      out << "F\tpokemonStats" if ess? && class_method?(pokemon_class, :iv) && class_method?(pokemon_class, :iv=) && class_method?(pokemon_class, :ev) && class_method?(pokemon_class, :ev=) && (class_method?(pokemon_class, :calc_stats) || class_method?(pokemon_class, :calcStats))
      if rpgm?
        out << "F\tactors"
        actor_value_fields.each { |name| out << "AS\t#{name}" }
        out << "F\tactorSkills" if actors.any? { |actor| actor.respond_to?(:learn_skill) && actor.respond_to?(:forget_skill) }
        out << "F\tactorStates" if actors.any? { |actor| actor.respond_to?(:add_state) && actor.respond_to?(:remove_state) }
        out << "F\tactorEquips" if actors.any? { |actor| actor_equip_supported?(actor) }
      end
      out.join("\n")
    end

    def actor_value_fields
      fields = case engine
      when :xp then %w[level hp mhp mp mmp atk def mat mdf agi dex]
      when :vx then %w[level hp mhp mp mmp atk def mat agi]
      when :ace then %w[level hp mhp mp mmp atk def mat mdf agi luk]
      else []
      end
      fields << 'points' if custom_points_supported?
      fields
    end

    def pokemon_display_name(pokemon)
      name = pokemon.respond_to?(:name) ? pokemon.name.to_s : ""
      species = if gd?
                  (GameData::Species.get(pokemon.species).name rescue "")
                elsif defined?(PBSpecies)
                  (PBSpecies.getName(pokemon.species) rescue "")
                else
                  ""
                end
      name = species if name.empty?
      return name if species.empty? || species == name
      "#{name} (#{species})"
    rescue Exception
      name.to_s
    end

    def pokemon_item_name(pokemon)
      return "" unless pokemon && pokemon.respond_to?(:item)
      item = pokemon.item
      return "" if item.nil? || item == 0 || item.to_s.empty?
      if gd?
        data = (GameData::Item.get(item) rescue nil)
        return data.name.to_s if data && data.respond_to?(:name)
      elsif defined?(PBItems) && PBItems.respond_to?(:getName)
        name = (PBItems.getName(item.to_i) rescue nil)
        return name.to_s unless name.nil?
      end
      item.to_s
    rescue Exception
      ""
    end

    def list_pokemon
      return NOPE unless ess?
      out = []
      pokes.each_with_index do |pokemon, index|
        next unless pokemon
        name = wire_text(pokemon_display_name(pokemon))
        level = pokemon.respond_to?(:level) ? pokemon.level.to_i : 0
        hp = pokemon.respond_to?(:hp) ? pokemon.hp.to_i : 0
        maximum = pokemon.respond_to?(:totalhp) ? pokemon.totalhp.to_i : hp
        exp = pokemon.respond_to?(:exp) ? pokemon.exp.to_i : 0
        egg = pokemon.respond_to?(:egg?) && pokemon.egg?
        status = pokemon.respond_to?(:status) ? pokemon_status_key(pokemon.status) : ""
        item = wire_text(pokemon_item_name(pokemon))
        out << "P\t#{index}\t#{name}\t#{level}\t#{hp}\t#{maximum}\t#{exp}\t#{egg ? 1 : 0}\t#{status}\t#{item}"
      end
      out.join("\n")
    rescue Exception => e
      "Couldn't read party Pokemon: #{e.class}: #{e.message}"
    end

    POKEMON_STATUSES = [
      [:SLEEP, "Sleep"], [:POISON, "Poison"], [:BURN, "Burn"],
      [:PARALYSIS, "Paralysis"], [:FROZEN, "Frozen"]
    ]
    def pokemon_status_key(value)
      return "" if no_status?(value)
      return value.to_s.gsub(/^:/, "").upcase unless value.is_a?(Numeric)
      POKEMON_STATUSES.each do |key, _name|
        return key.to_s if defined?(PBStatuses) && PBStatuses.const_defined?(key) && PBStatuses.const_get(key).to_i == value.to_i
      end
      value.to_s
    rescue Exception
      value.to_s
    end

    def pokemon_at(index_text)
      pokemon = pokes[index_text.to_i]
      return [nil, "That Pokemon isn't in the current party."] unless pokemon
      return [nil, "Egg data can't be edited here."] if pokemon.respond_to?(:egg?) && pokemon.egg?
      [pokemon, nil]
    end

    def pokemon_move_key(move)
      move.respond_to?(:move) ? move.move : (move.respond_to?(:id) ? move.id : nil)
    rescue Exception
      nil
    end

    def pokemon_move_name(key)
      return "" if key.nil?
      if defined?($cache) && $cache && $cache.respond_to?(:moves)
        data = ($cache.moves[key] rescue nil)
        return data.name.to_s if data && data.respond_to?(:name) && !data.name.to_s.empty?
      end
      if key.is_a?(Integer) && defined?(PBMoves) && PBMoves.respond_to?(:getName)
        name = (PBMoves.getName(key) rescue nil)
        return name.to_s unless name.nil? || name.to_s.empty?
      end
      if defined?(GameData::Move) && GameData::Move.respond_to?(:get)
        data = (GameData::Move.get(key) rescue nil)
        return data.name.to_s if data && data.respond_to?(:name)
      end
      key.to_s
    rescue Exception
      key.to_s
    end

    def pokemon_move_catalog
      out = []
      if defined?(GameData::Move) && GameData::Move.respond_to?(:each)
        begin
          GameData::Move.each do |move|
            key = move.respond_to?(:id) ? move.id : nil
            name = move.respond_to?(:name) ? move.name.to_s : ""
            out << [key, name] if key && !name.empty?
          end
        rescue Exception
          out = []
        end
      end
      if out.empty? && defined?($cache) && $cache && $cache.respond_to?(:moves)
        begin
          data = $cache.moves
          if data.respond_to?(:each_pair)
            data.each_pair do |key, move|
              name = move.respond_to?(:name) ? move.name.to_s : ""
              out << [key, name] if key && !name.empty?
            end
          elsif data.respond_to?(:each_with_index)
            data.each_with_index do |move, index|
              next unless move
              name = move.respond_to?(:name) ? move.name.to_s : ""
              out << [index, name] if !name.empty? && index > 0
            end
          end
        rescue Exception
          out = []
        end
      end
      if out.empty? && defined?(PBMoves) && PBMoves.respond_to?(:maxValue) && PBMoves.respond_to?(:getName)
        (1..(PBMoves.maxValue rescue 0)).each do |id|
          name = (PBMoves.getName(id) rescue "").to_s
          out << [id, name] unless name.empty?
        end
      end
      seen = {}
      unique = []
      out.each do |entry|
        key, name = entry
        next if key.nil? || name.to_s.empty? || seen[key.to_s]
        seen[key.to_s] = true
        unique << [key, name.to_s]
      end
      unique.sort_by { |entry| entry[1].downcase }
    rescue Exception
      []
    end

    def list_pokemon_moves(index_text)
      pokemon, error = pokemon_at(index_text)
      return error if error
      return NOPE unless pokemon.respond_to?(:moves)
      moves = pokemon.moves || []
      (0...4).map do |slot|
        move = moves[slot]
        key = pokemon_move_key(move)
        name = key.nil? ? "" : wire_text(pokemon_move_name(key))
        "M\t#{slot}\t#{wire_text(key)}\t#{name}"
      end.join("\n")
    rescue Exception => e
      "Couldn't read Pokemon moves: #{e.class}: #{e.message}"
    end

    def list_pokemon_move_candidates
      pokemon_move_catalog.map { |key, name| "C\t#{wire_text(key)}\t#{wire_text(name)}" }.join("\n")
    rescue Exception => e
      "Couldn't read move list: #{e.class}: #{e.message}"
    end

    def list_pokemon_statuses(index_text)
      pokemon, error = pokemon_at(index_text)
      return error if error
      return NOPE unless pokemon.respond_to?(:status)
      current = pokemon_status_key(pokemon.status)
      POKEMON_STATUSES.map do |key, _name|
        "S\t#{key}\t#{current == key.to_s ? 1 : 0}"
      end.join("\n")
    rescue Exception => e
      "Couldn't read Pokemon status: #{e.class}: #{e.message}"
    end

    def list_pokemon_stats(index_text)
      pokemon, error = pokemon_at(index_text)
      return error if error
      return NOPE unless pokemon.respond_to?(:iv) && pokemon.respond_to?(:ev)
      out = []
      [ ["I", pokemon.iv], ["E", pokemon.ev] ].each do |kind, values|
        next unless values.is_a?(Array)
        values[0, 6].each_with_index { |value, index| out << "#{kind}\t#{index}\t#{value.to_i}" }
      end
      out.join("\n")
    rescue Exception => e
      "Couldn't read Pokemon stats: #{e.class}: #{e.message}"
    end

    def set_pokemon_value(index_text, payload)
      index = index_text.to_i
      field, raw = payload.to_s.split("\t", 2)
      return "Enter a whole number." unless raw.to_s =~ /\A\d+\z/
      pokemon = pokes[index]
      return "That Pokemon isn't in the current party." unless pokemon
      return "Egg data can't be edited here." if pokemon.respond_to?(:egg?) && pokemon.egg?
      value = raw.to_i
      case field
      when 'level'
        return NOPE unless pokemon.respond_to?(:level=)
        value = [[value, 1].max, max_level].min
        pokemon.level = value
        mon_calc(pokemon)
      when 'exp'
        return NOPE unless pokemon.respond_to?(:exp=)
        value = [value, max_exp_of(pokemon)].min
        pokemon.exp = value
        mon_calc(pokemon)
      when 'hp'
        return NOPE unless pokemon.respond_to?(:hp=) && pokemon.respond_to?(:totalhp)
        value = [[value, 0].max, pokemon.totalhp.to_i].min
        pokemon.hp = value
      when /\Aiv_([0-5])\z/
        return NOPE unless pokemon.respond_to?(:iv) && pokemon.respond_to?(:iv=)
        stats = pokemon.iv
        return NOPE unless stats.is_a?(Array) && stats.length >= 6
        slot = field[3, 1].to_i
        stats = stats.dup
        stats[slot] = [[value, 0].max, 31].min
        pokemon.iv = stats
        mon_calc(pokemon)
        value = stats[slot]
      when /\Aev_([0-5])\z/
        return NOPE unless pokemon.respond_to?(:ev) && pokemon.respond_to?(:ev=)
        stats = pokemon.ev
        return NOPE unless stats.is_a?(Array) && stats.length >= 6
        slot = field[3, 1].to_i
        current_total = 0
        stats.each_with_index { |n, i| current_total += n.to_i if i != slot }
        maximum = [[252, 510 - current_total].min, 0].max
        stats = stats.dup
        stats[slot] = [[value, 0].max, maximum].min
        pokemon.ev = stats
        mon_calc(pokemon)
        value = stats[slot]
      else
        return NOPE
      end
      "#{wire_text(pokemon_display_name(pokemon))} #{field} set to #{value}"
    rescue Exception => e
      "Couldn't change Pokemon: #{e.class}: #{e.message}"
    end

    def recover_pokemon(index_text)
      pokemon = pokes[index_text.to_i]
      return "That Pokemon isn't in the current party." unless pokemon
      return "Eggs can't be recovered here." if pokemon.respond_to?(:egg?) && pokemon.egg?
      pokemon.hp = pokemon.totalhp if pokemon.respond_to?(:hp=) && pokemon.respond_to?(:totalhp)
      (mon_heal_status(pokemon) rescue nil)
      (mon_heal_pp(pokemon) rescue nil)
      "#{wire_text(pokemon_display_name(pokemon))} recovered"
    rescue Exception => e
      "Couldn't recover Pokemon: #{e.class}: #{e.message}"
    end

    def set_pokemon_move(index_text, payload)
      pokemon, error = pokemon_at(index_text)
      return error if error
      return NOPE unless pokemon.respond_to?(:moves) && pokemon.respond_to?(:moves=)
      slot_text, raw_key = payload.to_s.split("\t", 2)
      return "Choose a move slot first." unless slot_text.to_s =~ /\A[0-3]\z/
      slot = slot_text.to_i
      key = raw_key.to_s.strip
      moves = (pokemon.moves || []).dup
      return "This Pokemon has no move data." unless moves.is_a?(Array)
      moves << nil while moves.length < 4
      if key.empty?
        moves[slot] = nil
        pokemon.moves = moves
        (pokemon.zmoves[slot] = nil rescue nil) if pokemon.respond_to?(:zmoves) && pokemon.zmoves.is_a?(Array)
        return "Move slot cleared."
      end
      candidate = pokemon_move_catalog.find { |entry| entry[0].to_s == key }
      return "That move isn't in this game's move list." unless candidate
      move_key = candidate[0]
      if pokemon.respond_to?(:pbLearnMoveAtIndex)
        pokemon.pbLearnMoveAtIndex(move_key, slot)
      elsif defined?(PBMove)
        moves[slot] = PBMove.new(move_key)
        pokemon.moves = moves
        (pokemon.updateZMoveIndex(slot) rescue nil) if pokemon.respond_to?(:updateZMoveIndex)
      else
        return NOPE
      end
      "#{wire_text(pokemon_display_name(pokemon))} learned #{wire_text(candidate[1])}"
    rescue Exception => e
      "Couldn't change Pokemon move: #{e.class}: #{e.message}"
    end

    def set_pokemon_status(index_text, raw_status)
      pokemon, error = pokemon_at(index_text)
      return error if error
      return NOPE unless pokemon.respond_to?(:status=)
      status = raw_status.to_s.strip.upcase
      if status.empty? || status == "NONE"
        if pokemon.respond_to?(:healStatus)
          pokemon.healStatus
        elsif pokemon.respond_to?(:heal_status)
          pokemon.heal_status
        else
          pokemon.status = nil
          pokemon.statusCount = 0 if pokemon.respond_to?(:statusCount=)
        end
        return "Pokemon status cleared."
      end
      allowed = POKEMON_STATUSES.find { |entry| entry[0].to_s == status }
      return "That status isn't supported." unless allowed
      pokemon.status = status.to_sym
      pokemon.statusCount = 0 if pokemon.respond_to?(:statusCount=)
      "#{wire_text(pokemon_display_name(pokemon))} status set to #{allowed[1]}"
    rescue Exception => e
      "Couldn't change Pokemon status: #{e.class}: #{e.message}"
    end

    def set_pokemon_item(index_text, raw_item)
      pokemon, error = pokemon_at(index_text)
      return error if error
      return NOPE unless pokemon.respond_to?(:item=) || pokemon.respond_to?(:setItem)
      key = raw_item.to_s.strip
      if key.empty? || key == "0"
        value = nil
        label = "No item"
      else
        value = gd? ? key.to_sym : key.to_i
        if gd?
          item = (GameData::Item.get(value) rescue nil)
          return "That item isn't in this game's item list." unless item
          label = item.name.to_s
        else
          return "That item isn't in this game's item list." if key.to_i <= 0
          label = (PBItems.getName(key.to_i) rescue "").to_s
          return "That item isn't in this game's item list." if label.empty?
        end
      end
      if pokemon.respond_to?(:setItem)
        pokemon.setItem(value)
      else
        pokemon.item = value
      end
      "#{wire_text(pokemon_display_name(pokemon))} held item set to #{wire_text(label)}"
    rescue Exception => e
      "Couldn't change Pokemon held item: #{e.class}: #{e.message}"
    end

    def actor_id_of(actor, index)
      value = if actor.respond_to?(:actor_id) then actor.actor_id
              elsif actor.respond_to?(:id) then actor.id
              else index + 1 end
      value.to_i
    rescue Exception
      index + 1
    end

    def actor_by_id(id_text)
      id = id_text.to_i
      actors.each_with_index do |actor, index|
        return actor if actor_id_of(actor, index) == id
      end
      nil
    end

    def list_actors
      return NOPE unless rpgm?
      out = []
      actors.each_with_index do |actor, index|
        id = actor_id_of(actor, index)
        name = wire_text(actor.respond_to?(:name) ? actor.name : "Actor #{id}")
        level = actor.respond_to?(:level) ? actor.level.to_i : 0
        hp = actor.respond_to?(:hp) ? actor.hp.to_i : 0
        mhp = actor.respond_to?(:mhp) ? actor.mhp.to_i : (actor.respond_to?(:maxhp) ? actor.maxhp.to_i : 0)
        mp = actor.respond_to?(:mp) ? actor.mp.to_i : (actor.respond_to?(:sp) ? actor.sp.to_i : 0)
        mmp = actor.respond_to?(:mmp) ? actor.mmp.to_i : (actor.respond_to?(:maxsp) ? actor.maxsp.to_i : (actor.respond_to?(:maxmp) ? actor.maxmp.to_i : 0))
        stats = actor_value_fields.reject { |field| %w[level hp mhp mp mmp].include?(field) }.map do |field|
          value = actor_stat_value(actor, field)
          "#{field}=#{value.to_i}" unless value.nil?
        end.compact
        out << (["A", id, name, level, hp, mhp, mp, mmp] + stats).join("\t")
      end
      out.join("\n")
    rescue Exception => e
      "Couldn't read actors: #{e.class}: #{e.message}"
    end

    def actor_stat_value(actor, name)
      return actor.points if name == 'points' && actor.respond_to?(:points)
      key = case engine
            when :xp then { 'atk' => :str, 'def' => :pdef, 'mat' => :int, 'mdf' => :mdef, 'agi' => :agi, 'dex' => :dex }[name]
            when :vx then { 'atk' => :atk, 'def' => :def, 'mat' => :spi, 'agi' => :agi }[name]
            when :ace then { 'atk' => :atk, 'def' => :def, 'mat' => :mat, 'mdf' => :mdf, 'agi' => :agi, 'luk' => :luk }[name]
            end
      return nil unless key && actor.respond_to?(key)
      actor.send(key)
    rescue Exception
      nil
    end

    def set_actor_value(id_text, payload)
      id = id_text.to_i
      field, raw = payload.to_s.split("\t", 2)
      return "Choose a character value first." if field.to_s.empty?
      return "Enter a whole number." unless raw.to_s =~ /\A[-+]?\d+\z/
      actor = actor_by_id(id)
      return "That character is not in the current party." unless actor
      return NOPE unless actor_value_fields.include?(field)
      value = raw.to_i
      case field
      when 'level'
        value = [[value, 1].max, (actor.respond_to?(:max_level) ? actor.max_level.to_i : 99)].min
        result = set_actor_level(actor, value)
        return result if result.is_a?(String)
      when 'hp'
        maximum = actor.respond_to?(:mhp) ? actor.mhp.to_i : actor.maxhp.to_i
        actor.hp = [[value, 0].max, maximum].min
      when 'mp'
        maximum = actor.respond_to?(:mmp) ? actor.mmp.to_i : (actor.respond_to?(:maxsp) ? actor.maxsp.to_i : actor.maxmp.to_i)
        if actor.respond_to?(:mp=)
          actor.mp = [[value, 0].max, maximum].min
        elsif actor.respond_to?(:sp=)
          actor.sp = [[value, 0].max, maximum].min
        else
          return NOPE
        end
      when 'mhp', 'mmp'
        result = set_actor_stat(actor, field, value)
        return result if result.is_a?(String)
      when 'points'
        return NOPE unless custom_points_supported? && set_custom_points(actor, value)
      else
        result = set_actor_stat(actor, field, value)
        return result if result.is_a?(String)
      end
      actor.refresh if actor.respond_to?(:refresh)
      "#{wire_text(actor.name)} #{field} set to #{value}"
    rescue Exception => e
      "Couldn't change character: #{e.class}: #{e.message}"
    end

    def set_custom_points(actor, value)
      return false unless actor && actor.respond_to?(:points) && actor.respond_to?(:points=)
      return false unless actor.points.is_a?(Numeric)
      actor.points = value
      if on?('points')
        @points_snap ||= {}
        @points_snap[custom_points_key(actor)] = value
      end
      true
    rescue Exception
      false
    end

    def set_actor_level(actor, level)
      maximum = actor.respond_to?(:max_level) ? actor.max_level.to_i : 99
      level = [[level.to_i, 1].max, maximum].min
      if actor.respond_to?(:change_level)
        actor.change_level(level, false)
        return true
      end
      if actor.respond_to?(:level=)
        actor.level = level
        return true
      end
      if actor.respond_to?(:change_exp) && actor.respond_to?(:exp_for_level)
        actor.change_exp(actor.exp_for_level(level), false)
        return true
      end
      if actor.respond_to?(:exp=)
        list = actor.respond_to?(:exp_list) ? actor.exp_list : (actor.instance_variable_get(:@exp_list) rescue nil)
        return NOPE unless list && list.respond_to?(:[]) && list[level]
        actor.exp = list[level]
        return true
      end
      NOPE
    rescue Exception => e
      "Couldn't set character level: #{e.class}: #{e.message}"
    end

    def set_actor_stat(actor, name, value)
      if engine == :ace && actor.respond_to?(:add_param) && actor.respond_to?(:param)
        id = { 'mhp' => 0, 'mmp' => 1, 'atk' => 2, 'def' => 3, 'mat' => 4, 'mdf' => 5, 'agi' => 6, 'luk' => 7 }[name]
        return NOPE unless id
        actor.add_param(id, value - actor.param(id))
        return true
      end
      if (engine == :xp || engine == :vx) && ['mhp', 'mmp'].include?(name)
        key = name == 'mhp' ? :maxhp : (engine == :vx ? :maxsp : :maxsp)
        plus = "#{key}_plus=".to_sym
        return NOPE unless actor.respond_to?(key) && actor.respond_to?(plus)
        actor.send(plus, actor.send("#{key}_plus").to_i + value - actor.send(key).to_i)
        return true
      end
      key = if engine == :xp
              { 'atk' => :str, 'def' => :pdef, 'mat' => :int, 'mdf' => :mdef, 'agi' => :agi, 'dex' => :dex }[name]
            elsif engine == :vx
              { 'atk' => :atk, 'def' => :def, 'mat' => :spi, 'agi' => :agi }[name]
            else
              nil
            end
      return NOPE unless key && actor.respond_to?(key)
      plus = "#{key}_plus=".to_sym
      return NOPE unless actor.respond_to?(plus)
      actor.send(plus, actor.send("#{key}_plus").to_i + value - actor.send(key).to_i)
      true
    end

    def recover_actor(id_text)
      actor = actor_by_id(id_text)
      return "That character is not in the current party." unless actor
      if actor.respond_to?(:recover_all)
        actor.recover_all
      else
        actor.hp = maxhp_of(actor) if actor.respond_to?(:hp=)
        maxmp = maxmp_of(actor)
        actor.mp = maxmp if actor.respond_to?(:mp=)
        actor.sp = maxmp if actor.respond_to?(:sp=)
        if actor.respond_to?(:states) && actor.respond_to?(:remove_state)
          actor.states.dup.each { |state| actor.remove_state(state.respond_to?(:id) ? state.id : state) }
        end
      end
      "#{wire_text(actor.name)} recovered"
    rescue Exception => e
      "Couldn't recover character: #{e.class}: #{e.message}"
    end

    def list_actor_collection(actor_id, collection)
      actor = actor_by_id(actor_id)
      return "That character is not in the current party." unless actor
      if collection == 'skills'
        data = defined?($data_skills) ? $data_skills : nil
        current = actor.respond_to?(:skills) ? actor.skills : []
        ids = current.map { |skill| skill.respond_to?(:id) ? skill.id.to_i : skill.to_i }
        return "" unless data
        out = []
        (1...data.length).each do |id|
          skill = data[id]
          next unless skill
          out << "C\tskill\t#{id}\t#{ids.include?(id) ? 1 : 0}\t#{wire_text(skill.name)}"
        end
        return out.join("\n")
      elsif collection == 'states'
        data = defined?($data_states) ? $data_states : nil
        current = actor.respond_to?(:states) ? actor.states : []
        ids = current.map { |state| state.respond_to?(:id) ? state.id.to_i : state.to_i }
        return "" unless data
        out = []
        (1...data.length).each do |id|
          state = data[id]
          next unless state && !state.name.to_s.empty?
          out << "C\tstate\t#{id}\t#{ids.include?(id) ? 1 : 0}\t#{wire_text(state.name)}"
        end
        return out.join("\n")
      end
      NOPE
    rescue Exception => e
      "Couldn't read character data: #{e.class}: #{e.message}"
    end

    def set_actor_collection(actor_id, payload)
      parts = payload.to_s.split("\t", 3)
      collection = parts[0].to_s
      item_id = parts[1].to_i
      enabled = parts[2] == '1'
      actor = actor_by_id(actor_id)
      return "That character is not in the current party." unless actor
      if collection == 'skills'
        return NOPE unless actor.respond_to?(:learn_skill) && actor.respond_to?(:forget_skill)
        enabled ? actor.learn_skill(item_id) : actor.forget_skill(item_id)
      elsif collection == 'states'
        return NOPE unless actor.respond_to?(:add_state) && actor.respond_to?(:remove_state)
        enabled ? actor.add_state(item_id) : actor.remove_state(item_id)
      else
        return NOPE
      end
      actor.refresh if actor.respond_to?(:refresh)
      "ok"
    rescue Exception => e
      "Couldn't change character data: #{e.class}: #{e.message}"
    end

    def actor_equip_supported?(actor)
      return actor.respond_to?(:equips) && actor.respond_to?(:change_equip) if engine != :xp
      actor && actor.respond_to?(:equip) && actor.respond_to?(:weapon_id) &&
        %w[armor1_id armor2_id armor3_id armor4_id].all? { |name| actor.respond_to?(name) }
    rescue Exception
      false
    end

    def actor_equip_ids(actor)
      if engine == :xp
        [actor.weapon_id, actor.armor1_id, actor.armor2_id, actor.armor3_id, actor.armor4_id]
      else
        actor.equips
      end
    rescue Exception
      []
    end

    def xp_actor_can_equip?(actor, kind, slot, item_id, item)
      return false unless engine == :xp && actor && item && item_id.to_i > 0
      return false if slot.to_i == 0 && kind != 'weapon'
      return false if slot.to_i > 0 && kind != 'armor'
      if kind == 'armor'
        return false unless item.respond_to?(:kind) && item.kind.to_i == slot.to_i - 1
      end
      classes = defined?($data_classes) ? $data_classes : nil
      klass = classes && actor.respond_to?(:class_id) ? classes[actor.class_id.to_i] : nil
      allowed = if kind == 'weapon'
                  klass && klass.respond_to?(:weapon_set) ? klass.weapon_set : nil
                else
                  klass && klass.respond_to?(:armor_set) ? klass.armor_set : nil
                end
      return false unless allowed && allowed.include?(item_id.to_i)
      true
    rescue Exception
      false
    end

    def list_actor_equips(actor_id)
      actor = actor_by_id(actor_id)
      return "That character is not in the current party." unless actor
      return NOPE unless actor_equip_supported?(actor)
      if engine == :xp
        return actor_equip_ids(actor).each_with_index.map do |item_id, index|
          kind = index == 0 ? 'weapon' : 'armor'
          item = item_id.to_i > 0 ? (table_for(kind)[item_id.to_i] rescue nil) : nil
          slot_name = index == 0 ? 'Weapon' : "Armor #{index}"
          "Q\t#{index}\t#{item_id.to_i}\t#{kind}\t#{slot_name}\t#{wire_text(item && item.respond_to?(:name) ? item.name : 'None')}"
        end.join("\n")
      end
      equip_slots = actor.respond_to?(:equip_slots) ? actor.equip_slots : []
      terms = ($data_system && $data_system.respond_to?(:terms)) ? $data_system.terms : nil
      etypes = terms && terms.respond_to?(:etypes) ? terms.etypes : []
      actor.equips.each_with_index.map do |item, index|
        slot_type = equip_slots[index].to_i rescue 0
        slot_name = (etypes[slot_type].to_s rescue "")
        slot_name = "Slot #{index + 1}" if slot_name.empty?
        kind = item && defined?($data_weapons) && $data_weapons && $data_weapons[item.id] ? "weapon" : "armor"
        item_id = item && item.respond_to?(:id) ? item.id.to_i : 0
        item_name = item && item.respond_to?(:name) ? item.name.to_s : "None"
        "Q\t#{index}\t#{item_id}\t#{kind}\t#{wire_text(slot_name)}\t#{wire_text(item_name)}"
      end.join("\n")
    rescue Exception => e
      "Couldn't read equipment: #{e.class}: #{e.message}"
    end

    def list_equip_candidates(payload)
      actor_id, slot_text = payload.to_s.split("\t", 2)
      actor = actor_by_id(actor_id)
      return "That character is not in the current party." unless actor
      return NOPE unless actor_equip_supported?(actor)
      slot = slot_text.to_i
      slots = actor_equip_ids(actor)
      return "That equipment slot doesn't exist." unless slot >= 0 && slot < slots.length
      out = ["Q\tnone\t0\tNone"]
      %w[weapon armor].each do |kind|
        table = table_for(kind)
        next unless table
        (1...table.length).each do |id|
          item = table[id]
          next unless item
          next if engine == :xp && !xp_actor_can_equip?(actor, kind, slot, id, item)
          count = if engine == :xp && $game_party.respond_to?("#{kind}_number")
                    ($game_party.send("#{kind}_number", id) rescue 0)
                  elsif $game_party.respond_to?(:item_number)
                    ($game_party.item_number(item) rescue 0)
                  elsif $game_party.respond_to?("#{kind}_number")
                    ($game_party.send("#{kind}_number", id) rescue 0)
                  else
                    0
                  end
          next unless count.to_i > 0
          next if actor.respond_to?(:equippable?) && !(actor.equippable?(item) rescue false)
          out << "Q\t#{kind}\t#{id}\t#{wire_text(item.name)}"
        end
      end
      out.join("\n")
    rescue Exception => e
      "Couldn't read equipment choices: #{e.class}: #{e.message}"
    end

    def set_actor_equip(actor_id, payload)
      parts = payload.to_s.split("\t", 3)
      slot = parts[0].to_i
      kind = parts[1].to_s
      item_id = parts[2].to_i
      actor = actor_by_id(actor_id)
      return "That character is not in the current party." unless actor
      return NOPE unless actor_equip_supported?(actor)
      slots = actor_equip_ids(actor)
      return "That equipment slot doesn't exist." unless slot >= 0 && slot < slots.length
      if engine == :xp
        expected_kind = slot == 0 ? 'weapon' : 'armor'
        return "Choose an item for this equipment slot." unless kind == expected_kind
        item = item_id > 0 ? (table_for(kind)[item_id] rescue nil) : nil
        return "That item doesn't exist." if item_id > 0 && !item
        return "That item can't be equipped in this slot." if item_id > 0 && !xp_actor_can_equip?(actor, kind, slot, item_id, item)
        actor.equip(slot, item_id)
        equipped_id = slot == 0 ? actor.weapon_id.to_i : actor.send("armor#{slot}_id").to_i
        return "This item isn't in the party inventory or can't be equipped." unless equipped_id == item_id
        actor.refresh if actor.respond_to?(:refresh)
        return "ok"
      end
      if item_id == 0
        item = nil
      elsif %w[weapon armor].include?(kind)
        item = (table_for(kind)[item_id] rescue nil)
      else
        return "Choose an equipment item first."
      end
      return "That item doesn't exist." if item_id > 0 && !item
      return "This character can't equip that item." if item && actor.respond_to?(:equippable?) && !(actor.equippable?(item) rescue false)
      actor.change_equip(slot, item)
      actor.refresh if actor.respond_to?(:refresh)
      "ok"
    rescue Exception => e
      "Couldn't change equipment: #{e.class}: #{e.message}"
    end

    def set_quantity(kind, payload)
      key, count_text = payload.to_s.split("\t", 2)
      return "Choose an item first." if key.to_s.empty?
      return "Enter a quantity from 0 to 999999." unless count_text.to_s =~ /\A\d+\z/
      count = [[count_text.to_i, 0].max, 999999].min
      if ess?
        return "The Essentials bag is unavailable." unless bag && kind == 'item'
        item = gd? ? key.to_sym : key.to_i
        current = bag_qty(item).to_i
        if count < current
          removed = if bag.respond_to?(:remove) then bag.remove(item, current - count)
                    elsif bag.respond_to?(:pbDeleteItem) then bag.pbDeleteItem(item, current - count)
                    else false end
          return "This bag doesn't support removing that item." unless removed
        elsif count > current
          return "The bag is full or doesn't accept this amount." unless bag_add(item, count - current)
        end
      elsif rpgm?
        return "Start or load a game first." unless $game_party
        return NOPE unless %w[item weapon armor].include?(kind)
        id = key.to_i
        return "That item ID is invalid." unless id > 0
        if engine == :xp
          current = $game_party.send("#{kind}_number", id).to_i
          $game_party.send("gain_#{kind}", id, count - current)
        else
          item = table_for(kind)[id] rescue nil
          return "That item ID doesn't exist." unless item
          current = $game_party.item_number(item).to_i
          $game_party.gain_item(item, count - current)
        end
      else
        return NOPE
      end
      actual = if ess?
                 bag_qty(item).to_i
               elsif engine == :xp
                 $game_party.send("#{kind}_number", id).to_i
               else
                 $game_party.item_number(table_for(kind)[id]).to_i
               end
      actual == count ? "Quantity set to #{actual}" : "Game inventory limit set the quantity to #{actual}"
    rescue Exception => e
      "Couldn't set quantity: #{e.class}: #{e.message}"
    end

    # The game's System database contains the labels used by its own debug
    # screen. Values remain in the game's live Game_Switches/Game_Variables.
    def list_system
      return "Start or load a game first." unless $game_switches && $game_variables
      database = system_database
      return "The game's System database is unavailable." unless database
      switches = system_names(database, :switches)
      variables = system_names(database, :variables)
      return "The game's switch and variable tables are unavailable." unless switches && variables
      out = []
      [switches, variables].each_with_index do |names, group|
        limit = names.length - 1
        1.upto(limit) do |id|
          name = wire_text(names[id])
          name = (group == 0 ? "Switch #{id}" : "Variable #{id}") if name.empty?
          if group == 0
            value = ($game_switches[id] ? "1" : "0")
            out << "S\t#{id}\t#{value}\t#{name}"
          else
            value = $game_variables[id]
            if value.is_a?(Numeric)
              type = 'number'
              text = wire_text(value)
            elsif value == true || value == false
              type = 'boolean'
              text = value ? 'true' : 'false'
            elsif value.nil?
              type = 'json'
              text = 'null'
            elsif value.is_a?(String)
              type = 'string'
              text = wire_text(value)
            elsif value.is_a?(Array) || value.is_a?(Hash)
              parser = json_module
              if parser
                serialized = json_generate_value(parser, value)
                if serialized
                  type = 'json'
                  text = wire_text(serialized)
                else
                  type = 'complex'
                  text = "<#{value.class}>"
                end
              else
                type = 'complex'
                text = "<#{value.class}>"
              end
            else
              type = 'complex'
              text = "<#{value.class}>"
            end
            out << "V\t#{id}\t#{type}\t#{text}\t#{name}"
          end
        end
      end
      out.join("\n")
    rescue Exception => e
      "Couldn't read game switches and variables: #{e.class}: #{e.message}"
    end

    def refresh_game_map
      if $game_map && $game_map.respond_to?(:need_refresh=)
        $game_map.need_refresh = true
      end
    rescue Exception
      nil
    end

    def set_system_switch(id_text, value)
      return "Start or load a game first." unless $game_switches
      id = id_text.to_i
      database = system_database
      names = system_names(database, :switches)
      return "That switch ID is not in this game's System database." unless id > 0 && names && id < names.length
      return "Switch value must be 0 or 1." unless value == '0' || value == '1'
      $game_switches[id] = (value == '1')
      refresh_game_map
      ""
    rescue Exception => e
      "Couldn't set switch #{id_text}: #{e.class}: #{e.message}"
    end

    def set_system_variable(id_text, value)
      return "Start or load a game first." unless $game_variables
      id = id_text.to_i
      database = system_database
      names = system_names(database, :variables)
      return "That variable ID is not in this game's System database." unless id > 0 && names && id < names.length
      parser = json_module
      begin
        # Valid JSON may change the variable's type; other input is stored as
        # a Ruby String so ordinary text remains directly editable.
        replacement = parser ? json_parse_value(parser, value.to_s) : value.to_s
      rescue Exception
        replacement = value.to_s
      end
      $game_variables[id] = replacement
      refresh_game_map
      ""
    rescue Exception => e
      "Couldn't set variable #{id_text}: #{e.class}: #{e.message}"
    end

    def map_info_table
      if ess? && defined?($cache) && $cache && $cache.respond_to?(:mapinfos)
        value = ($cache.mapinfos rescue nil)
        return value if value
      end
      return $data_mapinfos if defined?($data_mapinfos) && $data_mapinfos
      ext = case engine
            when :xp, :essentials then 'rxdata'
            when :vx then 'rvdata'
            when :ace then 'rvdata2'
            else nil
            end
      return nil unless ext
      (load_data("Data/MapInfos.#{ext}") rescue nil)
    rescue Exception
      nil
    end

    def map_info_name(info)
      value = if info.respond_to?(:name)
                (info.name rescue nil)
              else
                (info.instance_variable_get(:@name) rescue nil)
              end
      wire_text(value)
    rescue Exception
      ""
    end

    def map_info_parent(info)
      value = if info.respond_to?(:parent_id)
                (info.parent_id rescue nil)
              elsif info.respond_to?(:parentId)
                (info.parentId rescue nil)
              else
                (info.instance_variable_get(:@parent_id) rescue nil)
              end
      value.to_i
    rescue Exception
      0
    end

    def map_info_depth(id, table)
      depth = 0
      seen = {}
      info = table.is_a?(Hash) ? table[id] : (table.is_a?(Array) ? table[id] : nil)
      parent = map_info_parent(info)
      while parent > 0 && depth < 32 && !seen[parent]
        seen[parent] = true
        ancestor = table.is_a?(Hash) ? table[parent] : (table.is_a?(Array) ? table[parent] : nil)
        break unless ancestor
        depth += 1
        parent = map_info_parent(ancestor)
      end
      depth
    rescue Exception
      0
    end

    def map_info_entries
      table = map_info_table
      return [] unless table
      out = []
      if table.is_a?(Hash)
        table.each do |id, info|
          next unless id.to_s =~ /\A\d+\z/
          out << [id.to_i, map_info_name(info)] if info
        end
      elsif table.is_a?(Array)
        table.each_with_index do |info, id|
          out << [id, map_info_name(info)] if id > 0 && info
        end
      end
      out.sort_by { |entry| entry[0] }
    rescue Exception
      []
    end

    def list_maps
      current = ($game_map && $game_map.respond_to?(:map_id)) ? $game_map.map_id.to_i : 0
      table = map_info_table
      x = ($game_player && $game_player.respond_to?(:x)) ? $game_player.x.to_i : nil
      y = ($game_player && $game_player.respond_to?(:y)) ? $game_player.y.to_i : nil
      direction = ($game_player && $game_player.respond_to?(:direction)) ? $game_player.direction.to_i : nil
      map_info_entries.map do |id, name|
        name = "Map #{id}" if name.empty?
        here = id == current
        map_x = here && !x.nil? ? x : ""
        map_y = here && !y.nil? ? y : ""
        map_direction = here && !direction.nil? ? direction : ""
        "#{id}\t#{here ? 1 : 0}\t#{map_info_depth(id, table)}\t#{map_x}\t#{map_y}\t#{map_direction}\t#{name}"
      end.join("\n")
    rescue Exception => e
      "Couldn't read map list: #{e.class}: #{e.message}"
    end

    def event_switches
      switches = if defined?($game_self_switches) && $game_self_switches then $game_self_switches
                elsif defined?($game_selfswitches) && $game_selfswitches then $game_selfswitches
                else nil end
      switches
    rescue Exception
      nil
    end

    def list_events
      return "The current map isn't ready." unless $game_map && $game_map.respond_to?(:events)
      map_id = $game_map.respond_to?(:map_id) ? $game_map.map_id.to_i : 0
      switches = event_switches
      out = ["M\t#{map_id}"]
      $game_map.events.each do |id, live|
        next unless live
        info = live.respond_to?(:event) ? live.event : nil
        name = info && info.respond_to?(:name) ? info.name : nil
        pages = info && info.respond_to?(:pages) ? info.pages : []
        first_page = pages && pages[0]
        commands = first_page && first_page.respond_to?(:list) ? first_page.list : []
        name = "Event #{id}" if name.to_s.empty?
        values = []
        %w[A B C D].each do |letter|
          value = false
          if switches && switches.respond_to?(:[])
            value = (switches[[map_id, id.to_i, letter]] rescue false)
          end
          values << (value ? 1 : 0)
        end
        x = live.respond_to?(:x) ? live.x.to_i : 0
        y = live.respond_to?(:y) ? live.y.to_i : 0
        out << "E\t#{id.to_i}\t#{wire_text(name)}\t#{x}\t#{y}\t#{values.join("\t")}\t#{pages ? pages.length : 0}\t#{commands ? commands.length : 0}"
      end
      out.join("\n")
    rescue Exception => e
      "Couldn't read map events: #{e.class}: #{e.message}"
    end

    def set_event_switch(id_text, payload)
      letter, enabled = payload.to_s.split("\t", 2)
      return "This game doesn't expose self-switches." unless event_switches && event_switches.respond_to?(:[]=)
      return "Choose on or off." unless enabled == '1' || enabled == '0'
      return "The current map isn't ready." unless $game_map && $game_map.respond_to?(:map_id)
      id = id_text.to_i
      return "That event doesn't exist on this map." unless $game_map.respond_to?(:events) && $game_map.events[id]
      switches = event_switches
      return "This game doesn't expose self-switches." unless switches && switches.respond_to?(:[]=)
      if letter == 'ALL'
        %w[A B C D].each { |key| switches[[ $game_map.map_id.to_i, id, key ]] = (enabled == '1') }
      else
        return "Use a self-switch from A to D." unless %w[A B C D].include?(letter)
        switches[[ $game_map.map_id.to_i, id, letter ]] = (enabled == '1')
      end
      refresh_game_map
      "ok"
    rescue Exception => e
      "Couldn't change self-switch: #{e.class}: #{e.message}"
    end

    def list_minimap
      return "The current map isn't ready." unless $game_map && $game_map.respond_to?(:width) && $game_map.respond_to?(:height)
      source_width = [[$game_map.width.to_i, 1].max, 8192].min
      source_height = [[$game_map.height.to_i, 1].max, 8192].min
      step = 1
      while ((source_width + step - 1) / step) * ((source_height + step - 1) / step) > 65536
        step += 1
      end
      width = (source_width + step - 1) / step
      height = (source_height + step - 1) / step
      px = $game_player && $game_player.respond_to?(:x) ? $game_player.x.to_i / step : -1
      py = $game_player && $game_player.respond_to?(:y) ? $game_player.y.to_i / step : -1
      grid = Array.new(height) { Array.new(width, '#') }
      height.times do |y|
        width.times do |x|
          open = false
          [2, 4, 6, 8].each do |direction|
            begin
              if $game_map.passable?(x * step, y * step, direction)
                open = true
                break
              end
            rescue Exception
            end
          end
          grid[y][x] = '.' if open
        end
      end
      events = []
      if $game_map.respond_to?(:events)
        $game_map.events.each do |event_id, event|
          next unless event && event.respond_to?(:x) && event.respond_to?(:y)
          x = event.x.to_i / step; y = event.y.to_i / step
          grid[y][x] = 'E' if x >= 0 && y >= 0 && x < width && y < height
          events << "E\t#{event_id.to_i}\t#{x}\t#{y}" if x >= 0 && y >= 0 && x < width && y < height
        end
      end
      grid[py][px] = '@' if px >= 0 && py >= 0 && px < width && py < height
      out = ["H\t#{width}\t#{height}\t#{px}\t#{py}"]
      grid.each { |row| out << "R\t#{row.join}" }
      out.concat(events)
      out.join("\n")
    rescue Exception => e
      "Couldn't read map layout: #{e.class}: #{e.message}"
    end

    def battle_enemies
      if ess? && @battle
        battlers = @battle.respond_to?(:battlers) ? @battle.battlers : []
        return battlers.compact.select { |b| foe?(@battle, b.index) }.map { |b| [b.index.to_i, b.name.to_s, b.hp.to_i, (b.respond_to?(:totalhp) ? b.totalhp.to_i : b.hp.to_i)] }
      end
      return [] unless $game_troop
      values = $game_troop.respond_to?(:enemies) ? $game_troop.enemies : ($game_troop.respond_to?(:members) ? $game_troop.members : [])
      values.each_with_index.map do |enemy, index|
        [index, (enemy.respond_to?(:name) ? enemy.name.to_s : "Enemy #{index + 1}"), enemy.respond_to?(:hp) ? enemy.hp.to_i : 0,
         (enemy.respond_to?(:mhp) ? enemy.mhp.to_i : (enemy.respond_to?(:maxhp) ? enemy.maxhp.to_i : 0))]
      end
    rescue Exception
      []
    end

    def list_battle
      return NEED_BATTLE unless in_battle?
      battle_enemies.map { |row| "B\t#{row[0]}\t#{wire_text(row[1])}\t#{row[2]}\t#{row[3]}" }.join("\n")
    rescue Exception => e
      "Couldn't read battle: #{e.class}: #{e.message}"
    end

    def list_battle_actors
      return NEED_BATTLE unless in_battle?
      rows = if ess? && @battle
               @battle.battlers.compact.reject { |battler| foe?(@battle, battler.index) }.map do |battler|
                 [battler.index.to_i, battler.name.to_s, battler.hp.to_i,
                  (battler.respond_to?(:totalhp) ? battler.totalhp.to_i : battler.hp.to_i)]
               end
             else
               list = $game_party && $game_party.respond_to?(:battle_members) ? $game_party.battle_members : actors
               list.each_with_index.map do |actor, index|
                 [index, actor.respond_to?(:name) ? actor.name.to_s : "Actor #{index + 1}",
                  actor.respond_to?(:hp) ? actor.hp.to_i : 0,
                  actor.respond_to?(:mhp) ? actor.mhp.to_i : maxhp_of(actor).to_i]
               end
             end
      rows.map { |row| "L\t#{row[0]}\t#{wire_text(row[1])}\t#{row[2]}\t#{row[3]}" }.join("\n")
    rescue Exception => e
      "Couldn't read party battle status: #{e.class}: #{e.message}"
    end

    def set_enemy_hp(index_text, hp_text)
      return NEED_BATTLE unless in_battle?
      return "Enter a non-negative HP value." unless hp_text.to_s =~ /\A\d+\z/
      index = index_text.to_i
      value = hp_text.to_i
      if ess? && @battle
        enemy = @battle.battlers.compact.find { |b| b.index.to_i == index && foe?(@battle, b.index) }
        return "That enemy isn't in the current battle." unless enemy
        max = enemy.respond_to?(:totalhp) ? enemy.totalhp.to_i : enemy.hp.to_i
        target = [[value, 0].max, max].min
        if enemy.respond_to?(:hp=)
          enemy.hp = target
        elsif enemy.respond_to?(:pbReduceHP) && target <= enemy.hp
          enemy.pbReduceHP(enemy.hp - target, false)
        else
          return NOPE
        end
        enemy.pbFaint(true) if target == 0 && enemy.respond_to?(:pbFaint)
      else
        rows = $game_troop.respond_to?(:enemies) ? $game_troop.enemies : ($game_troop.respond_to?(:members) ? $game_troop.members : [])
        enemy = rows[index]
        return "That enemy isn't in the current battle." unless enemy
        max = enemy.respond_to?(:mhp) ? enemy.mhp.to_i : enemy.maxhp.to_i
        enemy.hp = [[value, 0].max, max].min
        enemy.refresh if enemy.respond_to?(:refresh)
      end
      "Enemy HP updated"
    rescue Exception => e
      "Couldn't change enemy HP: #{e.class}: #{e.message}"
    end

    def safe_map_position(map)
      width = map.width.to_i
      height = map.height.to_i
      return nil if width <= 0 || height <= 0
      100.times do
        x = rand(width)
        y = rand(height)
        next if map.respond_to?(:valid?) && !map.valid?(x, y)
        passable = false
        if map.respond_to?(:passableStrict?)
          passable = (map.passableStrict?(x, y, 0, $game_player) rescue false)
        elsif map.respond_to?(:passable?)
        # RGSS uses the same directional values for XP, VX and VX Ace.
        directions = [2, 4, 6, 8]
          directions.each do |direction|
            if (map.passable?(x, y, direction) rescue false)
              passable = true
              break
            end
          end
        end
        next unless passable
        blocked = false
        if map.respond_to?(:events)
          map.events.values.each do |event|
            next unless event.x == x && event.y == y
            next if event.respond_to?(:through) && event.through
            character = event.respond_to?(:character_name) ? event.character_name.to_s : ""
            tile = event.respond_to?(:tile_id) ? event.tile_id.to_i : 0
            if !character.empty? || tile > 0
              blocked = true
              break
            end
          end
        end
        return [x, y] unless blocked
      end
      nil
    rescue Exception
      nil
    end

    def warp_map(id_text, coordinate_text = "")
      return "Start or load a game first." unless $game_player && $game_map
      id = id_text.to_i
      return "Enter a valid map ID." unless id > 0
      info = map_info_table
      exists = info.is_a?(Hash) ? (info[id] || info[id.to_s]) : (info.is_a?(Array) ? info[id] : nil)
      return "That map ID is not in this game's MapInfos database." unless exists
      map = Game_Map.new
      map.setup(id)
      position = nil
      if !coordinate_text.to_s.empty?
        coords = coordinate_text.to_s.split("\t", 2)
        return "Enter both map coordinates." unless coords.length == 2 && coords[0] =~ /\A\d+\z/ && coords[1] =~ /\A\d+\z/
        x = coords[0].to_i; y = coords[1].to_i
        valid = x >= 0 && y >= 0 && x < map.width.to_i && y < map.height.to_i
        valid = false if valid && map.respond_to?(:valid?) && !(map.valid?(x, y) rescue false)
        return "That location is outside the map." unless valid
        position = [x, y]
      else
        position = safe_map_position(map)
      end
      return "Couldn't find a passable landing tile on that map." unless position
      x, y = position
      if ess?
        # Older Essentials versions use the pending-transfer fields from their
        # debug menu. Newer Rejuvenation exposes a parameterized helper.
        helper = (Object.new.method(:pbWarpToMap) rescue nil)
        if helper && helper.arity != 0
          return "The game's map warp failed." unless helper.call(id, x, y)
          return "ok"
        end
        if defined?(Scene_Map) && defined?($scene) && $scene.is_a?(Scene_Map) && $scene.respond_to?(:transfer_player)
          $game_temp.player_new_map_id = id
          $game_temp.player_new_x = x
          $game_temp.player_new_y = y
          $game_temp.player_new_direction = 2
          $scene.transfer_player
          $game_map.refresh if $game_map.respond_to?(:refresh)
        else
          # Reborn and Desolation expose a zero-argument pbWarpToMap that opens
          # their own interactive picker. Their debug menu applies the selected
          # destination through MapFactory when it is opened outside Scene_Map;
          # use the same game-owned transfer path with our already selected map
          # and coordinates instead of rejecting the action from menus/battles.
          return "This game doesn't expose a map transfer method for this screen." unless
            defined?($MapFactory) && $MapFactory && $MapFactory.respond_to?(:setup) &&
            $game_player.respond_to?(:moveto)
          (Kernel.pbCancelVehicles rescue nil)
          $MapFactory.setup(id)
          $game_player.moveto(x, y)
          $game_player.turn_down if $game_player.respond_to?(:turn_down)
          $game_map.update if $game_map && $game_map.respond_to?(:update)
          $game_map.autoplay if $game_map && $game_map.respond_to?(:autoplay)
          $game_map.refresh if $game_map && $game_map.respond_to?(:refresh)
        end
      elsif engine == :xp
        $game_temp.player_new_map_id = id
        $game_temp.player_new_x = x
        $game_temp.player_new_y = y
        $game_temp.player_new_direction = 2
        $game_temp.player_transferring = true
      elsif engine == :vx || engine == :ace
        return "This game does not expose the native transfer method." unless $game_player.respond_to?(:reserve_transfer)
        $game_player.reserve_transfer(id, x, y, 2)
      else
        return NOPE
      end
      "ok"
    rescue Exception => e
      "Map warp failed: #{e.class}: #{e.message}"
    end

    def list_items(_ignored = "")
      out = []
      case engine
      when :essentials
        if gd?
          GameData::Item.each do |i|
            n = i.name.to_s
            description = i.respond_to?(:description) ? wire_text(i.description) : ""
            out << "item\t#{i.id}\t#{quantity_of('item', i.id)}\t#{wire_text(n)}\t#{description}" unless n.empty?
          end
          GameData::Species.each do |s|
            next if s.form != 0
            n = s.name.to_s
            out << "pokemon\t#{s.species}\t0\t#{wire_text(n)}\t" unless n.empty?
          end
        else
          (1..(PBItems.maxValue rescue 0)).each do |i|
            n = (PBItems.getName(i) rescue "").to_s
            out << "item\t#{i}\t#{quantity_of('item', i)}\t#{wire_text(n)}\t" unless n.empty?
          end
          (1..(PBSpecies.maxValue rescue 0)).each do |i|
            n = (PBSpecies.getName(i) rescue "").to_s
            out << "pokemon\t#{i}\t0\t#{wire_text(n)}\t" unless n.empty?
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
            description = o.respond_to?(:description) ? wire_text(o.description) : ""
            out << "#{kind}\t#{i}\t#{quantity_of(kind, i)}\t#{wire_text(n)}\t#{description}" unless n.empty?
          end
        end
      end
      out.join("\n")
    end

    def list_gold
      amount = if ess?
                 t = trainer
                 t && t.respond_to?(:money) ? t.money.to_i : 0
               elsif $game_party && $game_party.respond_to?(:gold)
                 $game_party.gold.to_i
               else
                 0
               end
      "G\t#{amount}"
    rescue Exception => e
      "Couldn't read gold: #{e.class}: #{e.message}"
    end

    def quantity_of(kind, key)
      if ess?
        return 0 unless kind == 'item' && bag
        bag_qty(gd? ? key.to_sym : key.to_i).to_i
      elsif engine == :xp
        $game_party ? $game_party.send("#{kind}_number", key.to_i).to_i : 0
      elsif rpgm?
        item = table_for(kind)[key.to_i] rescue nil
        $game_party && item ? $game_party.item_number(item).to_i : 0
      else
        0
      end
    rescue Exception
      0
    end

    # ------------------------------------------------------ one-click actions
    def run_action(name, options = "")
      if name.to_s =~ /\AsaveSlot:(\d+)\z/
        return save_slot($1)
      end
      if name.to_s =~ /\AloadSlot:(\d+)\z/
        return load_slot($1)
      end
      return warp_map(name.to_s.split(':', 2)[1], options) if name.to_s.index('warp:') == 0
      case name
      when 'maxExp'        then max_exp
      when 'instantWin'    then instant_win
      when 'killEnemies'   then kill_enemies
      when 'fullInventory' then full_inventory
      when 'recoverParty'  then recover_party
      when 'maxGold'       then max_gold
      when 'forceVictory'  then force_victory
      when 'forceEscape'   then force_escape
      when 'saveScreen'    then open_native_screen(:save)
      when 'loadScreen'    then open_native_screen(:load)
      when 'nativeMenu'    then open_native_window
      when 'closeNativeMenu' then close_native_window
      else "Unknown action."
      end
    end

    def save_scene
      return nil unless defined?(Scene_Save)
      engine == :vx ? Scene_Save.new(true, false, false) : Scene_Save.new
    rescue Exception
      nil
    end

    def load_scene
      return nil unless defined?(Scene_Load)
      engine == :vx ? Scene_Load.new(false, false, false) : Scene_Load.new
    rescue Exception
      nil
    end

    def save_slot_path(slot)
      id = slot.to_i
      return nil if id < 1
      if ess? && defined?(RTP) && RTP.respond_to?(:getSaveSlotPath)
        return RTP.getSaveSlotPath(id)
      end
      if engine == :ace && defined?(DataManager) && DataManager.respond_to?(:make_filename)
        return DataManager.make_filename(id - 1).to_s
      end
      scene = save_scene
      return nil unless scene && scene.respond_to?(:make_filename, true)
      scene.send(:make_filename, id - 1).to_s
    rescue Exception
      nil
    end

    def save_slots_supported?
      if ess?
        has_save = Object.private_method_defined?(:pbSave) || Object.method_defined?(:pbSave)
        has_load = defined?(PokemonLoad) && class_method?(PokemonLoad, :pbTryLoadFile) &&
                   class_method?(PokemonLoad, :startPlayingSaveFile)
        return has_save && has_load && defined?(RTP) && RTP.respond_to?(:getSaveSlotPath)
      end
      if engine == :ace
        return defined?(DataManager) && DataManager.respond_to?(:make_filename) &&
               DataManager.respond_to?(:save_game) && DataManager.respond_to?(:load_game)
      end
      return false unless rpgm? && defined?(Scene_File) && defined?(Scene_Save) && defined?(Scene_Load)
      has_filename = class_method?(Scene_File, :make_filename)
      has_writer = class_method?(Scene_Save, :write_save_data)
      has_reader = if engine == :xp
                     class_method?(Scene_Load, :on_decision)
                   else
                     class_method?(Scene_Load, :read_save_data)
                   end
      has_filename && has_writer && has_reader
    rescue Exception
      false
    end

    def declared_save_slot_limit(owner)
      return nil unless owner && owner.respond_to?(:const_defined?) && owner.respond_to?(:const_get)
      [:SAVE_FILES, :SAVEFILE_MAX, :MAX_SAVE_FILES, :FILE_MAX].each do |name|
        begin
          next unless owner.const_defined?(name)
          count = owner.const_get(name).to_i
          return count if count > 0 && count <= 100
        rescue Exception
        end
      end
      nil
    rescue Exception
      nil
    end

    # Essentials uses numbered files with no configured slot ceiling. Build
    # the picker from real save files, the current slot, and the first free
    # slot instead of hiding saves after an arbitrary count.
    def essentials_save_slot_ids
      ids = {}
      current = if defined?($Unidata) && $Unidata
                  ($Unidata[:saveslot].to_i rescue 1)
                else
                  1
                end
      current = 1 if current < 1
      ids[current] = true

      listed = false
      if defined?(RTP) && RTP.respond_to?(:getSaveFolder)
        begin
          folder = RTP.getSaveFolder.to_s
          Dir.entries(folder).each do |filename|
            next unless filename =~ /\AGame(?:_(\d+))?\.rxdata\z/i
            slot_text = $1
            next unless File.file?(File.join(folder, filename))
            slot = slot_text ? slot_text.to_i : 1
            ids[slot] = true if slot > 0
          end
          listed = true
        rescue Exception
        end
      end

      if listed
        next_slot = 1
        next_slot += 1 while ids[next_slot]
        ids[next_slot] = true
      else
        # Keep the current slot usable if a custom RTP hides its save folder.
        ids[current + 1] = true
      end
      ids.keys.sort
    rescue Exception
      [current > 0 ? current : 1]
    end

    def valid_save_slot_id?(id)
      return false if id < 1
      return essentials_save_slot_ids.include?(id) if ess?
      id <= save_slot_limit
    rescue Exception
      false
    end

    def save_slot_limit
      return essentials_save_slot_ids.max || 1 if ess?
      if engine == :ace && defined?(DataManager) && DataManager.respond_to?(:savefile_max)
        count = (DataManager.savefile_max.to_i rescue 0)
        return [[count, 1].max, 100].min if count > 0
      end
      if defined?(Scene_File)
        [Scene_File, (Scene_Save if defined?(Scene_Save)), (Scene_Load if defined?(Scene_Load))].compact.each do |klass|
          count = declared_save_slot_limit(klass)
          return count if count
          begin
            scene = klass == Scene_Load ? load_scene : save_scene
            count = scene.instance_variable_get(:@file_max).to_i if scene
            return count if count && count > 0 && count <= 100
          rescue Exception
          end
        end
      end
      Object.constants.each do |constant_name|
        begin
          owner = Object.const_get(constant_name)
          count = declared_save_slot_limit(owner) if owner.is_a?(Module)
          return count if count
        rescue Exception
        end
      end
      engine == :ace ? 20 : 4
    rescue Exception
      engine == :ace ? 20 : 4
    end

    def list_save_slots
      return NOPE unless save_slots_supported?
      current = ess? && defined?($Unidata) && $Unidata ? ($Unidata[:saveslot].to_i rescue 1) :
                (@selected_save_slot ? @selected_save_slot.to_i : 1)
      current = 1 if current < 1
      slot_ids = ess? ? essentials_save_slot_ids : (1..[[save_slot_limit.to_i, 1].max, 100].min).to_a
      out = []
      slot_ids.each do |id|
        path = save_slot_path(id)
        next if path.to_s.empty?
        exists = File.file?(path) rescue false
        modified = exists ? ((File.mtime(path).to_i rescue 0)) : 0
        label = ess? ? (id == 1 ? "Game.rxdata" : "Game_#{id}.rxdata") : File.basename(path)
        out << "S\t#{id}\t#{exists ? 1 : 0}\t#{id == current ? 1 : 0}\t#{modified}\t#{wire_text(label)}"
      end
      out.join("\n")
    rescue Exception => e
      "Couldn't read save slots: #{e.class}: #{e.message}"
    end

    def select_save_slot(id_text)
      id = id_text.to_i
      return "Enter a valid save slot." unless valid_save_slot_id?(id)
      path = save_slot_path(id)
      return "This game doesn't expose that save slot." if path.to_s.empty?
      if ess? && defined?($Unidata) && $Unidata
        $Unidata[:saveslot] = id
        (saveClientData rescue nil)
      else
        @selected_save_slot = id
      end
      "ok"
    rescue Exception => e
      "Couldn't select save slot: #{e.class}: #{e.message}"
    end

    def save_slot(id_text)
      id = id_text.to_i
      return "Enter a valid save slot." unless valid_save_slot_id?(id)
      path = save_slot_path(id)
      return "This game doesn't expose that save slot." if path.to_s.empty?
      result = select_save_slot(id)
      return result unless result == "ok"
      if ess?
        saver = (Object.new.method(:pbSave) rescue nil)
        return "This game doesn't expose its native save function." unless saver
        return "The game couldn't save this slot." unless saver.call
      elsif engine == :ace
        return "The game couldn't save this slot." unless DataManager.save_game(id - 1)
      else
        scene = save_scene
        return "This game's native save writer isn't available." unless scene && scene.respond_to?(:write_save_data, true)
        scene.instance_variable_set(:@index, id - 1)
        scene.instance_variable_set(:@file_index, id - 1)
        if $game_temp && $game_temp.respond_to?(:last_file_index=)
          $game_temp.last_file_index = id - 1
        end
        temporary = path.to_s + ".tyranor-tmp"
        begin
          File.delete(temporary) if File.file?(temporary)
          File.open(temporary, "wb") { |file| scene.send(:write_save_data, file) }
          File.rename(temporary, path)
        rescue Exception
          File.delete(temporary) rescue nil
          raise
        end
        if $game_temp && $game_temp.respond_to?(:last_file_index=)
          $game_temp.last_file_index = id - 1
        end
      end
      "Save slot #{id} saved"
    rescue Exception => e
      "Couldn't save slot #{id_text}: #{e.class}: #{e.message}"
    end

    def load_slot(id_text)
      id = id_text.to_i
      return "Enter a valid save slot." unless valid_save_slot_id?(id)
      path = save_slot_path(id)
      return "This game doesn't expose that save slot." if path.to_s.empty?
      return "That save slot is empty." unless File.file?(path)
      result = select_save_slot(id)
      return result unless result == "ok"
      if ess?
        # The game's loader owns migrations, custom save data, and map setup.
        stub_scene = Class.new do
          def pbEndScene; end
        end.new
        loader = PokemonLoad.new(stub_scene)
        loader.send(:pbTryLoadFile, path)
        return "The game's native loader rejected this save." unless loader.send(:startPlayingSaveFile, path)
      elsif engine == :ace
        return "The game couldn't load this slot." unless DataManager.load_game(id - 1)
        SceneManager.goto(Scene_Map) if defined?(SceneManager) && defined?(Scene_Map)
      elsif engine == :xp
        scene = load_scene
        return "This game's native load handler isn't available." unless scene && scene.respond_to?(:on_decision, true)
        scene.send(:on_decision, path.to_s)
      else
        scene = load_scene
        return "This game's native load reader isn't available." unless scene && scene.respond_to?(:read_save_data, true)
        scene.instance_variable_set(:@index, id - 1)
        scene.instance_variable_set(:@file_index, id - 1)
        File.open(path, "rb") { |file| scene.send(:read_save_data, file) }
        if $game_temp && $game_temp.respond_to?(:last_file_index=)
          $game_temp.last_file_index = id - 1
        end
        $scene = Scene_Map.new if defined?(Scene_Map)
        if scene.instance_variable_defined?(:@last_bgm) || scene.instance_variable_defined?(:@last_bgs)
          RPG::BGM.fade(1500) if defined?(RPG::BGM) && RPG::BGM.respond_to?(:fade)
          Graphics.fadeout(60) if defined?(Graphics) && Graphics.respond_to?(:fadeout)
          Graphics.wait(40) if defined?(Graphics) && Graphics.respond_to?(:wait)
          bgm = scene.instance_variable_get(:@last_bgm)
          bgs = scene.instance_variable_get(:@last_bgs)
          bgm.play if bgm && bgm.respond_to?(:play)
          bgs.play if bgs && bgs.respond_to?(:play)
        end
      end
      "Save slot #{id} loaded"
    rescue Exception => e
      "Couldn't load slot #{id_text}: #{e.class}: #{e.message}"
    end

    def delete_save_slot(id_text)
      id = id_text.to_i
      return "Enter a valid save slot." unless valid_save_slot_id?(id)
      path = save_slot_path(id)
      return "This game doesn't expose that save slot." if path.to_s.empty?
      return "That save slot is empty." unless File.file?(path)
      File.delete(path)
      "Save slot #{id} deleted"
    rescue Exception => e
      "Couldn't delete save slot: #{e.class}: #{e.message}"
    end

    def consume_save_slot_index
      return nil unless @selected_save_slot
      index = @selected_save_slot.to_i - 1
      @selected_save_slot = nil
      index >= 0 ? index : nil
    rescue Exception
      @selected_save_slot = nil
      nil
    end

    def recover_party
      if ess?
        return "You have no Pokemon yet." if pokes.empty?
        pokes.each do |pokemon|
          next if pokemon.respond_to?(:egg?) && pokemon.egg?
          pokemon.hp = pokemon.totalhp if pokemon.respond_to?(:hp=) && pokemon.respond_to?(:totalhp)
          (mon_heal_status(pokemon) rescue nil)
          (mon_heal_pp(pokemon) rescue nil)
        end
        return "Party recovered"
      end
      return "Your party is empty." if actors.empty?
      actors.each do |actor|
        if actor.respond_to?(:recover_all)
          actor.recover_all
        else
          actor.hp = maxhp_of(actor) if actor.respond_to?(:hp=)
          maximum = maxmp_of(actor)
          actor.mp = maximum if actor.respond_to?(:mp=)
          actor.sp = maximum if actor.respond_to?(:sp=)
          if actor.respond_to?(:states) && actor.respond_to?(:remove_state)
            actor.states.dup.each { |state| actor.remove_state(state.respond_to?(:id) ? state.id : state) }
          end
        end
      end
      "Party recovered"
    rescue Exception => e
      "Couldn't recover party: #{e.class}: #{e.message}"
    end

    def max_gold
      if ess?
        t = trainer
        return "Trainer data isn't ready yet. Start or load a game first." unless t
        maximum = ess_money_limit
        t.money = maximum
        "Money set to #{t.money}"
      elsif rpgm? && $game_party
        maximum = rpgm_gold_limit
        actual = set_rpgm_gold(maximum)
        return "Gold set to #{actual}"
      else
        "Start or load a game first."
      end
    rescue Exception => e
      "Couldn't maximize gold: #{e.class}: #{e.message}"
    end

    def force_victory
      return instant_win if ess?
      return NEED_BATTLE unless in_battle?
      if defined?(BattleManager) && BattleManager.respond_to?(:process_victory)
        BattleManager.process_victory
        return "Battle victory requested"
      end
      foes = $game_troop.respond_to?(:enemies) ? $game_troop.enemies : $game_troop.members
      foes.each { |enemy| enemy.hp = 0 if enemy && enemy.respond_to?(:hp=) }
      scene = defined?($scene) ? $scene : nil
      if scene && scene.respond_to?(:judge, true)
        scene.send(:judge)
        "Battle victory requested"
      elsif scene && scene.respond_to?(:judge_win_loss, true)
        scene.send(:judge_win_loss)
        "Battle victory requested"
      else
        "Enemies defeated. Finish the current turn to complete the battle."
      end
    rescue Exception => e
      "Couldn't finish battle: #{e.class}: #{e.message}"
    end

    def force_escape
      return NEED_BATTLE unless in_battle?
      if ess? && @battle
        @battle.instance_variable_set(:@decision, 3)
        return "Escape requested"
      end
      if defined?(BattleManager) && BattleManager.respond_to?(:process_escape)
        saved_ratio = BattleManager.instance_variable_get(:@escape_ratio)
        BattleManager.instance_variable_set(:@escape_ratio, 1.0)
        begin
          BattleManager.process_escape
        ensure
          BattleManager.instance_variable_set(:@escape_ratio, saved_ratio)
        end
        return "Escape requested"
      end
      scene = defined?($scene) ? $scene : nil
      if engine == :xp && scene && scene.respond_to?(:update_phase2_escape, true)
        return finish_xp_escape(scene) ? "Escape requested" : "This game's XP battle scene doesn't expose battle_end."
      end
      if scene && scene.respond_to?(:process_escape, true)
        saved_ratio = scene.instance_variable_get(:@escape_ratio)
        scene.instance_variable_set(:@escape_ratio, 100)
        begin
          scene.send(:process_escape)
        ensure
          scene.instance_variable_set(:@escape_ratio, saved_ratio)
        end
        return "Escape requested"
      end
      "This game's battle scene doesn't expose an immediate escape method."
    rescue Exception => e
      "Couldn't escape battle: #{e.class}: #{e.message}"
    end

    # XP ends battles through Scene_Battle#battle_end(result), rather than the
    # BattleManager / process_escape entry points used by later generations.
    def finish_xp_escape(scene)
      return false unless scene && scene.respond_to?(:battle_end, true)
      begin
        $game_system.se_play($data_system.escape_se) if $game_system && $data_system && $data_system.respond_to?(:escape_se)
      rescue Exception
      end
      begin
        $game_system.bgm_play($game_temp.map_bgm) if $game_system && $game_temp && $game_temp.respond_to?(:map_bgm)
      rescue Exception
      end
      scene.send(:battle_end, 1)
      true
    rescue Exception
      false
    end

    def open_native_screen(kind)
      if ess?
        if kind == :save
          return NOPE unless defined?(PokemonSaveScene) && defined?(PokemonSave)
          scene = PokemonSaveScene.new
          PokemonSave.new(scene).pbSaveScreen
        else
          return NOPE unless defined?(PokemonLoadScene) && defined?(PokemonLoad)
          scene = PokemonLoadScene.new
          PokemonLoad.new(scene).pbStartLoadScreen
        end
      elsif engine == :ace
        return NOPE unless defined?(SceneManager)
        scene_class = kind == :save ? Scene_Save : Scene_Load
        return NOPE unless scene_class
        SceneManager.call(scene_class)
      else
        scene_class = kind == :save ? Scene_Save : Scene_Load
        return NOPE unless scene_class
        $scene = if engine == :vx
                   kind == :save ? scene_class.new(true, false, false) : scene_class.new(false, false, false)
                 else
                   scene_class.new
                 end
      end
      "ok"
    rescue Exception => e
      "Couldn't open the game's #{kind} screen: #{e.class}: #{e.message}"
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
          maximum = a.respond_to?(:max_level) ? a.max_level.to_i : 99
          result = set_actor_level(a, maximum)
          return result if result.is_a?(String)
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
        foes.each do |enemy|
          next unless enemy && enemy.respond_to?(:hp=)
          enemy.hp = 0
          enemy.refresh if enemy.respond_to?(:refresh)
        end
        "Enemies defeated. Finish the current turn to win."
      else
        NOPE
      end
    end

    def kill_enemies
      return NEED_BATTLE unless in_battle?
      foes = if ess? && @battle
               @battle.battlers.compact.select { |battler| foe?(@battle, battler.index) && !battler.fainted? }
             elsif $game_troop && $game_troop.respond_to?(:enemies)
               $game_troop.enemies
             elsif $game_troop
               $game_troop.members
             else
               []
             end
      foes.each do |enemy|
        if ess? && enemy.respond_to?(:pbReduceHP)
          enemy.pbReduceHP(enemy.hp, false)
          enemy.pbFaint(true) if enemy.respond_to?(:pbFaint)
        elsif enemy.respond_to?(:hp=)
          enemy.hp = 0
          enemy.refresh if enemy.respond_to?(:refresh)
        end
      end
      "Enemies defeated"
    rescue Exception => e
      "Couldn't defeat enemies: #{e.class}: #{e.message}"
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
    def load_native_window_layer
      return true if respond_to?(:open_native_window)
      return false if @native_window_layer_load_attempted
      path = (ENV['TYRANOR_NATIVE_CHEAT_SCRIPT'] rescue '').to_s
      return false if path.empty? || !File.file?(path)
      @native_window_layer_load_attempted = true
      load(path)
      loaded = respond_to?(:open_native_window)
      AnyPlayLog.write("RGSS native cheat window layer loaded lazily") if loaded
      loaded
    rescue Exception => e
      @native_window_layer_load_attempted = true
      AnyPlayLog.error("RGSS native cheat window lazy load failed: #{e.class}: #{e.message}") rescue nil
      false
    end

    def frame
      @tick += 1
      poll if (@tick % 6) == 0
      load_native_window_layer
      install_native_window_hook if respond_to?(:install_native_window_hook)
      install_hooks
      apply unless @on.empty?
    rescue Exception => e
      @errors += 1
      # A broken optional cheat must not stop command polling; otherwise the
      # panel can no longer query capabilities or turn that cheat back off.
      if @errors == 1 || (@errors % 120) == 0
        AnyPlayLog.error("cheat frame error (#{@errors}): #{e.class}: #{e.message}") rescue nil
      end
    end

    def poll
      return unless File.exist?(CMD)
      txt = (File.read(CMD) rescue nil)
      File.delete(CMD) rescue nil
      return if txt.nil? || txt.empty?
      txt = txt.dup
      txt.force_encoding("UTF-8") if txt.respond_to?(:force_encoding)
      id, cmd, a, b = txt.chomp.split("\t", 4)
      reply = handle(cmd.to_s, a.to_s, b.to_s)
      File.open(REPLY + ".tmp", "wb") { |f| f.write("#{id}\t#{reply}") }
      File.rename(REPLY + ".tmp", REPLY)
      if cmd == 'list' && a == 'capabilities'
        AnyPlayLog.write("cheat capability handshake: engine=#{engine} response_bytes=#{reply.to_s.bytesize}")
      end
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
        maximum = ess_money_limit
        t.money = maximum if t.money < maximum
      end
      maintain_coins if on?('coins')
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
      if on?('hpmp') || on?('tp') || on?('status') || on?('points')
        actors.each do |a|
          if on?('hpmp')
            a.hp = maxhp_of(a)
            mp = maxmp_of(a)
            if a.respond_to?(:mp=) then a.mp = mp
            elsif a.respond_to?(:sp=) then a.sp = mp end
          end
          a.tp = a.max_tp if on?('tp') && a.respond_to?(:tp=) && a.respond_to?(:max_tp)
          maintain_custom_points(a) if on?('points')
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
        maximum = rpgm_gold_limit
        set_rpgm_gold(maximum) if rpgm_gold_value != maximum
      end
      keep_items if on?('items')
      common_map_cheats
    end

    def common_map_cheats
      if on?('noclip') && $game_player
        unless @through_forced
          @old_player_through = player_through($game_player)
          @through_forced = set_player_through($game_player, true)
        else
          set_player_through($game_player, true)
        end
      end
      if on?('noEncounter')
        save_encounter_state
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
        %w[items weapons armors].each do |kind|
          inventory = $game_party.instance_variable_get("@#{kind}")
          next unless inventory.is_a?(Hash)
          snapshot = (@item_snap[kind] ||= {})
          snapshot.each do |id, quantity|
            current = inventory[id].to_i
            inventory[id] = quantity if current < quantity
          end
          inventory.each do |id, quantity|
            quantity = quantity.to_i
            snapshot[id] = quantity if quantity > (snapshot[id] || 0)
          end
        end
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
            is_player = ($game_player && self.equal?($game_player))
            speed_this = (TyranorCheats.speed_on? && is_player) || (TyranorCheats.on?('eventSpeed') && !is_player)
            if speed_this
              m = is_player ? TyranorCheats.speed.to_f : 4.0
              m = 1.0 if m <= 0
              # Games store the walking speed in different ways, so scale
              # whichever one this game actually uses, for this frame only.
              if instance_variable_defined?(:@move_speed_real) && @move_speed_real.is_a?(Numeric)
                key = :@move_speed_real; saved = @move_speed_real; scaled = saved * m
              elsif instance_variable_defined?(:@move_time) && @move_time.is_a?(Numeric)
                key = :@move_time; saved = @move_time; scaled = saved / m
              elsif @move_speed.is_a?(Numeric)
                key = :@move_speed; saved = @move_speed
                bonus = is_player ? TyranorCheats.speed_bonus : (Math.log(m) / Math.log(2))
                scaled = saved + bonus
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

      if engine == :xp && defined?(Scene_File) && class_method?(Scene_File, :initialize)
        Scene_File.class_eval do
          alias_method :__ap_cheat_scene_file_initialize, :initialize
          def initialize(*args)
            __ap_cheat_scene_file_initialize(*args)
            index = TyranorCheats.consume_save_slot_index
            @file_index = index unless index.nil?
          end
        end
      end

      if (engine == :vx || engine == :ace) && defined?(Scene_File) && class_method?(Scene_File, :start)
        Scene_File.class_eval do
          alias_method :__ap_cheat_scene_file_start, :start
          def start(*args)
            __ap_cheat_scene_file_start(*args)
            index = TyranorCheats.consume_save_slot_index
            unless index.nil?
              @index = index
              @file_index = index
              if instance_variable_defined?(:@savefile_windows)
                windows = instance_variable_get(:@savefile_windows)
                if windows.respond_to?(:each_with_index)
                  windows.each_with_index do |window, window_index|
                    window.selected = (window_index == index) if window && window.respond_to?(:selected=)
                  end
                end
              end
            end
          end
        end
      end

      if engine != :essentials && defined?(Game_Battler) && Game_Battler.method_defined?(:hp=)
        Game_Battler.class_eval do
          alias_method :__ap_cheat_hp_set, :hp=
          def hp=(value)
            current = (hp rescue 0).to_i
            lower = value.to_i < current
            attacker = TyranorCheats.xp_critical_attacker
            if lower && attacker && TyranorCheats.on?('crit') &&
                TyranorCheats.party_battler?(attacker) && TyranorCheats.enemy_battler?(self) &&
                @damage.is_a?(Numeric) && @damage.to_i > 0 && !@critical
              lost = current - value.to_i
              if lost > 0
                @critical = true
                @damage = @damage.to_i + lost
                value = current - (lost * 2)
              end
            end
            if lower && TyranorCheats.on?('invincible') && TyranorCheats.party_battler?(self)
              value = current
            elsif lower && TyranorCheats.on?('oneHit') && TyranorCheats.enemy_battler?(self)
              value = 0
            end
            __ap_cheat_hp_set(value)
          end
        end
      end

      # RPG Maker XP calculates damage inside its effect methods instead of
      # exposing the later generations' make_attack_damage_value hook. Carry
      # the attacking actor through the effect so hp= can double positive
      # damage before XP applies it, keeping the critical flag, damage popup,
      # HP clamp, and state updates in sync.
      if engine == :xp && defined?(Game_Battler) && class_method?(Game_Battler, :attack_effect)
        Game_Battler.class_eval do
          alias_method :__ap_cheat_xp_attack_effect, :attack_effect
          def attack_effect(attacker, *args)
            TyranorCheats.with_xp_critical_attacker(attacker) do
              __ap_cheat_xp_attack_effect(attacker, *args)
            end
          end

          if method_defined?(:skill_effect)
            alias_method :__ap_cheat_xp_skill_effect, :skill_effect
            def skill_effect(user, skill, *args)
              TyranorCheats.with_xp_critical_attacker(user) do
                __ap_cheat_xp_skill_effect(user, skill, *args)
              end
            end
          end
        end
      end

      if engine == :xp && defined?(Scene_Battle) && class_method?(Scene_Battle, :make_item_action_result)
        Scene_Battle.class_eval do
          alias_method :__ap_cheat_xp_item_action_result, :make_item_action_result
          def make_item_action_result(*args)
            TyranorCheats.with_xp_critical_attacker(@active_battler) do
              __ap_cheat_xp_item_action_result(*args)
            end
          end
        end
      end

      if defined?(Game_Interpreter) && class_method?(Game_Interpreter, :update)
        Game_Interpreter.class_eval do
          alias_method :__ap_cheat_interpreter_update, :update
          def update(*args)
            if TyranorCheats.on?('eventSpeed')
              @wait_count = 0 if @wait_count && @wait_count.to_i > 0
              @wait_mode = "" if instance_variable_defined?(:@wait_mode)
            end
            __ap_cheat_interpreter_update(*args)
          end
        end
      end

      if defined?(Window_Message)
        Window_Message.class_eval do
          if TyranorCheats.class_method?(Window_Message, :update_message)
            alias_method :__ap_cheat_update_message, :update_message
            def update_message(*args)
              if TyranorCheats.on?('fastText') || TyranorCheats.on?('msgSkip')
                @show_fast = true if instance_variable_defined?(:@show_fast)
                @wait_count = 0 if instance_variable_defined?(:@wait_count)
              end
              __ap_cheat_update_message(*args)
            end
          end
          if TyranorCheats.class_method?(Window_Message, :update_input)
            alias_method :__ap_cheat_update_input, :update_input
            def update_input(*args)
              if TyranorCheats.on?('msgSkip') && @pause && !TyranorCheats.message_choice_active?
                @pause = false
                (terminate_message rescue nil)
                return true
              end
              __ap_cheat_update_input(*args)
            end
          end

          # XP displays the whole message at once, then waits in update while
          # @contents_showing is true. Its stock API has no update_input hook.
          if TyranorCheats.engine == :xp &&
              TyranorCheats.class_method?(Window_Message, :update) &&
              TyranorCheats.class_method?(Window_Message, :terminate_message)
            alias_method :__ap_cheat_xp_message_update, :update
            def update(*args)
              if TyranorCheats.on?('msgSkip') && @contents_showing && @pause &&
                  !TyranorCheats.message_choice_active? && !@input_number_window
                terminate_message
                return
              end
              __ap_cheat_xp_message_update(*args)
            end
          end

          # VX pauses through input_pause after update_message finishes a page.
          # Keep page continuation logic and never consume a choice prompt.
          if TyranorCheats.engine == :vx && TyranorCheats.class_method?(Window_Message, :input_pause)
            alias_method :__ap_cheat_vx_input_pause, :input_pause
            def input_pause(*args)
              if TyranorCheats.on?('msgSkip') && !TyranorCheats.message_choice_active?
                self.pause = false if respond_to?(:pause=)
                if @text && !@text.empty?
                  max_lines = (self.class.const_get(:MAX_LINE) rescue nil)
                  new_page if instance_variable_defined?(:@line_count) && max_lines && @line_count >= max_lines.to_i
                else
                  terminate_message if respond_to?(:terminate_message)
                end
                return
              end
              __ap_cheat_vx_input_pause(*args)
            end
          end

          # Ace uses a Fiber for each message. Fast display skips per-character
          # yields; input_pause and timed waits are the only pause points to bypass.
          if TyranorCheats.engine == :ace && TyranorCheats.class_method?(Window_Message, :update_show_fast)
            alias_method :__ap_cheat_ace_update_show_fast, :update_show_fast
            def update_show_fast(*args)
              result = __ap_cheat_ace_update_show_fast(*args)
              @show_fast = true if TyranorCheats.on?('fastText') || TyranorCheats.on?('msgSkip')
              result
            end
          end
          if TyranorCheats.engine == :ace && TyranorCheats.class_method?(Window_Message, :input_pause)
            alias_method :__ap_cheat_ace_input_pause, :input_pause
            def input_pause(*args)
              # Choice lists use the message window's pause to wait for a
              # selection. Fast-forward may dismiss ordinary message pauses,
              # but must leave this wait to the game so the player can choose.
              if TyranorCheats.on?('msgSkip') && !TyranorCheats.message_choice_active?
                self.pause = false if respond_to?(:pause=)
                return
              end
              __ap_cheat_ace_input_pause(*args)
            end
          end
          if TyranorCheats.engine == :ace && TyranorCheats.class_method?(Window_Message, :wait)
            alias_method :__ap_cheat_ace_wait, :wait
            def wait(*args)
              return if TyranorCheats.on?('msgSkip')
              __ap_cheat_ace_wait(*args)
            end
          end
        end
      end

      # Pokémon Essentials uses Window_AdvancedTextPokemon and advances its
      # own formatted-character cursor. Apply the fast frame skip only for the
      # duration of an update, clear scripted waits, and resume input-pause
      # markers without touching the separate command-choice flow.
      if engine == :essentials && defined?(Window_AdvancedTextPokemon) &&
          class_method?(Window_AdvancedTextPokemon, :update)
        Window_AdvancedTextPokemon.class_eval do
          alias_method :__ap_cheat_ess_message_update, :update
          def update(*args)
            return __ap_cheat_ess_message_update(*args) unless TyranorCheats.on?('msgSkip')
            previous_frameskip = instance_variable_get(:@frameskip)
            instance_variable_set(:@frameskip, -999)
            instance_variable_set(:@waitcount, 0)
            begin
              result = __ap_cheat_ess_message_update(*args)
              resume if respond_to?(:pausing?) && pausing? && respond_to?(:resume)
              result
            ensure
              instance_variable_set(:@frameskip, previous_frameskip)
            end
          end
        end
      end

      if engine == :ace && defined?(Game_Battler) && class_method?(Game_Battler, :item_cri)
        Game_Battler.class_eval do
          alias_method :__ap_cheat_item_cri, :item_cri
          def item_cri(user, item)
            return 1.0 if TyranorCheats.on?('crit') &&
              TyranorCheats.party_battler?(user) && TyranorCheats.enemy_battler?(self)
            __ap_cheat_item_cri(user, item)
          end
        end
      end

      if (engine == :xp || engine == :vx) && defined?(Game_Battler) && class_method?(Game_Battler, :make_attack_damage_value)
        Game_Battler.class_eval do
          alias_method :__ap_cheat_attack_damage_value, :make_attack_damage_value
          def make_attack_damage_value(attacker, *args)
            result = __ap_cheat_attack_damage_value(attacker, *args)
            if TyranorCheats.on?('crit') && TyranorCheats.party_battler?(attacker) &&
                TyranorCheats.enemy_battler?(self) && @hp_damage.to_i > 0 && !@critical
              @critical = true
              multiplier = TyranorCheats.engine == :xp ? 2 : 3
              @hp_damage = (@hp_damage.to_f * multiplier).to_i
            end
            result
          end
        end
      end

      if (engine == :xp || engine == :vx) && defined?(Game_Battler) && class_method?(Game_Battler, :make_obj_damage_value)
        Game_Battler.class_eval do
          alias_method :__ap_cheat_obj_damage_value, :make_obj_damage_value
          def make_obj_damage_value(user, obj, *args)
            result = __ap_cheat_obj_damage_value(user, obj, *args)
            if TyranorCheats.on?('crit') && TyranorCheats.party_battler?(user) &&
                TyranorCheats.enemy_battler?(self) && !@critical
              multiplier = TyranorCheats.engine == :xp ? 2 : 3
              hp_damage = instance_variable_get(:@hp_damage).to_i
              mp_damage = instance_variable_get(:@mp_damage).to_i
              if hp_damage > 0 || mp_damage > 0
                @critical = true
                @hp_damage = hp_damage * multiplier if hp_damage > 0
                @mp_damage = mp_damage * multiplier if mp_damage > 0
              end
            end
            result
          end
        end
      end

      # VX routes both skill availability checks and battle consumption through
      # Game_Battler#calc_mp_cost, so returning zero there keeps the original
      # battle flow and cost modifiers intact.
      if engine == :vx && defined?(Game_Battler) && class_method?(Game_Battler, :calc_mp_cost)
        Game_Battler.class_eval do
          alias_method :__ap_cheat_calc_mp_cost, :calc_mp_cost
          def calc_mp_cost(skill, *args)
            return 0 if TyranorCheats.on?('freeSkill') && TyranorCheats.party_battler?(self)
            __ap_cheat_calc_mp_cost(skill, *args)
          end
        end
      end

      if engine == :xp
        if defined?(RPG::Skill) && class_method?(RPG::Skill, :sp_cost)
          RPG::Skill.class_eval do
            alias_method :__ap_cheat_sp_cost, :sp_cost
            def sp_cost(*args)
              return 0 if TyranorCheats.free_skill_cost_scope?
              __ap_cheat_sp_cost(*args)
            end
          end
        end

        if defined?(Game_Battler) && class_method?(Game_Battler, :skill_can_use?)
          Game_Battler.class_eval do
            alias_method :__ap_cheat_skill_can_use, :skill_can_use?
            def skill_can_use?(*args)
              TyranorCheats.with_free_skill_cost(self) do
                __ap_cheat_skill_can_use(*args)
              end
            end
          end
          preserve_keyword_forwarding(Game_Battler, :skill_can_use?)
        end

        if defined?(Scene_Battle) && class_method?(Scene_Battle, :make_skill_action_result)
          Scene_Battle.class_eval do
            alias_method :__ap_cheat_make_skill_action_result, :make_skill_action_result
            def make_skill_action_result(*args)
              TyranorCheats.with_free_skill_cost(@active_battler) do
                __ap_cheat_make_skill_action_result(*args)
              end
            end
          end
          preserve_keyword_forwarding(Scene_Battle, :make_skill_action_result)
        end

        # The stock XP menu executes skills in Scene_Skill and also charges the
        # RPG::Skill SP cost from there. Scope the override to the menu actor.
        if defined?(Scene_Skill) && class_method?(Scene_Skill, :update)
          Scene_Skill.class_eval do
            alias_method :__ap_cheat_scene_skill_update, :update
            def update(*args)
              TyranorCheats.with_free_skill_cost(@actor) do
                __ap_cheat_scene_skill_update(*args)
              end
            end
          end
          preserve_keyword_forwarding(Scene_Skill, :update)
        end
      end

      if engine == :ace && defined?(Game_BattlerBase)
        Game_BattlerBase.class_eval do
          if method_defined?(:skill_mp_cost)
            alias_method :__ap_cheat_skill_mp_cost, :skill_mp_cost
            def skill_mp_cost(skill)
              return 0 if TyranorCheats.on?('freeSkill') && TyranorCheats.party_battler?(self)
              __ap_cheat_skill_mp_cost(skill)
            end
          end
          if method_defined?(:skill_tp_cost)
            alias_method :__ap_cheat_skill_tp_cost, :skill_tp_cost
            def skill_tp_cost(skill)
              return 0 if TyranorCheats.on?('freeSkill') && TyranorCheats.party_battler?(self)
              __ap_cheat_skill_tp_cost(skill)
            end
          end
        end
      end

      if (engine == :ace || engine == :xp || engine == :vx) && defined?(BattleManager) && BattleManager.respond_to?(:process_escape)
        BattleManager.instance_eval do
          alias_method :__ap_cheat_process_escape, :process_escape
          define_method(:process_escape) do |*args|
            if TyranorCheats.on?('escape')
              saved = @escape_ratio
              @escape_ratio = 1.0
              begin
                return __ap_cheat_process_escape(*args)
              ensure
                @escape_ratio = saved
              end
            end
            __ap_cheat_process_escape(*args)
          end
        end
      end

      if (engine == :xp || engine == :vx) && defined?(Scene_Battle) && class_method?(Scene_Battle, :process_escape)
        Scene_Battle.class_eval do
          alias_method :__ap_cheat_process_escape, :process_escape
          def process_escape(*args)
            return __ap_cheat_process_escape(*args) unless TyranorCheats.on?('escape')
            saved_ratio = instance_variable_get(:@escape_ratio)
            instance_variable_set(:@escape_ratio, 100)
            begin
              __ap_cheat_process_escape(*args)
            ensure
              instance_variable_set(:@escape_ratio, saved_ratio)
            end
          end
        end
      end

      if engine == :xp && defined?(Scene_Battle) && class_method?(Scene_Battle, :update_phase2_escape)
        Scene_Battle.class_eval do
          alias_method :__ap_cheat_update_phase2_escape, :update_phase2_escape
          def update_phase2_escape(*args)
            if TyranorCheats.on?('escape') && $game_temp && $game_temp.battle_can_escape
              return TyranorCheats.finish_xp_escape(self)
            end
            __ap_cheat_update_phase2_escape(*args)
          end
        end
      end

      return unless engine == :essentials

      # Essentials versions differ between pbCanEncounter? and
      # encounter_triggered?; the sample games use pbCanEncounter?.
      if defined?(PokemonEncounters)
        PokemonEncounters.class_eval do
          if method_defined?(:pbCanEncounter?)
            alias_method :__ap_cheat_can_encounter, :pbCanEncounter?
            def pbCanEncounter?(*a)
              return false if TyranorCheats.on?('noEncounter')
              __ap_cheat_can_encounter(*a)
            end
          end
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
      move_c = ess_move_class
      battler_c = ess_battler_class
      battle_c = ess_battle_class

      if move_c && class_method?(move_c, :pbCritRate?)
        move_c.class_eval do
          alias_method :__ap_cheat_critrate, :pbCritRate?
          def pbCritRate?(*a)
            attacker = a[0]
            if TyranorCheats.on?('crit') && attacker && @battle && !TyranorCheats.foe?(@battle, attacker.index)
              return 3 # Sample move scripts index the guaranteed [24, 8, 2, 1] ratio.
            end
            __ap_cheat_critrate(*a)
          end
        end
      elsif move_c && class_method?(move_c, :pbIsCritical?)
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
          if TyranorCheats.class_method?(self, :pbReduceHP)
            alias_method :__ap_cheat_reduce, :pbReduceHP
            def pbReduceHP(amt, *rest)
              foe = TyranorCheats.foe?(@battle, @index)
              return 0 if !foe && TyranorCheats.on?('invincible')
              amt = self.hp if foe && TyranorCheats.on?('oneHit') && amt > 0
              __ap_cheat_reduce(amt, *rest)
            end
          end
          if TyranorCheats.class_method?(self, :pbReducePP)
            alias_method :__ap_cheat_pp, :pbReducePP
            def pbReducePP(move, *args)
              return true if TyranorCheats.on?('freeSkill') && !TyranorCheats.foe?(@battle, @index)
              __ap_cheat_pp(move, *args)
            end
          end
        end
        preserve_keyword_forwarding(battler_c, :pbReduceHP)
        preserve_keyword_forwarding(battler_c, :pbReducePP)
      end

      if battle_c
        battle_c.class_eval do
          if TyranorCheats.class_method?(self, :pbCommandPhase)
            alias_method :__ap_cheat_cmd, :pbCommandPhase
            def pbCommandPhase(*a)
              TyranorCheats.battle = self
              __ap_cheat_cmd(*a)
            end
          end
          if TyranorCheats.class_method?(self, :pbEndOfBattle)
            alias_method :__ap_cheat_end, :pbEndOfBattle
            def pbEndOfBattle(*a)
              begin
                __ap_cheat_end(*a)
              ensure
                TyranorCheats.battle = nil
              end
            end
          end
          if TyranorCheats.class_method?(self, :pbCanRun?)
            alias_method :__ap_cheat_canrun, :pbCanRun?
            def pbCanRun?(*args)
              idx = args[0]
              if TyranorCheats.on?('escape') && !@opponent && !(@cantescape || @cantRun) && !TyranorCheats.foe?(self, idx)
                return true
              end
              __ap_cheat_canrun(*args)
            end
          end
          if TyranorCheats.class_method?(self, :pbRun)
            alias_method :__ap_cheat_run, :pbRun
            def pbRun(*args)
              idx = args[0]
              if TyranorCheats.on?('escape') && !@opponent && !(@cantescape || @cantRun) && !TyranorCheats.foe?(self, idx)
                pbSEPlay("Battle flee") rescue nil
                pbDisplayPaused(_INTL("Got away safely!"))
                @decision = 3
                return 1
              end
              __ap_cheat_run(*args)
            end
          end
        end
      end
    end
  end
end
TYRANOR_CHEATS_LAYER
  AnyPlayLog.write("cheat layer initialized")
rescue Exception => e
  detail = (e.backtrace || []).first(8).join("\n  ") rescue ""
  AnyPlayLog.error("cheat layer disabled during preload: #{e.class}: #{e.message}\n  #{detail}") rescue nil
  # Keep the public frame hook alive so the game's input, render and perf
  # wrappers can continue. Reply to cheat requests with a clear failure instead
  # of leaving the Android panel waiting for a timeout.
  module TyranorCheats
    DIR = "/data/data/com.tyranor.runtime.rgss/cache" unless const_defined?(:DIR)
    CMD = DIR + "/tyranor_cheat_cmd.txt" unless const_defined?(:CMD)
    REPLY = DIR + "/tyranor_cheat_reply.txt" unless const_defined?(:REPLY)
    def self.engine; :unknown; end
    def self.frame
      return unless File.exist?(CMD)
      txt = (File.read(CMD) rescue "")
      File.delete(CMD) rescue nil
      id = txt.to_s.split("\t", 2)[0].to_s
      File.open(REPLY + ".tmp", "wb") do |f|
        f.write("#{id}\tCheat layer unavailable; see TYN RGSS diagnostics.")
      end
      File.rename(REPLY + ".tmp", REPLY)
    rescue Exception => fallback_error
      AnyPlayLog.error("cheat fallback reply failed: #{fallback_error.class}: #{fallback_error.message}") rescue nil
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
              # TilemapLoader#setClass copies terrain_tags onto the replacement tilemap.
              # The native class does not otherwise need this data, but the setter must
              # exist so the fallback can complete.
              unless SynchronizedTilemap.method_defined?(:terrain_tags=)
                SynchronizedTilemap.class_eval { attr_accessor :terrain_tags }
              end
              begin
                setClass(SynchronizedTilemap)
              rescue StandardError => e
                AnyPlayLog.write("native tilemap switch failed: #{e.class}: #{e.message}")
              end
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
    where = (caller(2) || []).slice(0, 3).map { |c| AnyPlayLog.label(c) }.join(" < ") if script_ms > 120
    AnyPlayLog.watch
    AnyPlayLog.runtime_checkpoint
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
                abortRubyStartup("Android RGSS compatibility prelude", "embedded compatibility code", preState);
                return;
            } else if (tynVerboseRuntimeLogs())
                Debug() << "[ruby] Android RGSS compatibility prelude complete";
        }
    }
#endif
    
    std::string &customScript = conf.customScript;
    if (!customScript.empty())
        runCustomScript(customScript, "custom");
    else
        runRMXPScripts(btData);
    
#if RAPI_FULL > 187
    VALUE exc = rb_errinfo();
#else
    VALUE exc = rb_gv_get("$!");
#endif
    if (!NIL_P(exc) && !rb_obj_is_kind_of(exc, rb_eSystemExit)) {
        Debug() << "[ruby] unhandled exception reached the engine error handler";
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
