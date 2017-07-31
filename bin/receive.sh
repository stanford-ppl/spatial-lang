#!/bin/sh

# NOTE: This script belongs on a remote where all machines can see and use it

export LANG=en_US.UTF-8
this_machine=`hostname`
if [[ ${this_machine} = "tflop1" ]]; then
  REGRESSION_HOME="/home/regression/"
elif [[ ${this_machine} = "tflop2" ]]; then
  REGRESSION_HOME="/home/regression/"
elif [[ ${this_machine} = "portland" ]]; then
  REGRESSION_HOME="/home/regression/"
elif [[ ${this_machine} = "max-2"* ]]; then
  REGRESSION_HOME="/kunle/users/mattfel/regression/"
elif [[ ${this_machine} = "tucson" ]]; then
  REGRESSION_HOME="/home/mattfel/regression/"
elif [[ ${this_machine} = "london" ]]; then
  REGRESSION_HOME="/home/mattfel/regression/"
elif [[ ${this_machine} = "ottawa" ]]; then
  REGRESSION_HOME="/home/regression/"
else
  echo "Unrecognized machine ${this_machine}" | tee -a /tmp/log
  exit 1
fi

## Helper for deleting directories when you still have those nfs files stuck in use
# 1 - directory to delete
stubborn_delete() {
  rm -rf $1 > /tmp/todelete 2>&1
  undeletable=(`cat /tmp/todelete | wc -l`)
  if [[ $undeletable -gt 0 ]]; then
    while read p; do
      f=(`echo $p | sed 's/rm: cannot remove \`//g' | sed "s/': Device or resource busy//g"`)
      fuser -k $f
    done </tmp/todelete
  fi
  rm -rf $1
}

## Helper function for checking if git checkouts were successful
# 1 - repo to check
checkout_success() {
  x=(`cat /tmp/gitstuff | grep error | grep "known to git" | wc -l`)
  if [[ $x -gt 0 ]]; then
    clean_exit "4" "Git error on ${1}"
  fi
}

## Helper function for safe exiting
# 1 - Error code
# 2 - (optional) error message
clean_exit() {
  summary=`sed -n '1p' $packet | sed -r "s/\| Status-[^\|]*\|/| Status- Failed (${1}) |/g"`
  echo "$summary"
  echo "`date` - $summary (clean_exit)" >> ${REGRESSION_HOME}/regression_history.log
  if [[ -z $2 ]]; then
    echo "Unknown error (${1})" >> $log
  else
    echo "Error: ${2} (${1})" >> $log
  fi

  logger "${2}"

  # errfile=`echo $packet | sed 's/ack/error/g'`
  rm $packet
  mv /remote/regression/mapping/${tim}.${branch}.${type_todo}---${this_machine} /remote/regression/graveyard
  exit 1
}


