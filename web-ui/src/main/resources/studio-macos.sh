#!/bin/sh

STUDIO_PATH="`dirname \"$0\"`"
DOT_STUDIO=$HOME/.studio

# Configure mount point of the device
FS_MOUNTPOINT=

# Make sure the .studio subdirectories exist
if [ ! -d $DOT_STUDIO/agent ]; then mkdir -p $DOT_STUDIO/agent; fi
if [ ! -d $DOT_STUDIO/db ]; then mkdir -p $DOT_STUDIO/db; fi
if [ ! -d $DOT_STUDIO/library ]; then mkdir -p $DOT_STUDIO/library; fi

# Copy agent and metadata JARs
cp $STUDIO_PATH/agent/studio-agent-${project.version}-jar-with-dependencies.jar $DOT_STUDIO/agent/studio-agent.jar
cp $STUDIO_PATH/agent/studio-metadata-${project.version}-jar-with-dependencies.jar $DOT_STUDIO/agent/studio-metadata.jar

java -Dvertx.disableDnsResolver=true -Djava.util.logging.manager=org.apache.logging.log4j.jul.LogManager -Dvertx.logger-delegate-factory-class-name=io.vertx.core.logging.Log4j2LogDelegateFactory -Dfile.encoding=UTF-8 -Dstudio.fs.mountpoint=$FS_MOUNTPOINT -cp $STUDIO_PATH/${project.build.finalName}.jar:$STUDIO_PATH/lib/*:. io.vertx.core.Launcher run ${vertx.main.verticle}
