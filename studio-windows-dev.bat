@echo off
REM Studio Launcher for Development Build
REM This script launches the locally built version with YouTube import feature

echo Starting Lunii Studio with YouTube Import...
echo.

set STUDIO_PATH=%~dp0
set DOT_STUDIO="%UserProfile%\.studio"

REM Make sure the .studio subdirectories exist
if not exist %DOT_STUDIO%\agent\* mkdir %DOT_STUDIO%\agent
if not exist %DOT_STUDIO%\db\* mkdir %DOT_STUDIO%\db
if not exist %DOT_STUDIO%\library\* mkdir %DOT_STUDIO%\library

REM Check if the JAR exists
if not exist "%STUDIO_PATH%web-ui\target\studio-web-ui-0.4.3-SNAPSHOT.jar" (
    echo ERROR: Application JAR not found!
    echo Please build the project first by running:
    echo   mvn clean install
    echo.
    echo Or use an IDE like IntelliJ IDEA to build and run.
    echo.
    pause
    exit /b 1
)

REM Copy agent and metadata JARs if they exist
if exist "%STUDIO_PATH%agent\target\studio-agent-*-jar-with-dependencies.jar" (
    copy "%STUDIO_PATH%agent\target\studio-agent-*-jar-with-dependencies.jar" %DOT_STUDIO%\agent\studio-agent.jar >nul 2>&1
)
if exist "%STUDIO_PATH%metadata\target\studio-metadata-*-jar-with-dependencies.jar" (
    copy "%STUDIO_PATH%metadata\target\studio-metadata-*-jar-with-dependencies.jar" %DOT_STUDIO%\agent\studio-metadata.jar >nul 2>&1
)

REM Run the application
echo Starting web UI...
echo Open your browser to: http://localhost:8080
echo.

java -Dvertx.disableDnsResolver=true -Dvertx.options.maxWorkerExecuteTime=600000000000 -Djava.util.logging.manager=org.apache.logging.log4j.jul.LogManager -Dvertx.logger-delegate-factory-class-name=io.vertx.core.logging.Log4j2LogDelegateFactory -Dfile.encoding=UTF-8 -cp "%STUDIO_PATH%web-ui\target\studio-web-ui-0.4.3-SNAPSHOT.jar";"%STUDIO_PATH%web-ui\target\lib\*";. io.vertx.core.Launcher run studio.webui.MainVerticle

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo Application exited with error code: %ERRORLEVEL%
    pause
)