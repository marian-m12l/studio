#!/bin/sh

# STUDIO_PATH="`dirname \"$0\"`"

exec java $@ -jar quarkus-run.jar
