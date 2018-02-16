declare -a arr=("BasicLoadTest" "Gibbs_Ising2D" "GDA" "Kmeans" "JPEG_Decompress" "OuterProduct" "MD_KNN" "MatMult_inner" "Sobel" "FFT_Transpose" "TRSM" "PageRank_Bulk")
# CMD=./bin/arria10_debuggers/regen_cpp.sh
CMD=regen.sh
for i in "${arr[@]}"
do
  echo "$i"
  screen -S $i -dm bash ${CMD} $i
  # scp ./gen/${i}/${i}.tar.gz root@arria10:~/BasicMemTests/
  # bash ${CMD} $i
  # scp ./1burst.csv root@arria10:~/BasicMemTests/$i/
done
