# declare -a arr=("Lab1Part1RegExample" "Lab1Part2DramSramExample"
# "Lab1Part4FIFOExample" "Lab1Part5FILOExample" "Lab1Part6ReduceExample"
# "Lab1Part7FoldExample" "Lab2Part1SimpleMemReduce" "Lab2Part2SimpleMemFold"
# "Lab2Part3BasicCondFSM" "Lab2Part4LUT" "Lab2Part5GEMM")

# declare -a arr=("DotProduct_1_1_16_Int" "DotProduct_4_4_16_Int")
declare -a arr=("DotProduct_1_256_256_Int")
CMD=regen.sh
for i in "${arr[@]}"
do
  echo "$i"
  screen -S $i -dm bash ${CMD} $i
  scp ./gen/${i}/${i}.tar.gz root@arria10.stanford.edu:~/labs/
done
