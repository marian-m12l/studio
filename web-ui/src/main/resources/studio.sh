#!/bin/sh

java -cp ${project.build.finalName}.jar:lib/*:~/.studio/lib/*:. io.vertx.core.Launcher run ${vertx.main.verticle}
