declare -a arr=("BasicDRAMReads" "DRAM0Stores" "DRAM2Stores" "DRAMLoadFirstEle" "DRAMLoads" "DRAMRWTest" "DRAMWriteTest" "InOutArg" "OneBurstOneLoad" "OneBurstOneLoadRegFile" "OneBurstOneStore" "OneBurstOneStoreArgs" "OneBurstTwoStore" "SRAMRWTest" "TwoBurst" "OneBurstTwoLoad")
# CMD=./bin/arria10_debuggers/regen_cpp.sh
CMD=regen.sh
for i in "${arr[@]}"
do
  echo "$i"
  screen -S $i -dm bash ${CMD} $i
  # bash ${CMD} $i
done
