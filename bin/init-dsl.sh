#!/bin/bash
dsl="Spatial"
lcdsl=`echo "$dsl" | tr '[:upper:]' '[:lower:]'`

if [ "$SPATIAL_HOME" == "" ]; then
        echo -e "Please set SPATIAL_HOME!"
        exit 1
fi

if [ "$VIRTUALIZED_HOME" == "" ]; then
       echo -e "Please set VIRTUALIZED_HOME!"
       exit 1
fi

if [ "$ARGON_HOME" == "" ]; then
       echo -e "Please set ARGON_HOME!"
       exit 1
fi

if [ $1 = 1 ]; then #requested republish
  cd $VIRTUALIZED_HOME
  sbt compile && sbt publishLocal
fi

if [ $2 = 1 ]; then #requested just dsl
  cd ${SPATIAL_HOME}
  sbt compile
fi
