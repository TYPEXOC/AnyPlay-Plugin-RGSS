/*
** debugwriter.h
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

#ifndef DEBUGWRITER_H
#define DEBUGWRITER_H

#include <iostream>
#include <sstream>
#include <chrono>
#include <cstdlib>
#include <ctime>
#include <iomanip>
#include <string>
#include <vector>
#include <cerrno>

#ifdef __ANDROID__
#include <android/log.h>
#include <fcntl.h>
#include <sys/file.h>
#include <unistd.h>

static void writeTynDiagnosticBytes(int fd, const char *data, size_t size)
{
	size_t offset = 0;
	while (offset < size) {
		const ssize_t written = ::write(fd, data + offset, size - offset);
		if (written > 0) {
			offset += static_cast<size_t>(written);
			continue;
		}
		if (written < 0 && errno == EINTR) continue;
		break;
	}
}

static void appendTynRuntimeDiagnostic(const std::string &message)
{
	const char *sessionDir = std::getenv("TYN_RGSS_DIAGNOSTICS_DIR");
	if (!sessionDir || !*sessionDir) return;

	using namespace std::chrono;
	const system_clock::time_point now = system_clock::now();
	const std::time_t wallTime = system_clock::to_time_t(now);
	std::tm localTime;
	if (!localtime_r(&wallTime, &localTime)) return;
	const long millisecondsPart = duration_cast<milliseconds>(now.time_since_epoch()).count() % 1000;
	std::string redactedMessage = message;
	const char *gameFolder = std::getenv("TYN_RGSS_GAME_FOLDER_FOR_LOG");
	const std::string privatePaths[] = {gameFolder ? gameFolder : "", sessionDir};
	for (const std::string &privatePath : privatePaths) {
		if (privatePath.empty()) continue;
		std::string::size_type position = 0;
		while ((position = redactedMessage.find(privatePath, position)) != std::string::npos) {
			redactedMessage.replace(position, privatePath.size(), privatePath == sessionDir ? "<session>" : "<game-folder>");
			position += privatePath == sessionDir ? 9 : 13;
		}
	}

	std::ostringstream line;
	line << std::put_time(&localTime, "%Y-%m-%d %H:%M:%S")
		 << '.' << std::setw(3) << std::setfill('0') << millisecondsPart
		 << " " << redactedMessage << '\n';
	const std::string path = std::string(sessionDir) + "/NativeRuntime.txt";
	std::string record = line.str();
	static const off_t maxFileBytes = 4 * 1024 * 1024;
	static const std::string truncationMarker = "[Older native runtime entries trimmed to keep this session bounded]\n";
	const int fd = ::open(path.c_str(), O_WRONLY | O_APPEND | O_CREAT, 0600);
	if (fd < 0) return;
	if (::flock(fd, LOCK_EX) != 0) {
		::close(fd);
		return;
	}
	const off_t currentBytes = ::lseek(fd, 0, SEEK_END);
	if (currentBytes < 0) {
		::flock(fd, LOCK_UN);
		::close(fd);
		return;
	}
	if (currentBytes + static_cast<off_t>(record.size()) > maxFileBytes) {
		const size_t available = static_cast<size_t>(maxFileBytes) - truncationMarker.size();
		if (record.size() > available) record.erase(0, record.size() - available);
		if (::ftruncate(fd, 0) != 0 || ::lseek(fd, 0, SEEK_SET) < 0) {
			::flock(fd, LOCK_UN);
			::close(fd);
			return;
		}
		writeTynDiagnosticBytes(fd, truncationMarker.data(), truncationMarker.size());
	}
	writeTynDiagnosticBytes(fd, record.data(), record.size());
	::flock(fd, LOCK_UN);
	::close(fd);
}
#endif


/* A cheap replacement for qDebug() */

class Debug
{
public:
	Debug()
	{
		buf << std::boolalpha;
	}

	template<typename T>
	Debug &operator<<(const T &t)
	{
		buf << t;
		buf << " ";

		return *this;
	}

	template<typename T>
	Debug &operator<<(const std::vector<T> &v)
	{
		for (size_t i = 0; i < v.size(); ++i)
			buf << v[i] << " ";

		return *this;
	}

	~Debug()
	{
#ifdef __ANDROID__
		const std::string message = buf.str();
		__android_log_write(ANDROID_LOG_DEBUG, "mkxp", message.c_str());
		appendTynRuntimeDiagnostic(message);
#else
		std::cerr << buf.str() << std::endl;
#endif
	}

private:
	std::stringstream buf;
};

#endif // DEBUGWRITER_H
