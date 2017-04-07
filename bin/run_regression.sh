#!/bin/bash

# Check if you are on the stanford network
if timeout 2 nc -z tucson.stanford.edu 22 2>/dev/null; then
    echo "On Stanford network: ✓"
    online=1
else
    echo "On Stanford network: ✗"
    online=0
fi

branch=$(git symbolic-ref HEAD | sed -e 's,.*/\(.*\),\1,')

# Get hashes and messages
if [[ $SPATIAL_HOME == "" || $ARGON_HOME == "" || $VIRTUALIZED_HOME == "" ]]; then
	echo "ERROR: Set your env variables! (spatial, argon, virtualized homes)"
	exit 1
fi
here=`pwd`
cd ${SPATIAL_HOME}
spatial_hash=`git rev-parse HEAD`
spatial_hash_message=`git log --stat --name-status HEAD^..HEAD`
# cd argon
# argon_hash=`git rev-parse HEAD`
# argon_hash_message=`git log --stat --name-status HEAD^..HEAD`
# cd ../scala-virtualized
# virtualized_hash=`git rev-parse HEAD`
# virtualized_hash_message=`git log --stat --name-status HEAD^..HEAD`
at=`date +"%Y-%m-%d_%H-%M-%S"`
machine=`hostname`
cd $here

# Specify tests to run
types=("chisel")
dsts=("portland")
#types=("scala" "chisel")
#dsts=("max-2" "portland") # CHANGE TUCSON TO PORTLAND ASAP
tests=all
status=debug

# Compile regression test packet
i=0
for type in ${types[@]}; do
packet="Creation Time- $at | Status- $status | Type- $type | tests- $tests | User- $USER | Origin- $machine | Destination- ${dsts[$i]} | Branch- $branch | Spatial- ${spatial_hash:0:5} | Argon- ${argon_hash:0:5} | Virtualized- ${virtualized_hash:0:5}"

# echo -e "$packet"
path="/remote/regression/${type}"

echo -e "$packet
$at
$status
$type
$tests
$USER
$machine
$spatial_hash
$branch
${dsts[$i]}
${path}" > /tmp/${at}.${branch}.${type}.new

if [[ $online = 1 ]]; then
#        echo "skipping scp"
	scp /tmp/${at}.${branch}.${type}.new ${dsts[$i]}.stanford.edu:${path}
else
	cp /tmp/${at}.${branch}.${type}.new ${SPATIAL_HOME}/bin/${at}.${branch}.${type}.networkerror
fi

((i++))
done

if [[ $online = 1 ]]; then
	echo "Using incron on ${dsts[@]} to call regression tests (scripts/receive.sh)"
	exit 0
else
	echo "================================================================================"
	echo "  Stanford network unreachable!  Run script when you are on the network:"
	echo "     ${SPATIAL_HOME}/bin/run_backlog.sh or ${SPATIAL_HOME}/bin/clean_backlog.sh"
	echo "================================================================================"
	exit 0
fi

echo "**** TEMPORARILY TURNED OFF MAX2"
