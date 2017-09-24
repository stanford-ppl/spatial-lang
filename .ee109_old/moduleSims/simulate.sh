rm test.vcd
iverilog -o JP1Test Computer_System_Expansion_JP1.v Computer_System_Expansion_JP1_tb.v
vvp JP1Test
echo "regenerated"
