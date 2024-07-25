#!/bin/bash

ARCH="$(uname -s)"
APP_PATH=`dirname $0`
VERSION=${project.build.finalName}

if [ "${ARCH}" == "Darwin" ]; then
    STUDIO_CP=$APP_PATH/lib/*:$APP_PATH/$VERSION.jar:$APP_PATH/../Resources/Java/lib/*:$APP_PATH/../Resources/Java/$VERSION.jar
    java -Dcom.apple.mrj.application.apple.menu.about.name="Lunii Transfert" -Dapple.awt.application.name="Lunii Transfert" -Xdock:description="Lunii Transfert" -Xdock:icon=$APP_PATH/../Resources/lunii.png -cp $STUDIO_CP -Djava.util.logging.manager=org.apache.logging.log4j.jul.LogManager studio.GUI
else
    STUDIO_CP=$APP_PATH/lib/*:$APP_PATH:$APP_PATH/$VERSION.jar
    java -cp $STUDIO_CP -Djava.util.logging.manager=org.apache.logging.log4j.jul.LogManager studio.GUI
fi
