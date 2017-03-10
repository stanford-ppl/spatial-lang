#!/bin/bash

export ARGON_HOME=`pwd`/../argon
export SPATIAL_HOME=`pwd`
export VIRTUALIZED_HOME=`pwd`/../scala-virtualized
export PUB_HOME=${SPATIAL_HOME}
export TEMPLATES_HOME=${SPATIAL_HOME}/src/spatial/codegen/chiselgen/resources/template-level
export JAVA_HOME=$(readlink -f $(dirname $(readlink -f $(which java)))/../../)
