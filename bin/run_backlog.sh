#!/bin/bash

echo "This script is not yet tested!!"

# Check if you are on the stanford network
if nc -z tucson.stanford.edu 22 2>/dev/null; then
    echo "On Stanford network: ✓"
    online=1
else
    echo "On Stanford network: ✗"
    online=0
fi

files=(*)
new_packets=()
for f in ${files[@]}; do if [[ $f = *".new"* ]]; then new_packets+=($f); fi; done
for f in ${new_packets[@]}; do
	dst=`sed -n '12p' $f`
	path=`sed -n '13p' $f`
	if [[ $online = 1 ]]; then
		scp $f ${dst}.stanford.edu:${path}
	    echo "Copied packet $f to $dst.."
	fi
	sleep 0.1
done

