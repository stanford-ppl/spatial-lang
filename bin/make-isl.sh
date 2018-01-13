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
fi

BIN="./isl-bin/emptiness"
if [[ ! -f $BIN ]]; then
	echo "Making emptiness executable"
	pushd .
	cd isl-bin
	make
	popd
fi
