@echo off

set CWD=%~dp0

:: Override env vars
set STUDIO_HOME=%CWD%

:: batch args as command args
for %%i in ("%CWD%\studio*runner.exe") do "%%~i" %*
