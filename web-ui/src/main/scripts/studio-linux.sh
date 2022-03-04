#!/bin/sh

STUDIO_PATH="`dirname \"$0\"`"
DOT_STUDIO="$HOME/.studio"

# Make sure the .studio subdirectories exist
if [ ! -d $DOT_STUDIO/db ]; then mkdir -p $DOT_STUDIO/db; fi
if [ ! -d $DOT_STUDIO/library ]; then mkdir -p $DOT_STUDIO/library; fi

java -Dfile.encoding=UTF-8 -Dvertx.disableDnsResolver=true \
 -cp $STUDIO_PATH/${project.build.finalName}.jar:$STUDIO_PATH/lib/*:. \
 io.vertx.core.Launcher run ${vertx.main.verticle}
