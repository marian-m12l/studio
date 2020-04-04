@echo off

set STUDIO_PATH=%~dp0
set DOT_STUDIO=%UserProfile%\.studio
set LOCAL_LUNIITHEQUE=%UserProfile%\AppData\Roaming\Luniitheque

:: Make sure the .studio subdirectories exist
if not exist %DOT_STUDIO%\agent\* mkdir %DOT_STUDIO%\agent
if not exist %DOT_STUDIO%\db\* mkdir %DOT_STUDIO%\db
if not exist %DOT_STUDIO%\lib\* mkdir %DOT_STUDIO%\lib
if not exist %DOT_STUDIO%\library\* mkdir %DOT_STUDIO%\library

:: Copy Luniistore JARs if needed
if not exist %DOT_STUDIO%\lib\lunii-java-util.jar copy %LOCAL_LUNIITHEQUE%\lib\lunii-java-util.jar %DOT_STUDIO%\lib\
if not exist %DOT_STUDIO%\lib\lunii-device-gateway.jar copy %LOCAL_LUNIITHEQUE%\lib\lunii-device-gateway.jar %DOT_STUDIO%\lib\
if not exist %DOT_STUDIO%\lib\lunii-device-wrapper.jar copy %LOCAL_LUNIITHEQUE%\lib\lunii-device-wrapper.jar %DOT_STUDIO%\lib\

:: Copy agent and metadata JARs
copy %STUDIO_PATH%\agent\studio-agent-${project.version}-jar-with-dependencies.jar %DOT_STUDIO%\agent\studio-agent.jar
copy %STUDIO_PATH%\agent\studio-metadata-${project.version}-jar-with-dependencies.jar %DOT_STUDIO%\agent\studio-metadata.jar

java -Dvertx.logger-delegate-factory-class-name=io.vertx.core.logging.Log4j2LogDelegateFactory -Dfile.encoding=UTF-8 -cp %STUDIO_PATH%/${project.build.finalName}.jar;%STUDIO_PATH%/lib/*;%DOT_STUDIO%/lib/*;. io.vertx.core.Launcher run ${vertx.main.verticle}
