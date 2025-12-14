#!/bin/sh

STUDIO_PATH="`dirname \"$0\"`"
DOT_STUDIO="$HOME/.studio"

# Make sure the .studio subdirectories exist
if [ ! -d $DOT_STUDIO/agent ]; then mkdir -p $DOT_STUDIO/agent; fi
if [ ! -d $DOT_STUDIO/db ]; then mkdir -p $DOT_STUDIO/db; fi
if [ ! -d $DOT_STUDIO/library ]; then mkdir -p $DOT_STUDIO/library; fi

# Copy agent and metadata JARs
cp $STUDIO_PATH/agent/studio-agent-${project.version}-jar-with-dependencies.jar $DOT_STUDIO/agent/studio-agent.jar
cp $STUDIO_PATH/agent/studio-metadata-${project.version}-jar-with-dependencies.jar $DOT_STUDIO/agent/studio-metadata.jar

# Configure libusb library path for libusb4java
# The library expects libusb at /opt/local/lib (MacPorts), but Homebrew installs it elsewhere
LIBUSB_TARGET="/opt/local/lib/libusb-1.0.0.dylib"
LIBUSB_SOURCE=""

# Check if MacPorts libusb already exists (no action needed)
if [ -f "$LIBUSB_TARGET" ]; then
    : # Already exists, nothing to do
# Check for Homebrew libusb (Apple Silicon: /opt/homebrew, Intel: /usr/local)
elif [ -f "/opt/homebrew/lib/libusb-1.0.dylib" ]; then
    LIBUSB_SOURCE="/opt/homebrew/lib/libusb-1.0.dylib"
elif [ -f "/usr/local/lib/libusb-1.0.dylib" ]; then
    LIBUSB_SOURCE="/usr/local/lib/libusb-1.0.dylib"
fi

# Create symlink if needed
if [ -n "$LIBUSB_SOURCE" ]; then
    if mkdir -p /opt/local/lib 2>/dev/null && ln -sf "$LIBUSB_SOURCE" "$LIBUSB_TARGET" 2>/dev/null; then
        : # Symlink created successfully
    else
        # Fallback: use DYLD_FALLBACK_LIBRARY_PATH (may not work on all macOS versions due to SIP)
        LIBUSB_DIR=$(dirname "$LIBUSB_SOURCE")
        export DYLD_FALLBACK_LIBRARY_PATH="$LIBUSB_DIR:${DYLD_FALLBACK_LIBRARY_PATH:-}"
    fi
fi

java -Dvertx.disableDnsResolver=true -Djava.util.logging.manager=org.apache.logging.log4j.jul.LogManager -Dvertx.logger-delegate-factory-class-name=io.vertx.core.logging.Log4j2LogDelegateFactory -Dfile.encoding=UTF-8 -cp $STUDIO_PATH/${project.build.finalName}.jar:$STUDIO_PATH/lib/*:. io.vertx.core.Launcher run ${vertx.main.verticle}
