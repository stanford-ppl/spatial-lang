#!/bin/bash

if [ "$(uname)" == "Darwin" ]; then
	# Do something under Mac OS X platform        
	export JAVA_HOME=$(/usr/libexec/java_home)
elif [ "$(expr substr $(uname -s) 1 5)" == "Linux" ]; then
	# Do something under GNU/Linux platform
	export JAVA_HOME=$(readlink -f $(dirname $(readlink -f $(which java)))/../../)
fi
