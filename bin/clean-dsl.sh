#!/bin/bash

if [ "$SPATIAL_HOME" == "" ]; then
    SPATIAL_HOME=$(dirname $(dirname $(readlink -f "$0")))
fi

#if [ "$SPATIAL_HOME" == "" ]; then
#  echo "Please set SPATIAL_HOME environment variable."
#  exit 1
#fi

#if [ "$ARGON_HOME" == "" ]; then
#  echo "Please set ARGON_HOME environment variable."
#  exit 1
#fi

rm -rf $SPATIAL_HOME/argon/core/target
rm -rf $SPATIAL_HOME/argon/forge/target
rm -rf $SPATIAL_HOME/argon/target
rm -rf $SPATIAL_HOME/spatial/target
rm -rf $SPATIAL_HOME/apps/target
rm -rf $SPATIAL_HOME/logs
rm -rf $SPATIAL_HOME/gen
rm -rf $SPATIAL_HOME/target

