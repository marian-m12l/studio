#!/bin/sh
DOT_STUDIO="$HOME/.studio"
LOCAL_LUNIITHEQUE=$HOME/.local/share/Luniitheque

[ -e "lib/lunii-java-util.jar" ] || cp $LOCAL_LUNIITHEQUE/lib/lunii-java-util.jar lib/
[ -e "lib/lunii-device-gateway.jar" ] || cp $LOCAL_LUNIITHEQUE/lib/lib/lunii-device-gateway.jar lib/
[ -e "lib/lunii-device-wrapper.jar" ] || cp $LOCAL_LUNIITHEQUE/lib/lunii-device-wrapper.jar lib/
[ -d $DOT_STUDIO/lib ] || mkdir -p $HOME/.studio/lib

java -cp ${project.build.finalName}.jar:lib/*:$HOME/.studio/lib/*:. io.vertx.core.Launcher run ${vertx.main.verticle}
