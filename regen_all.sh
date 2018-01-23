declare -a arr=("BasicDRAMReads" "DRAM0Stores" "DRAM2Stores" "DRAMLoadFirstEle" "DRAMLoads" "DRAMRWTest" "DRAMWriteTest" "InOutArg" "OneBurstOneLoad" "OneBurstOneLoadRegFile" "OneBurstOneStore" "OneBurstOneStoreArgs" "OneBurstTwoStore" "SRAMRWTest" "TwoBurst" "OneBurstTwoLoad")
# CMD=./bin/arria10_debuggers/regen_cpp.sh
# CMD=regen.sh
for i in "${arr[@]}"
do
  echo "$i"
  # screen -S $i -dm bash ${CMD} $i
  # scp ./gen/${i}/${i}.tar.gz root@arria10:~/BasicMemTests/
  # bash ${CMD} $i
  scp ./1burst.csv root@arria10:~/BasicMemTests/$i/
done
