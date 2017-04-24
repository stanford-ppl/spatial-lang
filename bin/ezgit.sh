#!/bin/bash

# Script for quickly merging a branch further from master into a branch closer
#   to master.  For example ezgit.sh fpga develop will merge fpga branch into develop branch.  
#   Call this script from your spatial-lang home

# Usage: 
#   arg 1 = lower branch name (develop, pre-master, master, etc..)
#   arg 2 = upper branch name
#   arg 3 = Run regression on higher branch? (1 or 0)

function gitt {
	# Delete those god damn lock files
	if [[ -f .git/modules/argon/index.lock ]]; then
		rm .git/modules/argon/index.lock
	fi
	if [[ -f .git/index.lock ]]; then 
		rm .git/index.lock
	fi
	git $1 $2 $3 $4 $5 $6 $7 $8 $9 $10
}

echo "=========================="
echo "Merging $1 --> $2"
echo "=========================="

# Clear git merging log
rm /tmp/pub

cd scala-virtualized
git pull
virtbranch=$(git symbolic-ref HEAD | sed -e 's,.*/\(.*\),\1,')
if [[ $virtbranch != "argon" ]]; then
	echo "Why are you on $virtbranch branch instead of argon branch for scala-virtualized?"
	rm /tmp/pub
	exit 1
fi
cd ../

# Get current branch
currentbranch=$(git symbolic-ref HEAD | sed -e 's,.*/\(.*\),\1,')


# # Checkout lower branch
# cd ../argon
# gitt checkout $1
# cd ..
# gitt checkout $1

# # Merge higher into lower
# cd argon
# gitt merge origin/$2 | tee -a /tmp/pub
# error=(`cat /tmp/pub | grep "CONFLICT" | wc -l`)
# if [[ $error != 0 ]]; then
# 	echo "Conflict error merging $2 into $1.  Please resolve"
# 	exit 1
# fi
# sleep 1
# gitt push

# cd ../
# gitt merge origin/$2 | tee -a /tmp/pub
# error=(`cat /tmp/pub | grep "CONFLICT" | wc -l`)
# if [[ $error != 0 ]]; then
# 	echo "Conflict error merging $2 into $1.  Please resolve"
# 	exit 1
# fi
# sleep 1
# gitt add argon
# gitt add scala-virtualized
# gitt commit -m "auto merge"
# gitt push

# Merge lower into higher
echo "=========================="
echo "Checkout $2 for argon"
echo "=========================="
cd argon
gitt stash
gitt checkout $2
gitt pull
gitt merge origin/$1 | tee -a /tmp/pub
error=(`cat /tmp/pub | grep -i "conflict\|error\|fatal" | wc -l`)
if [[ $error != 0 ]]; then
	echo "Conflict error merging $1 into $2.  Please resolve"
	rm /tmp/pub
	exit 1
fi
sleep 1
gitt push

echo "=========================="
echo "Checkout $2 for spatial-lang"
echo "=========================="
cd ../
gitt stash
gitt checkout $2
gitt pull
gitt merge origin/$1 | tee -a /tmp/pub
error=(`cat /tmp/pub | grep -i "conflict\|error\|fatal" | wc -l`)
if [[ $error != 0 ]]; then
	echo "Conflict error merging $1 into $2.  Please resolve"
	rm /tmp/pub
	exit 1
fi
sleep 1
gitt add argon
gitt add scala-virtualized
gitt commit -m "auto merge"
gitt push

# Regression test
if [[ $3 = 1 ]]; then
	echo "=========================="
	echo "Running regression on $2 branch"
	echo "=========================="
	bash bin/run_regression.sh
fi

# Go back to your original branch
echo "=========================="
echo "Checkout $currentbranch for argon"
echo "=========================="
cd argon
gitt checkout $currentbranch
echo "=========================="
echo "Checkout $current for spatial-lang"
echo "=========================="
cd ..
gitt checkout $currentbranch
cd argon
gitt stash pop
cd ..
gitt stash pop
rm /tmp/pub
