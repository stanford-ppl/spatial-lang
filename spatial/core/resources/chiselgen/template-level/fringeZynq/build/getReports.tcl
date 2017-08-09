source clockFreq.tcl

## TARGET_ARCH must either be ZC706 or Zedboard
set TARGET ZC706

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

## Create a second project to build the design
open_project ./project_1/project_1.xpr -part $PART

open_run -name synthDesign synth_1
report_timing_summary -file ./REPORT_synth_timing_summary.rpt
report_utilization -packthru -file ./REPORT_synth_utilization.rpt
report_utilization -packthru -hierarchical -hierarchical_depth 20 -hierarchical_percentages -file ./REPORT_synth_utilization_hierarchical.rpt
report_ram_utilization -detail -file ./REPORT_synth_ram_utilization.rpt

# Reports
open_run -name implDesign impl_1
report_timing_summary -file ./REPORT_par_timing_summary.rpt
report_utilization -packthru -file  ./REPORT_par_utilization.rpt
report_utilization -packthru -hierarchical -hierarchical_depth 20 -hierarchical_percentages -file  ./REPORT_par_utilization_hierarchical.rpt
report_ram_utilization -detail -file ./REPORT_par_ram_utilization.rpt

close_project
