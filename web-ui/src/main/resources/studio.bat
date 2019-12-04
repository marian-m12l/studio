@echo off
copy %UserProfile%\AppData\Roaming\Luniitheque\lib\lunii-java-util.jar lib\
copy %UserProfile%\AppData\Roaming\Luniitheque\lib\lunii-device-gateway.jar lib\
copy %UserProfile%\AppData\Roaming\Luniitheque\lib\lunii-device-wrapper.jar lib\
mkdir %UserProfile%\.studio\lib
java -cp ${project.build.finalName}.jar;lib/*;%UserProfile%/.studio/lib/*;. io.vertx.core.Launcher run ${vertx.main.verticle}
