rm arria10_argInOuts.vcd
iverilog -o arria10Test Top_DUT.v Arria10_tb.v RetimeShiftRegister.v SRAMVerilogAWS.v
vvp arria10Test
echo "regenerated"
