#!/bin/bash

files=(*)
new_packets=()
for f in ${files[@]}; do if [[ $f = *".networkerror"* ]]; then new_packets+=($f); fi; done
for f in ${new_packets[@]}; do
	rm $f
done

