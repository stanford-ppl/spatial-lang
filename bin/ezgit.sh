#!/bin/bash

# Script for quickly merging a branch further from master into a branch closer
#   to master.  For example ezgit.sh fpga develop will merge fpga branch into develop branch.  
#   Call this script from your spatial-lang home

# Usage: 
#   arg 1 = lower branch name (develop, pre-master, master, etc..)
#   arg 2 = upper branch name
#   arg 3 = Run regression on higher branch? (1 or 0)

function git {
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
# git checkout $1
# cd ..
# git checkout $1

# # Merge higher into lower
# cd argon
# git merge origin/$2 | tee -a /tmp/pub
# error=(`cat /tmp/pub | grep "CONFLICT" | wc -l`)
# if [[ $error != 0 ]]; then
# 	echo "Conflict error merging $2 into $1.  Please resolve"
# 	exit 1
# fi
# sleep 1
# git push

# cd ../
# git merge origin/$2 | tee -a /tmp/pub
# error=(`cat /tmp/pub | grep "CONFLICT" | wc -l`)
# if [[ $error != 0 ]]; then
# 	echo "Conflict error merging $2 into $1.  Please resolve"
# 	exit 1
# fi
# sleep 1
# git add argon
# git add scala-virtualized
# git commit -m "auto merge"
# git push

# Merge lower into higher
echo "=========================="
echo "Checkout $2 for argon"
echo "=========================="
cd argon
git stash
git checkout $2
git pull
git merge origin/$1 | tee -a /tmp/pub
error=(`cat /tmp/pub | grep -i "conflict\|error\|fatal" | wc -l`)
if [[ $error != 0 ]]; then
	echo "Conflict error merging $1 into $2.  Please resolve"
	rm /tmp/pub
	exit 1
fi
sleep 1
git push

echo "=========================="
echo "Checkout $2 for spatial-lang"
echo "=========================="
cd ../
git stash
git checkout $2
git pull
git merge origin/$1 | tee -a /tmp/pub
error=(`cat /tmp/pub | grep -i "conflict\|error\|fatal" | wc -l`)
if [[ $error != 0 ]]; then
	echo "Conflict error merging $1 into $2.  Please resolve"
	rm /tmp/pub
	exit 1
fi
sleep 1
git add argon
git add scala-virtualized
git commit -m "auto merge"
git push

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
git checkout $currentbranch
echo "=========================="
echo "Checkout $current for spatial-lang"
echo "=========================="
cd ..
git checkout $currentbranch
cd argon
git stash pop
cd ..
git stash pop
rm /tmp/pub
