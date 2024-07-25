#!/bin/bash

APP_PATH=`dirname $0`
VERSION=${project.build.finalName}
STUDIO_CP=$APP_PATH/lib/*:$APP_PATH:$APP_PATH/$VERSION.jar

if [ "$(uname)" == "Darwin" ]; then
    java -Xdock:description="Studio" -Xdock:icon=$APP_PATH/lunii.png -cp $STUDIO_CP -Djava.util.logging.manager=org.apache.logging.log4j.jul.LogManager studio.GUI
else
    java -cp $STUDIO_CP -Djava.util.logging.manager=org.apache.logging.log4j.jul.LogManager studio.GUI
fi
