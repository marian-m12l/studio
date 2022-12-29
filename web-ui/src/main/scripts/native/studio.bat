@echo off

set CWD=%~dp0

:: Override env vars
set STUDIO_HOME=%CWD%

:: batch args as command args
%CWD%\studio*runner.exe %*
