#!/bin/sh

DOT_STUDIO=$HOME/.studio
LOCAL_LUNIITHEQUE="$HOME/Library/Application Support/Luniitheque"

# Make sure the .studio subdirectories exist
if [ ! -d $DOT_STUDIO/db ]; then mkdir -p $DOT_STUDIO/db; fi
if [ ! -d $DOT_STUDIO/lib ]; then mkdir -p $DOT_STUDIO/lib; fi
if [ ! -d $DOT_STUDIO/library ]; then mkdir -p $DOT_STUDIO/library; fi

# Copy Luniistore JARs if needed
if [ ! -e "$DOT_STUDIO/lib/lunii-java-util.jar" ]; then cp "$LOCAL_LUNIITHEQUE/lib/lunii-java-util.jar" $DOT_STUDIO/lib/; fi
if [ ! -e "$DOT_STUDIO/lib/lunii-device-gateway.jar" ]; then cp "$LOCAL_LUNIITHEQUE/lib/lunii-device-gateway.jar" $DOT_STUDIO/lib/; fi
if [ ! -e "$DOT_STUDIO/lib/lunii-device-wrapper.jar" ]; then cp "$LOCAL_LUNIITHEQUE/lib/lunii-device-wrapper.jar" $DOT_STUDIO/lib/; fi

java -cp ${project.build.finalName}.jar:lib/*:$DOT_STUDIO/lib/*:. io.vertx.core.Launcher run ${vertx.main.verticle}
