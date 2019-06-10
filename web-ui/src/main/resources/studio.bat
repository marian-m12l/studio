@echo off

java -cp ${project.build.finalName}.jar;lib/*;${system.libs}/*;. io.vertx.core.Launcher run ${vertx.main.verticle}
