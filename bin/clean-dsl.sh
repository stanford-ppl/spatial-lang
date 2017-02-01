#!/bin/bash

if [ "$SPATIAL_HOME" == "" ]; then
        echo "Please set SPATIAL_HOME!"
        exit 1
fi

rm -rf $SPATIAL_HOME/lib_managed
rm -rf $SPATIAL_HOME/logs
rm -rf $SPATIAL_HOME/target
rm -rf $SPATIAL_HOME/gen

