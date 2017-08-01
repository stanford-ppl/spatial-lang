#!/bin/bash

# Check if you are on the stanford network
if timeout 2 nc -z tucson.stanford.edu 22 2>/dev/null; then
    echo "On Stanford network: ✓"
    online=1
else
    echo "On Stanford network: ✗"
	echo "================================================================================"
	echo "  Stanford network unreachable!  "
	echo "================================================================================"
    online=0
    exit 1
fi

# Warn if apps not on regression branch
cd ${SPATIAL_HOME}/apps
ab=`git rev-parse --abbrev-ref HEAD`
cd ../
if [[ $ab != "regression" ]]; then 
	read -p "You seem to be on an apps branch that is not regression.  Continue? [y/N]: " choice
	echo    # (optional) move to a new line
	case "$choice" in 
	  y|Y ) echo "Continuing..";;
	  n|N ) exit 1;;
	  * ) exit 1;;
	esac
fi

branch=$(git symbolic-ref HEAD | sed -e 's,.*/\(.*\),\1,')

# Get hashes and messages
if [[ $SPATIAL_HOME == "" ]]; then
	echo "ERROR: Set your SPATIAL_HOME variable please"
	exit 1
fi
here=`pwd`
cd ${SPATIAL_HOME}
spatial_hash=`git rev-parse HEAD`
spatial_hash_message=`git log --stat --name-status HEAD^..HEAD`
argon_hash=`git ls-files -s argon | cut -d\  -f2`
virtualized_hash=`git ls-files -s scala-virtualized | cut -d\  -f2`
apps_hash=`git ls-files -s apps | cut -d\  -f2`
# cd argon
# argon_hash=`git rev-parse HEAD`
# argon_hash_message=`git log --stat --name-status HEAD^..HEAD`
# cd ../scala-virtualized
# virtualized_hash=`git rev-parse HEAD`
# virtualized_hash_message=`git log --stat --name-status HEAD^..HEAD`
# cd ../apps
# apps_hash=`git rev-parse HEAD`
# apps_hash_message=`git log --stat --name-status HEAD^..HEAD`
# cd ../
at=`date +"%Y-%m-%d_%H-%M-%S"`
machine=`hostname`
cd $here

# Specify tests to run
 # types=("chisel")
 # dsts=("portland")
types=("scala" "chisel")
dsts=("portland;/home/regression/" "max-2;/kunle/users/mattfel/regression" "ottawa;/home/regression"
	  "tflop2;/home/regression/" "tflop1;/home/regression/"
	  "tucson;/home/mattfel/regression" "london;/home/mattfel/regression")
	  #manchester 
tests=all
status=debug

# Compile regression test packet
i=0

echo -e "You may be requested to log in to a few kunle machines.  
We recommend you set up ssh keys for all these machines so you don't need to keep typing
your password.  

**WARNING** If you do not have access to any of these machines, please CANCEL this script and contact mattfel@stanford.edu and ask for an account\n"

for type in ${types[@]}; do
	# Find least occupied machine
	most_idle=999
	for dst in ${dsts[@]}; do
		fields=(${dst//;/ })
		# David hack
  		if [[ "$SUNETID" != "" && ${fields[0]} != "max-2" ]]; then
		tmpuser=${SUNETID}		   		
  		else
		tmpuser=${USER}
  		fi
		existing_runs=`ssh $tmpuser@${fields[0]}.stanford.edu "ls ${fields[1]}" | grep ^20[1-2][0-9] | wc -l`
		# echo "${fields[0]} has ${existing_runs} runs going (current best ${most_idle})"

		if [[ ${existing_runs} -lt $most_idle ]]; then
			if [[ $type = "chisel" && ${fields[0]} = "max-2" ]]; then
				echo ""
				# Do not let chisel run on max2 for now
			elif [[ $type = "chisel" && ${fields[0]} = "tflop1" ]]; then
				echo ""
				# Do not let chisel run on tflop1 for now
			else 
				candidate=$dst
				most_idle=$existing_runs
			fi
		fi
	done

	fields=(${candidate//;/ })
	dst=${fields[0]}
	path=${fields[1]}
	if [[ "$SUNETID" != "" && ${dst} != "max-2" ]]; then
	USERNAME=${SUNETID}		   		
	else
	USERNAME=${USER}
	fi

	packet="Creation Time- $at | Status- $status | Type- $type | tests- $tests | User- $USERNAME | Origin- $machine | Destination- ${dst} | Branch- $branch | Spatial- ${spatial_hash:0:5} | Argon- ${argon_hash:0:5} | Virtualized- ${virtualized_hash:0:5} | Spatial-apps- ${apps_hash:0:5}"
	# echo $packet
	echo -e "$packet
$at
$status
$type
$tests
$USERNAME
$machine
$spatial_hash
$branch
${dst}
${path}" > /tmp/${at}.${branch}.${type}.new


	if [[ "$SUNETID" != "" ]]; then
	LONDONUSER=${SUNETID}
	else 
	LONDONUSER=${USER}
	fi

	#echo "skipping scp"
	scp /tmp/${at}.${branch}.${type}.new ${USERNAME}@${dst}.stanford.edu:${path}
	echo "Test located at $dst : $path" > /tmp/${at}.${branch}.${type}---${dst}
	scp /tmp/${at}.${branch}.${type}---${dst} ${LONDONUSER}@london.stanford.edu:/remote/regression/mapping

	echo -e "\n** Sent $type test to $dst (because it had ${most_idle} tests there already) **\n"

	((i++))
done

if [[ ${USER} = "mattfel" ]]; then 
	bash ${SPATIAL_HOME}/bin/window.sh
fi

echo -e "\n✓ SUCCESS!  Regression packets have been issued!  Check /remote/regression/mapping to see what is running where"
exit 0

