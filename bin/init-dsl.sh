#!/bin/bash
dsl="Spatial"
lcdsl=`echo "$dsl" | tr '[:upper:]' '[:lower:]'`

if [ "$SPATIAL_HOME" == "" ]; then
    SPATIAL_HOME=$(dirname $(dirname $(readlink -f "$0")))
fi

if [ $2 = 1 ]; then #requested just dsl
  cd ${SPATIAL_HOME}
  sbt "; project spatial; assembly"
fi

if [ $3 = 1 ]; then # requested apps
  cd ${SPATIAL_HOME}
  sbt "; project apps; assembly"
fi
