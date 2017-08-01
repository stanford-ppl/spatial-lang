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
  make apps > /tmp/log 2>&1
  logger "Spatial done!"
  logger "Checking if spatial made correctly..."
  errs=(`cat /tmp/log | grep "\[.*error.*\]" | wc -l`)
  if [[ $errs -gt 0 ]]; then
  	clean_exit 8 "Detected errors in spatial build (/tmp/log)"
  fi
  logger "Spatial probably made correctly but not sure how to check in argon spatial"

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
rm $packet

sleep 1000
stubborn_delete ${dirname}
mv /remote/regression/mapping/${tim}.${branch}.${type_todo}---${this_machine} /remote/regression/graveyard

ps aux | grep -ie mattfel | grep -v ssh | grep -v bash | grep -iv screen | grep -v receive | awk '{system("kill -9 " $2)}'

exit 1

}

## Function for collecting results and reporting in the markdown
collect_results() {
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
echo -e "
Time elapsed: $(($duration / 60)) minutes, $(($duration % 60)) seconds
* <---- indicates relative amount of work needed before app will **pass**" > $wiki_file

for ac in ${types_list[@]}; do
  logger "Collecting results for ${ac} apps, putting in ${wiki_file}"
  cd ${SPATIAL_HOME}/regression_tests/${ac}/results
  echo "

# ${ac}:
" | awk '{print toupper($0)}' >> $wiki_file
  init_travis_ci $ac $branch $type_todo
  update_log $wiki_file
  push_travis_ci $ac $branch $type_todo
done

# Update regtest timestamp
if [[ ${type_todo} = *"chisel"* ]]; then
  update_regression_timestamp
fi

echo -e "\n\n***\n\n" >> $wiki_file

# Link to logs
pretty_name=Pretty_Hist_Branch_${branch}_Backend_${type_todo}.csv
# echo -e "\n## [History log](https://raw.githubusercontent.com/wiki/stanford-ppl/spatial/${branch}_Regression_Test_History.csv) \n" >> $wiki_file
echo -e "\n## [Pretty History Log](https://raw.githubusercontent.com/wiki/stanford-ppl/spatial-lang/${pretty_name}) \n" >> $wiki_file
# echo -e "\n## [Performance Results](https://www.dropbox.com/s/a91ra3wvdyr3x5b/Performance_Results.xlsx?dl=0) \n" >> $wiki_file

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

update_log() {
  #failure points
  # failed_execution_validation  
  # failed_execution_nonexistent_validation  
  # failed_execution_backend_crash  
  # failed_compile_backend_crash  
  # failed_app_spatial_compile 
  # failed_app_not_written 
  # failed_app_initialized

  perf_hist=72
  echo "" >> $1
  echo "" >> $1
  progress=(`find . -maxdepth 1 -type f | sort -r`)
  for p in ${progress[@]}; do
    pname=(`echo $p | sed "s/.*[0-9]\+_//g"`)
    cute_plot="[🗠](https://raw.githubusercontent.com/wiki/stanford-ppl/spatial-lang/comptimes_${branch}_${type_todo}_${pname}.csv)"
    if [[ $p == *"pass"* ]]; then
      echo "**$p**${cute_plot}  " | sed "s/\.\///g" | tee -a $1 $tracker > /dev/null
      t=(`sed -n '1p' $p`)
    elif [[ $p == *"failed_execution_validation"* ]]; then
      echo "<----${p}${cute_plot}  " | sed "s/\.\///g" | tee -a $1 $tracker > /dev/null
      t=0
    elif [[ $p == *"failed_execution_nonexistent_validation"* ]]; then
      echo "<--------${p}${cute_plot}  " | sed "s/\.\///g" | tee -a $1 $tracker > /dev/null
      t=0
    elif [[ $p == *"failed_execution_backend_crash"* || $p == *"failed_execution_hanging"* ]]; then
      echo "<------------${p}${cute_plot}  " | sed "s/\.\///g" | tee -a $1 $tracker > /dev/null
      t=0
    elif [[ $p == *"failed_compile_backend_crash"* || $p == *"failed_compile_cpp_crash"* ]]; then
      echo "<----------------${p}${cute_plot}  " | sed "s/\.\///g" | tee -a $1 $tracker > /dev/null
      t=0
    elif [[ $p == *"failed_app_spatial_compile"* ]]; then
      echo "<--------------------${p}${cute_plot}  " | sed "s/\.\///g" | tee -a $1 $tracker > /dev/null
      t=0
    elif [[ $p == *"failed_app_not_written"* ]]; then
      echo "<------------------------${p}${cute_plot}  " | sed "s/\.\///g" | tee -a $1 $tracker > /dev/null
      t=0
    elif [[ $p == *"failed_app_initialized"* ]]; then
      echo "<----------------------------${p}${cute_plot}  " | sed "s/\.\///g" | tee -a $1 $tracker > /dev/null
      t=0
    else
      echo "Unknown result: $p  " | sed "s/\.\///g" | tee -a $1 $tracker > /dev/null
      t=0
    fi

    # Update performance file
    perf_file="${SPATIAL_HOME}/spatial-lang.wiki/comptimes_${branch}_${type_todo}_${pname}.csv"
    if [ ! -f ${perf_file} ]; then
      echo "Compile times (in seconds) by commit (0 = failure)" > $perf_file
      echo "times, 0" >> $perf_file
    fi
    line="Spatial ${spatial_hash:0:5} | Argon ${argon_hash:0:5} | Virtualized ${virtualized_hash:0:5} | Spatial-apps ${apps_hash:0:5}"
    sed -i "2s/$/, $t/" ${perf_file}
    echo "$line" >> ${perf_file}

    # lines=(`cat $perf_file | wc -l`)
    # dline=$(($lines-$(($perf_hist-1))))
    # last=(`tail -n1 < $perf_file`)
    # last_color=(`echo ${last[@]} | sed "s/;.*//g"`)
    # if [[ "${last[@]}" = *"$2 $3"* ]]; then
    #   color=$last_color
    #   echo "[SPATIAL NOTICE] Using old color $color for app $p and hash $2 $3"
    # else
    #   if [ "$last_color" = "r" ]; then
    #     color="b"
    #     echo "[SPATIAL NOTICE] Using new color $color for app $p and hash $2 $3 from line ${last[@]}"
    #   else
    #     color="r"
    #     echo "[SPATIAL NOTICE] Using new color $color for app $p and hash $2 $3 from line ${last[@]}"
    #   fi
    # fi

    # echo -ne "\n${color};$t;$2 $3" >> $perf_file

    # # Hack to get rid of empty first line for new files
    # first=(`head -n1 < $perf_file`)
    # if [ "$first" = "" ]; then 
    #   sed -i -e "1d" $perf_file
    # fi

    # if [ $dline -gt $perf_hist ]; then
    #   sed -i -e "1d" $perf_file
    # fi

    # cmd="/usr/local/bin/python2.7 ${SPATIAL_HOME}/static/plotter.py ${branch}_${pname} ${SPATIAL_HOME}/spatial.wiki/"
    # eval "$cmd"


  done
}

update_histories() {

# Update history 
# history_file=${SPATIAL_HOME}/spatial.wiki/${branch}_Regression_Test_History.csv

# Get list of apps that have data
IFS=$'\n'
all_apps=(`cat ${wiki_file} | grep "^\*\*pass\|^<-\+failed" | sed "s/<-\+//g" | sed "s/^.*[0-9]\+\_//g" | sed "s/\*//g" | sed "s/\[🗠.*//g" | sort`)

# Determine type for each app to build headers list
for aa in ${all_apps[@]}; do
  if [[ ! "$last_aa" = "$aa" ]]; then
    a=(`echo $aa | sed "s/ //g" | sed "s/\[.*//g"`)
    for bb in ${test_list[@]}; do
      if [[ $bb == "$a|"* ]]; then
        type=(`echo $bb | awk -F'|' '{print $2}'`)
        headers=("${headers[@]}" "${type}|${a}")
      fi
    done
  fi
  last_aa=$aa
done

  # List of failure points
  # failed_app_initialized
  # failed_app_not_written
  # failed_app_spatial_compile
  # failed_compile_backend_crash
  # failed_execution_backend_crash
  # failed_execution_nonexistent_validation
  # failed_execution_validation

pretty_name=Pretty_Hist_Branch_${branch}_Backend_${type_todo}.csv
pretty_file=${SPATIAL_HOME}/spatial-lang.wiki/${pretty_name}

# Inject the new data to the history
key=(`cat ${pretty_file} | grep KEY | wc -l`)
if [[ $key = 0 ]]; then
    echo "00  KEY:
000 █ = Success
000 ▇ = failed_execution_validation  
000 ▆ = failed_execution_nonexistent_validation  
000 ▅ = failed_execution_backend_crash  
000 ▄ = failed_compile_backend_crash or failed_compile_cpp_crash  
000 ▃ = failed_app_spatial_compile 
000 ▂ = failed_app_not_written 
000 ▁ = failed_app_initialized
000 □ = unknown
1 
1
1
1" >> ${pretty_file}
fi
for aa in ${headers[@]}; do
  a=(`echo $aa | sed "s/^.*|//g" | sed "s/\[.*//g"`)
  dashes=(`cat ${wiki_file} | grep "[0-9]\+\_$a\(\ \|\*\|\[\)" | sed "s/\[🗠.*//g" | grep -oh "\-" | wc -l`)
  num=$(($dashes/4))
  if [ $num = 0 ]; then bar=█; elif [ $num = 1 ]; then bar=▇; elif [ $num = 2 ]; then bar=▆; elif [ $num = 3 ]; then bar=▅; elif [ $num = 4 ]; then bar=▄; elif [ $num = 5 ]; then bar=▃; elif [ $num = 6 ]; then bar=▂; elif [ $num = 7 ]; then bar=▁; else bar=□; fi

  infile=(`cat ${pretty_file} | grep $aa | wc -l`)
  if [[ $infile -gt 0 ]]; then # This test exists in history
    # Get last known datapoint and vector
    last=$(cat ${pretty_file} | grep "${aa}\ " | grep -o ".$")
    if [ $last = █ ]; then old_num=0; elif [ $last = ▇ ]; then old_num=1; elif [ $last = ▆ ]; then old_num=2; elif [ $last = ▅ ]; then old_num=3; elif [ $last = ▄ ]; then old_num=4; elif [ $last = ▃ ]; then old_num=5; elif [ $last = ▂ ]; then old_num=6; elif [ $last = ▁ ]; then old_num=7; else oldnum=8; fi
    if [[ $old_num = 0 && $num = 0 ]]; then vec=" "; elif [[ $old_num > $num ]]; then vec=↗; elif [[ $old_num < $num ]]; then vec=↘; else vec=→; fi
    # Edit file
    logger "app $aa from $last to $bar, numbers $old_num to $num"
    cmd="sed -i 's/\\(^${aa}\\ \\+.\\),,\\(.*\\)/\\1,,\\2${bar}/' ${pretty_file}" # Append bar
    eval "$cmd"
    cmd="sed -i 's/\\(^${aa}\ \+\\).,,\\(.*\\)/\\1${vec},,\\2/' ${pretty_file}" # Inject change vector
    eval "$cmd"
    # Shave first if too long
    numel=(`cat ${pretty_file} | grep "^$aa\ " | grep -oh "." | wc -l`)
    chars_before_bars=(`cat ${pretty_file} | grep "^$aa\ " | sed "s/,,.*/,,/g" | grep -oh "." | wc -l`)
    if [ $numel -gt $(($hist+$chars_before_bars)) ]; then 
      cmd="sed -i \"s/^${a}\([[:blank:]]*\),,./${a}\1,,/g\" ${pretty_file}"
      eval "$cmd" 
      logger "Shaving $aa in pretty history because its history exceeds $hist"
    fi
  else 
    logger "Detected $aa as a new app!  Adding to pretty history log"
    add=(`printf '%-50s' "$aa"`)
    echo "${add},,${bar}" >> ${pretty_file}
  fi
done

# Add category if this is a new one
for ac in ${types_list[@]}; do
  infile=(`cat ${pretty_file} | grep "${ac}:" | wc -l`)
  if [[ $infile -eq 0 ]]; then # add this category
    echo "${ac}:" >> ${pretty_file}
  fi
done

# Add commit hashes
infile=(`cat ${pretty_file} | grep "Z Latest Update" | wc -l`)
if [[ $infile -gt 0 ]]; then # add stamp
  cmd="sed -i \"s/Z Latest Update: .*/Z Latest Update: ${tim}/g\" ${pretty_file}"
  eval "$cmd"
else
  echo "Z Latest Update: ${tim}" >> ${pretty_file}
fi

# Append which combo this update is:
at=`date +"%Y-%m-%d_%H-%M-%S"`
line="ZZ ${at} - Spatial ${spatial_hash:0:5} | Argon ${argon_hash:0:5} | Virtualized ${virtualized_hash:0:5} | Spatial-apps ${apps_hash:0:5}"
echo "$line" >> ${pretty_file}

# Sort file
sort $pretty_file > ${pretty_file}.tmp
mv ${pretty_file}.tmp ${pretty_file}

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

# Helper function for creating script for vulture to use
## 1 - filename for this script
## 2 - type of app this is for (dense, sparse, etc.)
## 3 - id for this test (0, 1, 2, etc.)
## 4 - name of this app
## 5 - directory for this script
## 6 - args
create_script() {


  # List of failure points
  # failed_app_initialized
  # failed_app_not_written
  # failed_app_spatial_compile
  # failed_compile_backend_crash failed_compile_cpp_crash
  # failed_execution_backend_crash
  # failed_execution_nonexistent_validation
  # failed_execution_validation

  if [[ $6 = "none" ]]; then
  	args=""
  else
  	args=$6
  fi

  if [[ ${type_todo} = "scala" || ${type_todo} = "maxj" || ${type_todo} = "chisel" ]]; then
    echo "ok!" > /tmp/log
  else
    stamp_commit_msgs
    clean_exit 1 "Error! ${type_todo} type of regression test not yet supported."
  fi

  echo "
#!/bin/bash

# 1 - file string
# 2 - error message
# 3 - pass (1) or fail (0)
function report {
  date >> ${5}/log
  cd ${5}
  # mv build.sbt build.hideme # hide build.sbt so future compiles ignore this one
  rm ${SPATIAL_HOME}/regression_tests/${2}/results/*.${3}_${4}
  if [ \${3} = 1 ]; then
    echo \"[APP_RESULT] `date` - SUCCESS for ${3}_${4}\" >> ${log}
    cat ${5}/log | grep \"Design ran for\" >> ${log} 
    touch ${SPATIAL_HOME}/regression_tests/${2}/results/pass.${3}_${4}
    echo \${comp_time} >> ${SPATIAL_HOME}/regression_tests/${2}/results/pass.${3}_${4}
    cat ${5}/log | grep \"Kernel done, cycles\" | sed \"s/Kernel done, cycles = //g\" >> ${SPATIAL_HOME}/regression_tests/${2}/results/pass.${3}_${4}
    exit 0
  else
    echo \"[APP_RESULT] `date` - \${1} for ${3}_${4} (\${2} - ${5}/)\" >> ${log}
    touch ${SPATIAL_HOME}/regression_tests/${2}/results/\${1}.${3}_${4}
    exit 1
  fi
}


# Override env vars to point to a separate directory for this regression test
export SPATIAL_HOME=${SPATIAL_HOME}
if [[ ${this_machine} = *\"tflop\"* ]]; then # ugly hack to get tflops working
  export PATH=/usr/local/sbin:/usr/bin:/usr/local/bin:/usr/sbin:/sbin:/bin:/usr/games:/usr/local/games
else
  export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:/usr/games:/usr/local/games
fi
export ARGON_HOME=${ARGON_HOME}
export VIRTUALIZED_HOME=${VIRTUALIZED_HOME}
export VCS_HOME=/cad/synopsys/vcs/K-2015.09-SP2-7
export PATH=\$VCS_HOME/amd64/bin:\$PATH
export LM_LICENSE_FILE=27000@cadlic0.stanford.edu:$LM_LICENSE_FILE
export JAVA_HOME=\$(readlink -f \$(dirname \$(readlink -f \$(which java)))/..)
if [[ \${JAVA_HOME} = *"/jre"* ]]; then # ugly ass hack because idk wtf is going on with tucson
  export JAVA_HOME=\${JAVA_HOME}/..
fi
export _JAVA_OPTIONS=\"-Xmx24g\"
date >> ${5}/log" >> $1

  if [[ ${type_todo} = "scala" ]]; then
    echo "#export JAVA_HOME=/usr/
    " >> $1
  fi

  echo "# sleep \$((${3}*${spacing})) # Backoff time to prevent those weird file IO errors
cd ${SPATIAL_HOME}
  " >> $1

  # Include retiming if this is a retiming branch
  if [[ ${branch} = *"retim"* ]]; then
    retime_flag="--retiming"
  else
    retime_flag=""
  fi

  # Compile command
  if [[ ${type_todo} = "scala" ]]; then
    echo "# Compile app
${SPATIAL_HOME}/bin/spatial --sim --multifile=4 ${retime_flag} --out=regression_tests/${2}/${3}_${4}/out ${4} 2>&1 | tee -a ${5}/log
    " >> $1
  elif [[ ${type_todo} = "chisel" ]]; then
    echo "# Compile app
${SPATIAL_HOME}/bin/spatial --synth --multifile=4 ${retime_flag} --out=regression_tests/${2}/${3}_${4}/out ${4} 2>&1 | tee -a ${5}/log
    " >> $1
  fi

  # Check for compile errors
  echo "
cd ${5}
# Ensure app class exists
wc=\$(cat ${5}/log | grep \"Could not find or load main class\" | wc -l)
if [ \"\$wc\" -ne 0 ]; then
  report \"failed_app_not_written\" \"[STATUS] Declaring failure app_not_written\" 0
fi

# Check for compile errors
sed -i \"s/^ERROR.*ignored\./Ignoring silly LD_PRELOAD  e r r o r/g\" ${5}/log
sed -i \"s/error retrieving current directory/Ignoring getcwd e r r o r/g\" ${5}/log
sed -i \"s/error: illegal sharing of mutable object/Ignoring scattergather mutable sharing e r r o r/g\" ${5}/log
wc=\$(cat ${5}/log | grep \"error\" | wc -l)
if [ \"\$wc\" -ne 0 ]; then
  report \"failed_app_spatial_compile\" \"[STATUS] Declaring failure build_in_spatial\" 0
fi

# Extract compile time
comp_time=(\`cat log | grep \"Total time:\" | sed 's/.*time: //g' | sed 's/ seconds//g'\`)

# Compile backend
cd ${5}/out

// Turn off vcd and dram prints
sed -i 's/#define EPRINTF(...) fprintf/#define EPRINTF(...) \\/\\/fprintf/g' cpp/fringeVCS/commonDefs.h
sed -i 's/\\\$dumpfile/\\/\\/\\\$dumpfile/g' chisel/template-level/fringeVCS/Top-harness.sv
sed -i 's/\\\$dumpvars/\\/\\/\\\$dumpvars/g' chisel/template-level/fringeVCS/Top-harness.sv
sed -i 's/\\\$vcdplusfile/\\/\\/\\\$vcdplusfile/g' chisel/template-level/fringeVCS/Top-harness.sv
sed -i 's/\\\$vcdpluson/\\/\\/\\\$vcdpluson/g' chisel/template-level/fringeVCS/Top-harness.sv

make vcs 2>&1 | tee -a ${5}/log" >> $1
  if [[ ${type_todo} = "chisel" ]]; then
    echo "make vcs-sw 2>&1 | tee -a ${5}/log # Because sometimes it refuses to do this part..." >> $1
  fi

echo "
# Check for annoying sbt compile not working
wc=\$(cat ${5}/log | grep \"No rule to make target\" | wc -l)
if [ \"\$wc\" -gt 0 ]; then
  echo \"[APP_RESULT] Annoying SBT crashing on ${3}_${4}.  Rerunning...\" >> ${log}
  echo -e \"\n\n=========\nSecond Chance!\n==========\n\n\" >> ${5}/log
  cd ${5}" >> $1
  # Compile command
  if [[ ${type_todo} = "scala" ]]; then
    echo "  # Compile app
  cd ${SPATIAL_HOME}
  ${SPATIAL_HOME}/bin/spatial --sim --multifile=4 --out=regression_tests/${2}/${3}_${4}/out ${4} 2>&1 | tee -a ${5}/log
    " >> $1
  elif [[ ${type_todo} = "chisel" ]]; then
    echo "  # Compile app
  cd ${SPATIAL_HOME}
  ${SPATIAL_HOME}/bin/spatial --synth --multifile=4 --out=regression_tests/${2}/${3}_${4}/out ${4} 2>&1 | tee -a ${5}/log
    " >> $1
  fi
  echo "  cd ${5}/out
  make vcs 2>&1 | tee -a ${5}/log" >> $1
  if [[ ${type_todo} = "chisel" ]]; then
    echo "  make vcs-sw 2>&1 | tee -a ${5}/log # Because sometimes it refuses to do this part..." >> $1
  fi


  echo "fi


# Check for crashes in backend compilation
wc=\$(cat ${5}/log | grep \"\\[bitstream-sim\\] Error\\|recipe for target 'bitstream-sim' failed\\|Compilation failed\\|java.lang.IndexOutOfBoundsException\\|BindingException\\|ChiselException\\|\\[vcs-hw\\] Error\" | wc -l)
if [[ \"\$wc\" -ne 0 ]]; then
  report \"failed_compile_backend_crash\" \"[STATUS] Declaring failure compile_chisel chisel side\" 0
fi" >> $1
  if [[ ${type_todo} = "chisel" ]]; then
    echo "# Check for missing Top
if [[ ! -f ${5}/out/Top ]]; then
  report \"failed_compile_backend_crash\" \"[STATUS] Declaring failure compile_chisel chisel side\" 0
fi" >> $1
  fi

echo "wc=\$(cat ${5}/log | grep \"\\[Top_sim\\] Error\\|recipe for target 'Top_sim' failed\\|fatal error\\|\\[vcs-sw\\] Error\" | wc -l)
if [ \"\$wc\" -ne 0 ]; then
  report \"failed_compile_cpp_crash\" \"[STATUS] Declaring failure compile_chisel c++ side\" 0
fi

# Move on to runtime
rm ${SPATIAL_HOME}/regression_tests/${2}/results/*.${3}_${4}
touch ${SPATIAL_HOME}/regression_tests/${2}/results/failed_execution_hanging.${3}_${4}
chmod +x ${5}/out/run.sh
timeout 400 ${5}/out/run.sh \"${args}\" 2>&1 | tee -a ${5}/log

# Check for annoying vcs assertion and rerun if needed
wc=\$(cat ${5}/log | grep \"void FringeContextVCS::connect(): Assertion \\\`0' failed\" | wc -l)
if [ \"\$wc\" -gt 0 ]; then
  echo \"[APP_RESULT] Annoying VCS assertion thrown on ${3}_${4}.  Rerunning...\" >> ${log}
  echo -e \"\n\n=========\nSecond Chance!\n==========\n\n\" >> ${5}/log
  make vcs 2>&1 | tee -a ${5}/log
  make vcs-sw 2>&1 | tee -a ${5}/log # Because sometimes it refuses to do this part...
  timeout 400 bash ${5}/out/run.sh \"${args}\" 2>&1 | tee -a ${5}/log

  # Check second time for annoying assert
  wc=\$(cat ${5}/log | grep \"void FringeContextVCS::connect(): Assertion \\\`0' failed\" | wc -l)
  if [ \"\$wc\" -gt 1 ]; then
    echo \"[APP_RESULT] Annoying VCS assertion thrown on ${3}_${4}.  Rerunning...\" >> ${log}
    echo -e \"\n\n=========\nThird Chance!\n==========\n\n\" >> ${5}/log
    make vcs 2>&1 | tee -a ${5}/log
    make vcs-sw 2>&1 | tee -a ${5}/log # Because sometimes it refuses to do this part...
    timeout 400 bash ${5}/out/run.sh \"${args}\" 2>&1 | tee -a ${5}/log
  fi
fi
# Check for annoying refusal to run that happens in scala sometimes
wc=\$(cat ${5}/log | grep \"PASS\" | wc -l)
if [ \"\$wc\" -eq 0 ]; then
  echo \"[APP_RESULT] Annoying refusal to run ${3}_${4}.  Rerunning...\" >> ${log}
  echo -i \"\n\n=========\nSecond Chance!\n==========\n\n\" >> ${5}/log
  timeout 400 bash ${5}/out/run.sh \"${args}\" 2>&1 | tee -a ${5}/log
fi

# Check for runtime errors
wc=\$(cat ${5}/log | grep \"Error: App\\|Segmentation fault\" | wc -l)

# Check if app validated or not
if grep -q \"PASS: 1\" ${5}/log; then
  report \"pass\" \"Test ${3}_${4} passed!\" 1
elif grep -q \"PASS: true\" ${5}/log; then
  report \"pass\" \"Test ${3}_${4} passed!\" 1
elif grep -q \"PASS: 0\" ${5}/log; then
  report \"failed_execution_validation\" \"[STATUS] Declaring failure validation\" 0
elif grep -q \"PASS: false\" ${5}/log; then
  report \"failed_execution_validation\" \"[STATUS] Declaring failure validation\" 0
elif grep -q \"PASS: X\" ${5}/log; then
  report \"failed_execution_validation\" \"[STATUS] Declaring failure validation\" 0
elif [ \"\$wc\" -ne 0 ]; then
  report \"failed_execution_backend_crash\" \"[STATUS] Declaring failure backend_crash\" 0
else 
  report \"failed_execution_nonexistent_validation\" \"[STATUS] Declaring failure no_validation_check\" 0
fi" >> $1

}


# Helper function for launching regression tests
launch_tests() {
  # # Use magic to free unused semaphores
  # logger "Killing semaphores"
  # cmd="for semid in `ipcs -s | cut -d\" \" -f 2` ; do pid=`ipcs -s -i $semid | tail -n 2 | head -n 1 | awk '{print $5}'`; running=`ps --no-headers -p $pid | wc -l` ; if [ $running -eq 0 ] ; then ipcrm -s $semid; fi ; done"
  # $cmd 2> /dev/null
  # Free old screens
  logger "Killing old screen sessions"
  screen -ls | grep "${branch}_${type_todo}" | cut -d. -f1 | awk '{print $1}' | xargs kill
  screen -wipe
  # logger "Killing maxeleros (?) jobs"
  # ps aux | grep -ie mattfel | grep -v ssh | grep -v bash | awk '{system("kill -9 " $2)}'

  IFS=$'\n'
  # Collect the regression tests by searching for "// Regression (<type>)" tags
  annotated_list=(`grep -r --color=never "// Regression" ${SPATIAL_HOME}/apps/src`)
  test_list=()
  for a in ${annotated_list[@]}; do
    if [[ $a = *"object"*"extends SpatialApp"* ]]; then
      test_list+=(`echo $a | sed 's/^.*object //g' | sed 's/ extends .*\/\/ Regression (/|/g' | sed 's/) \/\/ Args: /|/g' | sed 's/ /_/g'`)
    else
      logger "Error setting up test for $a !!!"
    fi
  done

  # Assemble regression types
  for t in ${test_list[@]}; do
    # logger "Processing app: $t"
    tp=(`echo $t | awk -F'|' '{print $2}'`)
    if [[ ! ${types_list[*]} =~ "$tp" ]]; then
      types_list=("${types_list[@]}" $tp)
    fi
  done

  # Make reg test dir
  rm -rf ${SPATIAL_HOME}/regression_tests;mkdir ${SPATIAL_HOME}/regression_tests

  logger "Found these app classes: ${types_list[@]}"
  for ac in ${types_list[@]}; do 
    check_packet
    logger "Preparing vulture directory for $ac..."
    # Create vulture dir
    rm -rf ${SPATIAL_HOME}/regression_tests/${ac};mkdir ${SPATIAL_HOME}/regression_tests/${ac}
    mkdir ${SPATIAL_HOME}/regression_tests/${ac}/results
    cd ${SPATIAL_HOME}/regression_tests/${ac}

    # Create the package for each app
    i=0
    for t in ${test_list[@]}; do
      check_packet
      if [[ $t == *"|${ac}|"* && (${tests_todo} == "all" || $t == *"|${tests_todo}|"*) ]]; then
        appname=(`echo $t | sed 's/|.*$//g'`)
        appargs=(`echo $t | sed 's/.*|.*|//g' | sed 's/_/ /g'`)
        # Initialize results
        touch ${SPATIAL_HOME}/regression_tests/${ac}/results/failed_app_initialized.${i}_${appname}

        # Make dir for this vulture job
        vulture_dir="${SPATIAL_HOME}/regression_tests/${ac}/${i}_${appname}"
        rm -rf $vulture_dir;mkdir $vulture_dir
        cmd_file="${vulture_dir}/cmd"

        # Create script
        # logger "Writing script for ${i}_${appname}"
        create_script $cmd_file ${ac} $i ${appname} ${vulture_dir} "$appargs"

        # Run script
        start=$SECONDS
        logger "Running test: ${i}_${appname}"
        bash ${cmd_file}
        duration=$(($SECONDS-$start))
        logger "Completed test in $duration seconds"
        cd ${SPATIAL_HOME}/regression_tests/${ac}
        
        ((i++))
      fi
    done
    # # Run vulture
    # cd ${SPATIAL_HOME}/regression_tests/${ac}/
    # logger "Executing vulture script in ${ac} directory..."
    # bash ${SPATIAL_HOME}/bin/vulture.sh ${ac}_${branch}_${type_todo}
    # logger "Script executed!"

  done


}
