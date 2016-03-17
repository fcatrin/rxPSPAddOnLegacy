@echo off
rem // Copyright (c) 2012- PPSSPP Project.

rem // This program is free software: you can redistribute it and/or modify
rem // it under the terms of the GNU General Public License as published by
rem // the Free Software Foundation, version 2.0 or later versions.

rem // This program is distributed in the hope that it will be useful,
rem // but WITHOUT ANY WARRANTY; without even the implied warranty of
rem // MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
rem // GNU General Public License 2.0 for more details.

rem // A copy of the GPL 2.0 should have been included with the program.
rem // If not, see http://www.gnu.org/licenses/

rem // Official git repository and contact information can be found at
rem // https://github.com/hrydgard/ppsspp and http://www.ppsspp.org/.

rem // This file automatically generates two files during build:
rem //   - git-version.cpp (contains the git version string for display.)
rem //   - Windows/win-version.h (contains git version for version.rc.)
rem //
rem // If git is not installed, "unknown" is the version.

setlocal ENABLEDELAYEDEXPANSION

set GIT_VERSION_FILE=%~p0..\git-version.cpp
set WIN_VERSION_FILE=%~p0.\win-version.h
set GIT_MISSING=0

if not defined GIT (
	set GIT="git"
)
call %GIT% describe --always > NUL 2> NUL
if errorlevel 1 (
	echo Git not on path, trying default Msysgit paths...
	set GIT="%ProgramFiles(x86)%\Git\bin\git.exe"
	call !GIT! describe > NUL 2> NUL
	if errorlevel 1 (
		echo !GIT!
		set GIT="%ProgramFiles%\Git\bin\git.exe"
		call !GIT! describe > NUL 2> NUL
		if errorlevel 1 (
			set GIT="%ProgramW6432%\Git\bin\git.exe"
		)
	)
)

call %GIT% describe --always > NUL 2> NUL
if errorlevel 1 (
	echo Git not on path, trying GitHub Desktop..
	rem // Cheating using short filenames.
	set GIT="%USERPROFILE%\AppData\Local\GitHub\PORTAB~1\bin\git.exe"
	call !GIT! describe > NUL 2> NUL
	if errorlevel 1 (
		set GIT="%USERPROFILE%\AppData\Local\GitHub\PORTAB~2\bin\git.exe"
	)
)

call %GIT% describe --always > NUL 2> NUL
if errorlevel 1 (
	set GIT_MISSING=1
)

if not "%GIT_MISSING%" == "1" (
	for /F %%I in ('call %GIT% describe --always') do set GIT_VERSION=%%I
)

if exist "%GIT_VERSION_FILE%" (
	rem // Skip updating the file if PPSSPP_GIT_VERSION_NO_UPDATE is 1.
	findstr /B /C:"#define PPSSPP_GIT_VERSION_NO_UPDATE 1" "%GIT_VERSION_FILE%" > NUL
	if not errorlevel 1 (
		goto gitdone
	)
)

if "%GIT_MISSING%" == "1" (
	echo WARNING: Unable to update git-version.cpp, git not found.
	echo If you don't want to add it to your path, set the GIT environment variable.

	echo // This is a generated file, by git-version-gen.cmd. > "%GIT_VERSION_FILE%"
	echo. >> "%GIT_VERSION_FILE%"
	echo // ERROR: Unable to determine version - git not on path. > "%GIT_VERSION_FILE%"
	echo const char *PPSSPP_GIT_VERSION = "unknown"; >> "%GIT_VERSION_FILE%"
	goto gitdone
)

rem // Don't modify the file if it already has the current version.
if exist "%GIT_VERSION_FILE%" (
	findstr /C:"%GIT_VERSION%" "%GIT_VERSION_FILE%" > NUL
	if not errorlevel 1 (
		goto gitdone
	)
)

echo // This is a generated file, by git-version-gen.cmd. > "%GIT_VERSION_FILE%"
echo. >> "%GIT_VERSION_FILE%"
echo const char *PPSSPP_GIT_VERSION = "%GIT_VERSION%"; >> "%GIT_VERSION_FILE%"
echo. >> "%GIT_VERSION_FILE%"
echo // If you don't want this file to update/recompile, change to 1. >> "%GIT_VERSION_FILE%"
echo #define PPSSPP_GIT_VERSION_NO_UPDATE 0 >> "%GIT_VERSION_FILE%"

:gitdone

if exist "%WIN_VERSION_FILE%" (
	rem // Skip updating the file if PPSSPP_WIN_VERSION_NO_UPDATE is 1.
	findstr /B /C:"#define PPSSPP_WIN_VERSION_NO_UPDATE 1" "%WIN_VERSION_FILE%" > NUL
	if not errorlevel 1 (
		goto done
	)
)

if "%GIT_MISSING%" == "1" (
	echo WARNING: Unable to update Windows/win-version.h, git not found.

	echo // This is a generated file, by git-version-gen.cmd. > "%WIN_VERSION_FILE%"
	echo. >> "%WIN_VERSION_FILE%"
	echo // ERROR: Unable to determine version - git not on path. > "%WIN_VERSION_FILE%"
	echo #define PPSSPP_WIN_VERSION_STRING "unknown" > "%WIN_VERSION_FILE%"
	echo #define PPSSPP_WIN_VERSION_COMMA 0,0,0,0 > "%WIN_VERSION_FILE%"

	goto done
)

if exist "%WIN_VERSION_FILE%" (
	rem // Don't modify the file if it already has the current version.
	findstr /C:"%GIT_VERSION%" "%WIN_VERSION_FILE%" > NUL
	if not errorlevel 1 (
		goto done
	)
)

set WIN_RELEASE_VERSION=0
set WIN_BUILD_NUMBER=0
for /f "tokens=1 delims=-" %%a in ("%GIT_VERSION:~1%") do set WIN_RELEASE_VERSION=%%a
for /f "tokens=2 delims=-" %%a in ("%GIT_VERSION%") do set WIN_BUILD_NUMBER=%%a

echo // This is a generated file, by git-version-gen.cmd. > "%WIN_VERSION_FILE%"
echo // GIT_VERSION=%GIT_VERSION% >> "%WIN_VERSION_FILE%"
echo. >> "%WIN_VERSION_FILE%"
echo #define PPSSPP_WIN_VERSION_STRING "%GIT_VERSION%" >> "%WIN_VERSION_FILE%"
echo #define PPSSPP_WIN_VERSION_COMMA %WIN_RELEASE_VERSION:.=,%,%WIN_BUILD_NUMBER% >> "%WIN_VERSION_FILE%"
echo. >> "%WIN_VERSION_FILE%"
echo // If you don't want this file to update/recompile, change to 1. >> "%WIN_VERSION_FILE%"
echo #define PPSSPP_WIN_VERSION_NO_UPDATE 0 >> "%WIN_VERSION_FILE%"

:done
