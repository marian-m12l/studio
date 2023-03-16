#/bin/bash

APP_PATH=`dirname $0`
VERSION=studio-desktop-0.3.2-SNAPSHOT
STUDIO_CP=$APP_PATH/lib/*:$APP_PATH:$APP_PATH/$VERSION.jar

java -Xdock:icon=$APP_PATH/lunii.png -cp $STUDIO_CP -Djava.util.logging.manager=org.apache.logging.log4j.jul.LogManager studio.GUI