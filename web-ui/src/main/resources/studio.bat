@echo off

java -cp ${project.build.finalName}.jar;lib/*;%UserProfile%/.studio/lib/*;. io.vertx.core.Launcher run ${vertx.main.verticle}
