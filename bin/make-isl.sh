#!/bin/bash

#TODO: I don't know how to do this properly
LIB=`ldconfig -p | grep libisl`
echo $LIB

if [ -z "$LIB" ]; then
	echo "Did not find ISL library - installing now"
	pushd .
	cd isl
	./autogen.sh
	./configure
	make
	make install
	popd
	
	pushd .
	cd isl-bin
	make
	popd
fi
