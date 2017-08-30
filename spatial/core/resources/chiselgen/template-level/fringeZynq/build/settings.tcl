## TARGET_ARCH must either be ZC706 or Zedboard
set TARGET ZC706
#set TARGET Zedboard

switch $TARGET {
  "ZC706" {
    set BOARD xilinx.com:zc706:part0:1.4
    set PART xc7z045ffg900-2
  }
  "Zedboard" {
    set BOARD em.avnet.com:zed:part0:1.3
    set PART xc7z020clg484-1
  }
  default {
    puts "$TARGET" is not a valid target! Must either be 'ZC706' or 'Zedboard'
  }
}


