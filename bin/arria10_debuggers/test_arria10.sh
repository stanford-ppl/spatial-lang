# arm-linux-gnueabi-gcc -Wall -o read_sysid read_sysid.c
# arm-linux-gnueabi-gcc -Wall -o page_size get_page_size.c
# arm-linux-gnueabi-gcc -Wall -o read_sram read_sram.c
# arm-linux-gnueabi-gcc -Wall -o set_sdram_hps set_sdram_hps.c
# arm-linux-gnueabi-gcc -Wall -o read_sdram_hps read_sdram_hps.c
# scp set_sdram_hps root@arria10:~/
# scp read_sdram_hps root@arria10:~/

# DESIGN=read_sdram_hps
# DESIGN=clean
DESIGN=read_sdram_hps
arm-linux-gnueabi-gcc -Wall -o ${DESIGN} ${DESIGN}.c
scp ${DESIGN} root@arria10:~/
