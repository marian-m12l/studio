@echo off

set CWD=%~dp0

:: Override env vars
set STUDIO_HOME=%CWD%

:: batch args as java system properties
java %* -jar %CWD%/quarkus-run.jar
