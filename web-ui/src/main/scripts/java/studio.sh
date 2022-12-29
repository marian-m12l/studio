#!/bin/sh

CWD="`dirname \"$0\"`"

# Override env vars
export STUDIO_HOME=$CWD

# batch args as java system properties
exec java $@ -jar $CWD/quarkus-run.jar
