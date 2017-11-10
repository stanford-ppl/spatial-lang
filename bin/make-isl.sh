#!/bin/bash

pushd .
cd isl
./autogen.sh
./configure
make
popd

#make install
