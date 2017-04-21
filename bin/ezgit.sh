#!/bin/bash

# Script for quickly merging a branch further from master into a branch closer
#   to master.  For example ezgit.sh develop fpga will merge fpga branch into develop branch.  
#   Call this script from your spatial-lang home

# Usage: 
#   arg 1 = upper branch name (develop, pre-master, master, etc..)
#   arg 2 = lower branch name
#   arg 3 = Run regression on higher branch? (1 or 0)




# Clear git merging log
rm /tmp/pub

cd scala-virtualized
git pull
virtbranch=$(git symbolic-ref HEAD | sed -e 's,.*/\(.*\),\1,')
if [[ $virtbranch != "argon" ]]; then
	echo "Why are you on $virtbranch branch instead of argon branch for scala-virtualized?"
	exit 1
fi


# Get current branch
currentbranch=$(git symbolic-ref HEAD | sed -e 's,.*/\(.*\),\1,')



# # Checkout lower branch
# cd ../argon
# git checkout $2
# cd ..
# git checkout $2

# # Merge higher into lower
# cd argon
# git merge origin/$1 | tee -a /tmp/pub
# error=(`cat /tmp/pub | grep "CONFLICT" | wc -l`)
# if [[ $error != 0 ]]; then
# 	echo "Conflict error merging $1 into $2.  Please resolve"
# 	exit 1
# fi
# sleep 1
# git push

# cd ../
# git merge origin/$1 | tee -a /tmp/pub
# error=(`cat /tmp/pub | grep "CONFLICT" | wc -l`)
# if [[ $error != 0 ]]; then
# 	echo "Conflict error merging $1 into $2.  Please resolve"
# 	exit 1
# fi
# sleep 1
# git add argon
# git add scala-virtualized
# git commit -m "auto merge"
# git push


# Merge lower into higher
cd argon
git checkout $1
git pull
git merge origin/$2 | tee -a /tmp/pub
error=(`cat /tmp/pub | grep -i "conflict\|error" | wc -l`)
if [[ $error != 0 ]]; then
	echo "Conflict error merging $1 into $2.  Please resolve"
	exit 1
fi
sleep 1
git push

cd ../
git checkout $1
git pull
git merge origin/$2 | tee -a /tmp/pub
error=(`cat /tmp/pub | grep -i "conflict\|error" | wc -l`)
if [[ $error != 0 ]]; then
	echo "Conflict error merging $1 into $2.  Please resolve"
	exit 1
fi
sleep 1
git add argon
git add scala-virtualized
git commit -m "auto merge"
git push

# Regression test
if [[ $3 = 1 ]]; then
	bash bin/run_regression.sh
fi

# Go back to your original branch
cd argon
git checkout $currentbranch
cd ..
git checkout $currentbranch