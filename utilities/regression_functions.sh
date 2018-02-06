#!/bin/bash

## This file contains all the functions used for regression testing.
##   It is called from the receive.sh, which handles path variables
##   and git checkouts on a server-specific basis

spacing=30
delay=1530
numpieces=30
hist=72

stamp_commit_msgs() {
  logger "Stamping commit messages"
  cd $SPATIAL_HOME
  tag=(`git describe --exact-match HEAD`)
  if [[ ! "${tag[@]}" = "" ]]; then
    echo -e "\n# Tag = $tag\n" >> $wiki_file
  fi
  spatial_msg=`git log --stat --name-status ${spatial_hash}^..${spatial_hash}`
  cd $ARGON_HOME
  argon_msg=`git log --stat --name-status ${argon_hash}^..${argon_hash}`
  cd $VIRTUALIZED_HOME
  virtualized_msg=`git log --stat --name-status ${virtualized_hash}^..${virtualized_hash}`
  cd ${SPATIAL_HOME}/apps
  apps_msg=`git log --stat --name-status ${apps_hash}^..${apps_hash}`
  echo "
# Commits
" >> $wiki_file
  echo -e "\nSpatial commit\n\`\`\`\n${spatial_msg}\n\`\`\`" >> $wiki_file
  echo -e "\nArgon commit\n\`\`\`\n${argon_msg}\n\`\`\`" >> $wiki_file
  echo -e "\nVirtualized commit\n\`\`\`\n${virtualized_msg}\n\`\`\`" >> $wiki_file
  echo -e "\nSpatial-Apps commit\n\`\`\`\n${apps_msg}\n\`\`\`" >> $wiki_file
  echo "
# Test summary
" >> $wiki_file
  summary=`sed -n '1p' $packet`
  echo -e "\n\n${summary}" >> $wiki_file
}

## Function for finding filse with older timestamps.
##   This tester will yield to any active or new tests who are older
coordinate() {
  check_packet
  cd ${REGRESSION_HOME}
  files=(*)
  new_packets=()
  sorted_packets=()
  # Removed this $f = *"$branch"* check in the following 3 places because sbt seems to screw up when used in parallel
  # for f in ${files[@]}; do if [[ ($f = *".new"* || $f = *".ack"* || $f = *".lock"*) && $f = *".$branch."* ]]; then new_packets+=($f); fi; done
  for f in ${files[@]}; do if [[ ($f = *".new"* || $f = *".ack"* || $f = *".lock"*) ]]; then new_packets+=($f); fi; done
  sorted_packets=( $(for arr in "${new_packets[@]}"; do echo $arr; done | sort) )
  stringified=$( IFS=$' '; echo "${sorted_packets[*]}" )
  rank=-1
  for i in ${!sorted_packets[@]}; do if [[  "$packet" = *"${sorted_packets[$i]}"* ]]; then rank=${i}; fi; done
  while [ $rank != 0 ]; do
    # Sanity check
    check_packet

    logger "This packet (${packet}) is ${rank}-th in line (${stringified})... Waiting 300 seconds..."
    sleep 300

    # Update active packets list
    files=(*)
    new_packets=()
    sorted_packets=()
    for f in ${files[@]}; do if [[ ($f = *".new"* || $f = *".ack"* || $f = *".lock"*) ]]; then new_packets+=($f); fi; done
    sorted_packets=( $(for arr in "${new_packets[@]}"; do echo $arr; done | sort) )
    stringified=$( IFS=$' '; echo "${sorted_packets[*]}" )
    rank=-1
    for i in ${!sorted_packets[@]}; do if [[  "$packet" = *"${sorted_packets[$i]}"* ]]; then rank=${i}; fi; done
  done
  logger "Packet cleared to launch! (${packet}) in list (${stringified})"

  cd -
}

