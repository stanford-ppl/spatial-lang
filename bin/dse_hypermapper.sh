#!/usr/bin/env bash

benchmarks=("BlackScholes" "DotProduct" "GDA" "Kmeans" "MatMult_outer"  "OuterProduct" "Sobel" "SW" "TPCHQ6")

iters=(0,1,2,3,4,5,6,7,8,9)

for benchmark in "${benchmarks[@]}"
do

    for i in "${iters[@]}"
    do
        echo "bin/spatial ${benchmark} --experiment --threads 16"
        bin/spatial ${benchmark} --hypermapper --threads 16 2>&1 | tee ${benchmark}.log
        mv dse_hm/generic_data_array_RS_1000_max_AL_25_execution_0.csv "dse_hm/${benchmark}_data_${i}.csv"
        mv dse_hm/generic_pareto_RS_1000_max_AL_25_execution_0.csv "dse_hm/${benchmark}_pareto_${i}.csv"
    done
done
