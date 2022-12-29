#!/bin/sh

CWD="`dirname \"$0\"`"

# Override env vars
export STUDIO_HOME=$CWD

# batch args as command args
exec $CWD/studio*runner $@
