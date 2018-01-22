declare -a arr=("BasicDRAMReads" "DRAM0Stores" "DRAM2Stores" "DRAMLoadFirstEle" "DRAMLoads" "DRAMRWTest" "DRAMWriteTest" "InOutArg" "OneBurstOneLoad" "OneBurstOneStore" "OneBurstTwoStore" "OneBurstOneStoreArgs" "SRAMRWTest")
CMD=./bin/arria10_debuggers/regen_cpp.sh
for i in "${arr[@]}"
do
  echo "$i"
  # screen -S $i -dm bash ${CMD} $i
  bash ${CMD} $i
done
