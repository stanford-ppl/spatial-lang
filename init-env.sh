#!/bin/bash

export SPATIAL_HOME=`pwd`
export PUB_HOME=${SPATIAL_HOME}
export TEMPLATES_HOME=${SPATIAL_HOME}/spatial/src/spatial/codegen/chiselgen/resources/template-level
export JAVA_HOME=$(readlink -f $(dirname $(readlink -f $(which java)))/../../)
