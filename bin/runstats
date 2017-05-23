#!/bin/bash

files=( "DotProduct" "OuterProduct" "TPCHQ6" "BlackScholes" "MatMult_outer" "MatMult_inner" "GDA" "LogReg" "SGD" "Kmeans" "SMV" "BFS" "PageRank")
rm -rf dse.log
for app in "${files[@]}"
do
  echo $app
  rm -r gen/$app
  bin/spatial $app --cgra+ 2>&1 | tee -a stats.log
done