get_packet() {
# Make sure there is only one packet to work on
wc=`ls $REGRESSION_HOME | grep ".new" | wc -l`
multiple_new=false
if [[ $wc -gt 1 ]]; then
  #echo "Warning: More than one packet detected.  Grabbing newest!" > ${REGRESSION_HOME}/log
  cd ${REGRESSION_HOME}
  files=(*)
  new_packets=()
  for f in ${files[@]}; do if [[ $f = *".new"* ]]; then new_packets+=($f); fi; done
  sorted_packets=( $(for arr in "${new_packets[@]}"; do echo $arr; done | sort) )
  newpacket=${sorted_packets[${#sorted_packets[@]}-1]}
  packet=$newpacket
  multiple_new=true
  cd -
elif [[ $wc = 0 ]]; then
  # echo "`date` - no new packet" >> ${REGRESSION_HOME}/regression_history.log
  exit 1
else
  # Find the new packet 
  newpacket=`ls $REGRESSION_HOME | grep ".new"`  
  packet=$newpacket
fi

packet="${REGRESSION_HOME}/$packet"

# Acknowledge packet
ackfile=`echo $packet | sed 's/new/ack/g'`
if [ -f $packet ]; then
  mv $packet $ackfile
fi
packet=$ackfile

sleep 2 # Because wft

# Set vars based on this packet
type_todo=`sed -n '4p' $packet`
tests_todo=`sed -n '5p' $packet`
this_machine=`sed -n '10p' $packet`
tim=`sed -n '2p' $packet`
branch=`sed -n '9p' $packet`
dirname="${REGRESSION_HOME}/testdir-${branch}.${tim}.${type_todo}.${tests_todo}"
SPATIAL_HOME="$dirname/spatial-lang"
ARGON_HOME="$SPATIAL_HOME/argon"
VIRTUALIZED_HOME="$SPATIAL_HOME/scala-virtualized"
WIKI_HOME="$SPATIAL_HOME/spatial-lang.wiki"
wiki_file="${WIKI_HOME}/Brnch:${branch}-Trgt:${type_todo}.md"
spatial_hash=`sed -n '8p' $packet`
PIR_HOME=$SPATIAL_HOME
pretty_name=Pretty_Hist_Branch_${branch}_Backend_${type_todo}.csv
pretty_file=${SPATIAL_HOME}/spatial-lang.wiki/${pretty_name}
log="${REGRESSION_HOME}/log-${tim}.${branch}.${type_todo}"

logger "Got packet.  `sed -n '1p' $packet`"
if [ $multiple_new = "true" ]; then
  logger "Had to get newest because multiple packets found:"
  logger "   ${sorted_packets[@]}"
fi

}

# Helper function for ensuring a file or dir exists
## 1 - directory to check
## 2 - error code (for tracing back where failure occured)
exists() {
  # Create error dictionary
  error_dict=(
    "N/A" #0
    "Error!  Hyperdsl did not clone properly" #1
    "Error!  Spatial did not clone properly" #2
    "Error!  Spatial did not build properly. No published" #3
    )

  if [ ! -d "$1" ]; then
    clean_exit $2 ${error_dict[$2]}
  fi
}

# Helper function for logging a message
## 1 - Message
logger() {
  echo "[${phase}] `date` - $1" >> $log
}

# Helper function for doing git stuff, so
#  that I can edit this script without crashing things (bash is odd)
git_things() {

  # Make directory for this regression test
  stubborn_delete $dirname
  mkdir $dirname
  cp $packet $dirname/$newpacket

  # Clone repos in dirname
  cd $dirname
  export dirname=${dirname}
  export SPATIAL_HOME=${SPATIAL_HOME}
  export ARGON_HOME=${ARGON_HOME}
  export VIRTUALIZED_HOME=${VIRTUALIZED_HOME}
  export WIKI_HOME=${WIKI_HOME}
  export wiki_file=${wiki_file}
  export spatial_hash=${spatial_hash}
  # export JAVA_HOME=/usr/
  logger "Cloning spatial... Are your ssh keys set up in git?"
  git clone git@github.com:stanford-ppl/spatial-lang.git > /dev/null 2>&1
  logger "Cloning done!"
  exists "$SPATIAL_HOME" 1
  cd $SPATIAL_HOME
  logger "Switching spatial commit (${spatial_hash})"
  git fetch > /dev/null 2>&1
  git checkout ${spatial_hash} > /tmp/gitstuff 2>&1
  checkout_success "Spatial"
  logger "Getting submodules..."
  cd $SPATIAL_HOME
  git submodule update --init
  logger "Getting done!"
  cd argon
  export argon_hash=`git rev-parse HEAD`
  cd ../scala-virtualized
  export virtualized_hash=`git rev-parse HEAD`
  cd ../apps
  export apps_hash=`git rev-parse HEAD`  
  cd ../

  # exists "$ARGON_HOME" 2
  # cd $ARGON_HOME
  # logger "Switching argon commit (${argon_hash})"
  # git fetch > /dev/null 2>&1
  # git checkout ${argon_hash} > /tmp/gitstuff 2>&1
  # cd $VIRTUALIZED_HOME
  # logger "Switching virtualized commit (${virtualized_hash})"
  # git checkout ${virtualized_hash} > /tmp/gitstuff 2>&1

  cd $SPATIAL_HOME
  checkout_success "Spatial"
  logger "Cloning wiki..."
  git clone git@github.com:stanford-ppl/spatial-lang.wiki.git  > /dev/null 2>&1
  git checkout -f HEAD  > /dev/null 2>&1
  logger "Cloning done!"
}

# Get test to run
# test_to_run=${1}
# if [[ "${test_to_run}" = "scala" ]]; then
#   # Give others headstart
#   sleep 2
# elif [[ "${test_to_run}" = "maxj" ]]; then
#   # Give others headstart
#   sleep 40
# fi

# Receive and parse packet
phase="INIT"
get_packet

# Do all the cloning and building
sleep 10 # Give it some time to upload to github
phase="GIT"
git_things

# Switch to revision-controlled bash script
phase="FUNCTIONS"
logger "Sourcing functions..."
source ${SPATIAL_HOME}/bin/regression_functions.sh
if [[ $? -ne 0 ]]; then
  logger "${SPATIAL_HOME}/bin/regression_functions.sh is nonexistent or has error!"
  clean_exit 7 "${SPATIAL_HOME}/bin/regression_functions.sh is nonexistent or has error!"
fi
# if [[ ! "${type_todo}" = "${test_to_run}" ]]; then
#   echo "Error: packet mislabeled.  Cannot run ${test_to_run} test on ${type_todo} packet!" >> $log
#   clean_exit 6
# fi
logger "Sourcing successful!"

# Wait for channel to be free
phase="COORDINATION"
logger "Looking for senior files..."
coordinate

# Reset counter for how long this test takes
SECONDS=0

# Build spatial
phase="BUILD"
build_spatial

# Lock file
logger "Locking my packet!"
lockfile=`echo $packet | sed 's/ack/lock/g'`
mv $packet $lockfile
packet=$lockfile

# Launch tests
phase="VULTURE"
launch_tests

# # Delay while tests run
# phase="NAPPING"
# for i in `seq 1 $numpieces`; do
#   dots="($i / $numpieces x $((delay/numpieces))s)"
#   logger "Sleeping $dots"
#   sleep $((delay/numpieces))
# done

# Update result file
phase="RESULTS"
collect_results

# Update history
phase="HISTORY"
update_histories

# Clean up regression files and push to git
phase="CLEANUP"
wipe_clean









# failed 1
# failed 2
# failed 3
# failed 4
# failed 5
# failed 6
# failed 7
# failed 8
# see clean_exit # or exists

                                   