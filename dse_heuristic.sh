#!/usr/bin/env bash

benchmarks=("BlackScholes", "DotProduct", "GDA", "Kmeans", "MatMult_outer", "MatMult_inner",  "OuterProduct", "Sobel", "SW", "TPCHQ6")

for benchmark in "${benchmarks[@]}"
do
   bin/spatial $benchmark --experiment --t 4
done