## Function for checking if packet still exists, and quit if it doesn't
check_packet() {
  ret=(`pwd`)
  cd ${REGRESSION_HOME}
  # Check for regression packet in a kind of expensive way
  files=(*)
  new_packets=()
  sorted_packets=()
  for f in ${files[@]}; do if [[ ($f = *".new"* || $f = *".ack"* || $f = *".lock"*) ]]; then new_packets+=($f); fi; done
  sorted_packets=( $(for arr in "${new_packets[@]}"; do echo $arr; done | sort) )
  stringified=$( IFS=$' '; echo "${sorted_packets[*]}" )
  rank=-1
  for ii in ${!sorted_packets[@]}; do if [[  "$packet" = *"${sorted_packets[$ii]}"* ]]; then rank=${ii}; fi; done
  if [ $rank = -1 ]; then
    logger "Packet for $packet disappeared from list $stringified!  Quitting ungracefully!"
    mv /remote/regression/mapping/${tim}.${branch}.${type_todo}---${this_machine} /remote/regression/graveyard
    exit 1
  fi
  # Check for mapping packet in kind of an expensive way
  cd /remote/regression/mapping
  files=(*)
  stringified=$( IFS=$' '; echo "${files[*]}" )
  mapped_packets=()
  for f in ${files[@]}; do if [[ $f = *"${tim}.${branch}.${type_todo}---${this_machine}"* ]]; then mapped_packets+=($f); fi; done
  rank=${#mapped_packets[@]}
  if [ $rank = 0 ]; then
    rm -f $packet
    logger "Mapping (${tim}.${branch}.${type_todo}---${this_machine}) for $packet disappeared from list $stringified !  Quitting ungracefully!"
    exit 1
  fi
  cd $ret  
}

## Function for building spatial
build_spatial() {
  check_packet
  logger "Cleaning old regression directories..."
  cd ${REGRESSION_HOME}
  files=(*)
  testdirs=()
  sorted_testdirs=()
  for f in ${files[@]}; do if [[ $f = *"testdir-${branch}"*"${type_todo}"* ]]; then testdirs+=($f); fi; done
  sorted_testdirs=( $(for arr in "${testdirs[@]}"; do echo $arr; done | sort) )
  stringified=$( IFS=$' '; echo "${sorted_testdirs[*]}" )
  rank=-5
  for i in ${!sorted_testdirs[@]}; do if [[  "${sorted_testdirs[$i]}" = *"$tim"* ]]; then rank=$((i-1)); fi; done
  # Sanity check
  if [ $rank = -5 ]; then 
    logger "CRITICAL ERROR: ${tim} stamp was not found in dirs list ${stringified}"
    rm ${tim}*
    exit 1
  fi

  for i in `seq 0 $rank`; do
    check_packet
    logger "Cleaning ${sorted_testdirs[$i]}..."
    cmd="stubborn_delete ${sorted_testdirs[$i]}"
    eval "$cmd"
  done
  logger "Cleanup done!"
  check_packet
  
  # logger "Patching the nsc library thing..."
  # cd $DELITE_HOME
  # find ./ -type f -exec sed -i -e 's/^import tools.nsc/import scala.tools.nsc/g' {} \; # Not sure why we do this suddenly..
  # cd $SPATIAL_HOME
  # find ./ -type f -exec sed -i -e 's/^import tools.nsc/import scala.tools.nsc/g' {} \; # Not sure why we do this suddenly..
  # logger "Patch done!"

  logger "Making spatial..."
  cd $SPATIAL_HOME
  # sbt compile > /tmp/log 2>&1
  # make lang > /tmp/log 2>&1
  # make apps > /tmp/log 2>&1
  # logger "Spatial done!"
  # logger "Checking if spatial made correctly..."
  # errs=(`cat /tmp/log | grep "\[.*error.*\]" | wc -l`)
  # if [[ $errs -gt 0 ]]; then
  # 	clean_exit 8 "Detected errors in spatial build (/tmp/log)"
  # fi
  # logger "Spatial probably made correctly but not sure how to check in argon spatial"

  # # Patch bin/spatial
  # logger "Patching bin/spatial..."
  # if [[ ${test_to_run} = "maxj" ]]; then
  #   sed -i 's/parser.add_argument("--maxj", dest="maxj", action="store_true", default=False/parser.add_argument("--maxj", dest="maxj", action="store_true", default=True/g' ${PUB_HOME}/bin/spatial
  # elif [[ ${test_to_run} = "scala" ]]; then
  #   sed -i 's/dest="test", action="store_true", default=False/dest="test", action="store_true", default=True/g' ${PUB_HOME}/bin/spatial
  # fi
  # sed -i "s/parser.add_argument('--CGRA', dest='cgra', action='store_true', default=True/parser.add_argument('--CGRA', dest='cgra', action='store_true', default=False/g" ${PUB_HOME}/bin/spatial
  # logger "Patch done!"
}

get_flags() {
  # Include retiming if this is a retiming branch
  if [[ ${branch} = *"retim"* ]]; then
    retime_flag="--retiming"
  else
    retime_flag=""
  fi

  # Include syncMem if this is a syncMem branch
  if [[ ${branch} = *"syncMem"* ]]; then
    syncMem_flag="--syncMem"
  else
    syncMem_flag=""
  fi

  # Include instrumentation if this is a pre-master branch
  if [[ ${branch} = *"pre-master"* ]]; then
    instrument_flag="--instrument"
  else
    instrument_flag=""
  fi

  # Set proper multifile flag
  if [[ ${branch} = *"syncMem"* ]]; then
    mf=4
  else
    mf=5
  fi

  # get type
  if [[ ${type_todo} = "scala" ]]; then
    gen="--sim"
  else
    gen="--synth"
  fi

  flags="$gen --multifile=${mf} ${retime_flag} ${syncMem_flag} ${instrument_flag}"
}

## Function for cleaning up iff test was successful
wipe_clean() {
summary=`sed -n '1p' $packet | sed -r "s/\| Status-[^\|]*\|/| Status- Success |/g"`

echo "`date` - $summary (wipe_clean)" >> ${REGRESSION_HOME}/regression_history.log

cd $WIKI_HOME
logger "Pushing wiki..."
git add -A
git commit -m "Automated incron update"
git push

logger "Removing packet ${packet} so those waiting are clear to launch"
mv /remote/regression/mapping/${tim}.${branch}.${type_todo}---${this_machine} /remote/regression/graveyard
rm $packet

sleep 2000
stubborn_delete ${dirname}

# ps aux | grep -ie mattfel | grep -v ssh | grep -v bash | grep -iv screen | grep -v receive | awk '{system("kill -9 " $2)}'

exit 1

}

## Function for collecting results and reporting in the markdown
# collect_results() {
# logger "Removing old wiki directory..."
# rm -rf $WIKI_HOME
# cd $SPATIAL_HOME
# logger "Cloning wiki to avoid conflicts..."
# git clone git@github.com:stanford-ppl/spatial-lang.wiki.git  > /dev/null 2>&1
# git checkout -f HEAD  > /dev/null 2>&1
# logger "Cloning done!"
# logger "Cleaning old markdown file..."
# rm $wiki_file > /dev/null 2>&1
# touch $wiki_file
# logger "Putting timestamp in wiki"
# duration=$SECONDS
# echo -e "
# Time elapsed: $(($duration / 60)) minutes, $(($duration % 60)) seconds
# * <---- indicates relative amount of work needed before app will **pass**
# * Flags: $flags" > $wiki_file

# # Write combined travis button
# combined_tracker_real="${SPATIAL_HOME}/ClassCombined-Branch${branch}-Backend${type_todo}-Tracker/results"
# logger "Writing combined travis button..."
# init_travis_ci Combined $branch $type_todo

# for ac in ${types_list[@]}; do
#   if [[ "$ac" != *"Fixme"* ]]; then 
#     combined_tracker=${combined_tracker_real}
#   else
#     combined_tracker="/dev/null"
#   fi
#   logger "Collecting results for ${ac} apps, putting in ${wiki_file}"
#   cd ${SPATIAL_HOME}/regression_tests/${ac}/results
#   echo "

# # ${ac}:
# " | awk '{print toupper($0)}' >> $wiki_file
#   init_travis_ci $ac $branch $type_todo
#   update_log $wiki_file
#   push_travis_ci $ac $branch $type_todo
# done

# push_travis_ci Combined $branch $type_todo

# # Update regtest timestamp
# if [[ ${type_todo} = *"chisel"* ]]; then
#   update_regression_timestamp
# fi

# echo -e "\n\n***\n\n" >> $wiki_file

# # Link to logs
# pretty_name=Pretty_Hist_Branch_${branch}_Backend_${type_todo}.csv
# # echo -e "\n## [History log](https://raw.githubusercontent.com/wiki/stanford-ppl/spatial/${branch}_Regression_Test_History.csv) \n" >> $wiki_file
# echo -e "\n## [Pretty History Log](https://raw.githubusercontent.com/wiki/stanford-ppl/spatial-lang/${pretty_name}) \n" >> $wiki_file
# # echo -e "\n## [Performance Results](https://www.dropbox.com/s/a91ra3wvdyr3x5b/Performance_Results.xlsx?dl=0) \n" >> $wiki_file

# stamp_app_comments
# stamp_commit_msgs
# }

collect_results_sbt() {
logger "Removing old wiki directory..."
rm -rf $WIKI_HOME
cd $SPATIAL_HOME
logger "Cloning wiki to avoid conflicts..."
git clone git@github.com:stanford-ppl/spatial-lang.wiki.git  > /dev/null 2>&1
git checkout -f HEAD  > /dev/null 2>&1
logger "Cloning done!"
logger "Cleaning old markdown file..."
rm $wiki_file > /dev/null 2>&1
touch $wiki_file
logger "Putting timestamp in wiki"
duration=$SECONDS
get_flags
flags_file=`ls | grep "flags.*log"`

echo -e "
Time elapsed: $(($duration / 60)) minutes, $(($duration % 60)) seconds
" > $wiki_file

cat $flags_file >> $wiki_file

echo -e "
Results
-------

" >> $wiki_file

# Write combined travis button
combined_tracker_real="${SPATIAL_HOME}/ClassCombined-Branch${branch}-Backend${type_todo}-Tracker/results"
logger "Writing combined travis button..."
init_travis_ci Combined $branch $type_todo


results_file=`ls | grep "regression.*log"`
sort $results_file > sorted_results.log
sed -i "s/Pass/**Pass**/g" sorted_results.log

# Send results to wiki
echo -e "
### Unit
" >> $wiki_file
sed -n "/\.Unit\./p" sorted_results.log > tmp
sed -i "s/\[newline\]/\n↳/g" tmp
sed -i "s/\`/\\\\\`/g" tmp
sed -i "s/$/  /g" tmp
cat tmp >> $wiki_file

echo -e "
### Dense
" >> $wiki_file
sed -n "/\.Dense\./p" sorted_results.log > tmp
sed -i "s/\[newline\]/\n↳/g" tmp
sed -i "s/\`/\\\\\`/g" tmp
sed -i "s/$/  /g" tmp
cat tmp >> $wiki_file

echo -e "
### Sparse
" >> $wiki_file
sed -n "/\.Sparse\./p" sorted_results.log > tmp
sed -i "s/\[newline\]/\n↳/g" tmp
sed -i "s/\`/\\\\\`/g" tmp
sed -i "s/$/  /g" tmp
cat tmp >> $wiki_file

echo -e "
### Fixme
" >> $wiki_file
sed -n "/\.Fixme\./p" sorted_results.log > tmp
sed -i "s/\[newline\]/\n↳/g" tmp
sed -i "s/\`/\\\\\`/g" tmp
sed -i "s/$/  /g" tmp
cat tmp >> $wiki_file


# Send results to travis button
sed "/\.Fixme\./d" sorted_results.log > tmp
sed -i "s/\[newline\]/\n↳/g" tmp
sed -i "s/\`/\\\\\`/g" tmp
sed -i "s/$/  /g" tmp
cat tmp >> $combined_tracker_real

push_travis_ci Combined $branch $type_todo

# Update regtest timestamp
if [[ ${type_todo} = *"chisel"* ]]; then
  update_regression_timestamp
fi

echo -e "\n\n***\n\n" >> $wiki_file

# Link to logs

if [[ $branch = "fpga" ]]; then
  gspread_hash='1CMeHtxCU4D2u12m5UzGyKfB3WGlZy_Ycw_hBEi59XH8'
elif [[ $branch = "develop" ]]; then
  gspread_hash='13GW9IDtg0EFLYEERnAVMq4cGM7EKg2NXF4VsQrUp0iw'
elif [[ $branch = "retime" ]]; then
  gspread_hash='1glAFF586AuSqDxemwGD208yajf9WBqQUTrwctgsW--A'
elif [[ $branch = "syncMem" ]]; then
  gspread_hash='1TTzOAntqxLJFqmhLfvodlepXSwE4tgte1nd93NDpNC8'
elif [[ $branch = "pre-master" ]]; then
  gspread_hash='18lj4_mBza_908JU0K2II8d6jPhV57KktGaI27h_R1-s'
elif [[ $branch = "master" ]]; then
  gspread_hash='1eAVNnz2170dgAiSywvYeeip6c4Yw6MrPTXxYkJYbHWo'
else
  gspread_hash='NA'
fi

if [[ $gspread_hash != "NA" ]]; then
  gspread_link="https://docs.google.com/spreadsheets/d/${gspread_hash}"
  echo -e "\n## [Performance Results](${gspread_link}) \n" >> $wiki_file
fi
# pretty_name=Pretty_Hist_Branch_${branch}_Backend_${type_todo}.csv
# # echo -e "\n## [History log](https://raw.githubusercontent.com/wiki/stanford-ppl/spatial/${branch}_Regression_Test_History.csv) \n" >> $wiki_file
# echo -e "\n## [Pretty History Log](https://raw.githubusercontent.com/wiki/stanford-ppl/spatial-lang/${pretty_name}) \n" >> $wiki_file
# # echo -e "\n## [Performance Results](https://www.dropbox.com/s/a91ra3wvdyr3x5b/Performance_Results.xlsx?dl=0) \n" >> $wiki_file

stamp_app_comments
stamp_commit_msgs
}

update_regression_timestamp() {
  old=`pwd`
  cd ${SPATIAL_HOME}
  git clone git@github.com:mattfel1/Trackers.git
  mv Trackers timestamps
  cd timestamps
  git checkout timestamps
  rm timestamp_${branch}.png
  rm timestamp_${branch}_text
  touch timestamp_${branch}_text
  echo "text 0,0 \"" > timestamp_${branch}_text
  date +"%a %b %d" >> timestamp_${branch}_text
  date +"%I:%M:%S %p" >> timestamp_${branch}_text
  echo "\"" >> timestamp_${branch}_text
  convert -size 80x30 xc:white -pointsize 12 -fill black -draw @timestamp_${branch}_text timestamp_${branch}.png
  git add timestamp_${branch}.png
  git commit -m "update $branch timestamp"
  git push
  cd $old
}

stamp_app_comments() {
  cd ${SPATIAL_HOME}/apps/src
  echo -e "\n# Pass Comments:\n" >> $wiki_file
  find . -maxdepth 3 -type f -exec grep PASS {} \; | grep "^PASS: \(.*\).*\*" | sed "s/PASS:.*(/> (/g" | sed "s/$/\n/g" >> $wiki_file
}


## $1 - test class (unit, dense, etc)
## $2 - branch
## $3 - backend
init_travis_ci() {

  # Pull Tracker repos
  goto=(`pwd`)
  cd ${SPATIAL_HOME}
  cmd="git clone git@github.com:mattfel1/Trackers.git"
  logger "Pulling TRAVIS CI buttons with command: $cmd"
  eval "$cmd" >> /tmp/log
  if [ -d "Trackers" ]; then
    logger "Repo Tracker exists, prepping it..."
    trackbranch="Class${1}-Branch${2}-Backend${3}-Tracker"
    mv ${SPATIAL_HOME}/Trackers ${SPATIAL_HOME}/${trackbranch}
    cd ${SPATIAL_HOME}/${trackbranch}
    logger "Checking if  branch $trackbranch exists..."
    wc=(`git branch -a | grep "remotes/origin/${trackbranch}" | wc -l`)
    if [[ $wc = 0 ]]; then
      logger "Does not exist! Making it..."
      git checkout -b ${trackbranch}
      git push --set-upstream origin ${trackbranch}
    else
      logger "Exists! Switching to it..."
      cmd="git checkout ${trackbranch}"
      eval "$cmd"
    fi

      echo "# Tracker
Travis-CI tracker for $1 tests on $2 branch with $3 backend
[![Build Status](https://travis-ci.org/mattfel1/Tracker.svg?branch=${trackbranch})](https://travis-ci.org/mattfel1/Tracker)

Based on https://github.com/stanford-ppl/spatial/wiki/${2}Branch-${3}Test-Regression-Tests-Status" > README.md
      echo "#!/bin/bash

if [ ! -f results ]; then
  echo \"FAIL: No results found\"
  exit 1
fi

errors=(\`cat results | grep -i \"fail\\|unknown\" | wc -l\`)
if [[ \$errors != 0 ]]; then
  cat results
  echo \"FAIL: Errors found\"
  exit 1
else
  echo \"Success\"
  exit 0
fi" > status.sh

echo "language: c
notifications:
  slack:
    rooms:
      - plasticine-arch:kRp0KfrygHiq2wCMrcgMogBW#regression
  email:
    recipients: mattfel@stanford.edu
    on_failure: never # default: always
script:
  - sudo bash ./status.sh # Is sudo now needed for travis?
" > .travis.yml

    tracker="${SPATIAL_HOME}/${trackbranch}/results"
    ls | grep -v travis | grep -v status | grep -v README | grep -v git | xargs rm -rf
    cp $packet ${SPATIAL_HOME}/${trackbranch}/
  else 
    logger "Repo Tracker does not exist! Skipping Travis..."
  fi
  cd ${goto}
}

## $1 - test class (unit, dense, etc)
## $2 - branch
## $3 - backend
push_travis_ci() {
  trackbranch="Class${1}-Branch${2}-Backend${3}-Tracker"

  # Pull Tracker repos
  goto=(`pwd`)
  if [ -d "${SPATIAL_HOME}/${trackbranch}" ]; then
    logger "Repo Tracker exists, pushing it..."
    cd ${SPATIAL_HOME}/${trackbranch}
    git pull
    git add -A
    git commit -m "auto update"
    git push | tee -a /tmp/log
  else
    logger "Repo Tracker does not exist, skipping it!"
  fi
  cd ${goto}
}


launch_tests_sbt() {
  cd ${SPATIAL_HOME}
  if [[ $type_todo = "chisel" ]]; then
    cd apps
    ah=`git rev-parse HEAD`
    cd ${SPATIAL_HOME}
    captype="Chisel"
    export CLOCK_FREQ_MHZ="NA"
    export timestamp=`git show -s --format=%ci`
    tid=`python3 ${SPATIAL_HOME}/utilities/gdocs.py "prepare_sheet" "$spatial_hash" "$ah" "$timestamp" "$branch"`
    echo $tid > ${SPATIAL_HOME}/tid
    echo ${spatial_hash} > ${SPATIAL_HOME}/hash
    echo $ah > ${SPATIAL_HOME}/ahash
  else
    captype="Scala"
  fi
  bash bin/regression $thredz $branch $captype
}
