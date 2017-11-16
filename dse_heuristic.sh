#!/usr/bin/env bash

benchmarks=("BlackScholes" "DotProduct" "GDA" "Kmeans" "MatMult_outer"  "OuterProduct" "Sobel" "SW" "TPCHQ6")
###benchmarks=("MatMult_outer" "TPCHQ6")

for benchmark in "${benchmarks[@]}"
do
    echo "bin/spatial ${benchmark} --experiment --threads 16"
    bin/spatial ${benchmark} --experiment --threads 16 2>&1 | tee ${benchmark}.log
done
