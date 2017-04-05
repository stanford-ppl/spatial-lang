#!/bin/bash
dsl="Spatial"
lcdsl=`echo "$dsl" | tr '[:upper:]' '[:lower:]'`

if [ "$SPATIAL_HOME" == "" ]; then
    SPATIAL_HOME=$(dirname $(dirname $(readlink -f "$0")))
fi

if [ $1 = 1 ]; then #requested just dsl
  cd ${SPATIAL_HOME}
  sbt "spatial/assembly"
fi

if [ $1 = 2 ]; then # requested apps
  cd ${SPATIAL_HOME}
  sbt "apps/assembly"
fi

if [ $1 = 3 ]; then # jar release
  cd ${SPATIAL_HOME}
  sbt "apps/assembly"
fi
