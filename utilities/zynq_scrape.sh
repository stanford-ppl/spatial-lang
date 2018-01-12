#!/bin/sh

donelanes=`find . -name "*.tar.gz"`
donedirs=()
for d in ${donelanes[@]}; do
  donedirs+=(`echo $d | sed "s/\.\///g" | sed "s/\/out_.*//g"`)
done

for d in ${donedirs[@]}; do 
  cd $d
  scp ${d}.tar.gz mattfel@holodeck-zc706: 2>1 >/dev/null
  tmg=`grep -r "VIOLATED" . | grep -v "synth" | wc -l`
  if [ $tmg != 0 ]; then tmg=fail; else tmg=pass; fi
  echo "TIMING MET? -- $tmg"
  cd ..
  yn="aeou" 

  while [[ $yn != "" ]]; do
    ssh mattfel@holodeck-zc706 "mkdir ${d};tar -xvf ${d}.tar.gz -C $d;cd $d;mkdir verilog && mv accel.bit.bin verilog" 2>1 >/dev/null
    read -p "Args for ${d} ? (type \"skip\" to skip): " choice
    if [[ $choice != "skip" ]]; then
      python bin/scrape.py $d | xclip -selection c
      echo "Copied scraped data"
      ssh -t mattfel@holodeck-zc706 "cd ${d}; bash run.sh $choice | grep \"PASS\|Design done,\|does not exist\""
    fi

    if [[ $choice != "skip" ]]; then
      read -p "Hit enter to go to next test, or any input to redo this test: " yn
    else
      yn=""
    fi

  done
  ssh mattfel@holodeck-zc706 "rm -rf ${d}*" 2>1 >/dev/null

done

