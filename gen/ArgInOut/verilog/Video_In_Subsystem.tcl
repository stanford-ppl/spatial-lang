# qsys scripting (.tcl) file for Video_In_Subsystem
package require -exact qsys 16.0

create_system {Video_In_Subsystem}

set_project_property DEVICE_FAMILY {Cyclone V}
set_project_property DEVICE {5CSEMA5F31C6}
set_project_property HIDE_FROM_IP_CATALOG {false}

# Instances and instance parameters
# (disabled instances are intentionally culled)
add_instance Edge_Detection_Subsystem Edge_Detection_Subsystem 1.0

add_instance Sys_Clk clock_source 16.1
set_instance_parameter_value Sys_Clk {clockFrequency} {50000000.0}
set_instance_parameter_value Sys_Clk {clockFrequencyKnown} {0}
set_instance_parameter_value Sys_Clk {resetSynchronousEdges} {NONE}

add_instance Video_In altera_up_avalon_video_decoder 16.1
set_instance_parameter_value Video_In {video_source} {On-board Video In (NTSC or PAL)}

add_instance Video_In_CSC altera_up_avalon_video_csc 16.1
set_instance_parameter_value Video_In_CSC {csc_type} {444 YCrCb to 24-bit RGB}

add_instance Video_In_Chroma_Resampler altera_up_avalon_video_chroma_resampler 16.1
set_instance_parameter_value Video_In_Chroma_Resampler {input_type} {YCrCb 422}
set_instance_parameter_value Video_In_Chroma_Resampler {output_type} {YCrCb 444}

add_instance Video_In_Clipper altera_up_avalon_video_clipper 16.1
set_instance_parameter_value Video_In_Clipper {width_in} {720}
set_instance_parameter_value Video_In_Clipper {height_in} {244}
set_instance_parameter_value Video_In_Clipper {drop_left} {40}
set_instance_parameter_value Video_In_Clipper {drop_right} {40}
set_instance_parameter_value Video_In_Clipper {drop_top} {2}
set_instance_parameter_value Video_In_Clipper {drop_bottom} {2}
set_instance_parameter_value Video_In_Clipper {add_left} {0}
set_instance_parameter_value Video_In_Clipper {add_right} {0}
set_instance_parameter_value Video_In_Clipper {add_top} {0}
set_instance_parameter_value Video_In_Clipper {add_bottom} {0}
set_instance_parameter_value Video_In_Clipper {add_value_plane_1} {0}
set_instance_parameter_value Video_In_Clipper {add_value_plane_2} {0}
set_instance_parameter_value Video_In_Clipper {add_value_plane_3} {0}
set_instance_parameter_value Video_In_Clipper {add_value_plane_4} {0}
set_instance_parameter_value Video_In_Clipper {color_bits} {16}
set_instance_parameter_value Video_In_Clipper {color_planes} {1}

add_instance Video_In_DMA altera_up_avalon_video_dma_controller 16.1
set_instance_parameter_value Video_In_DMA {mode} {From Stream to Memory}
set_instance_parameter_value Video_In_DMA {addr_mode} {X-Y}
set_instance_parameter_value Video_In_DMA {start_address} {134217728}
set_instance_parameter_value Video_In_DMA {back_start_address} {134217728}
set_instance_parameter_value Video_In_DMA {width} {320}
set_instance_parameter_value Video_In_DMA {height} {240}
set_instance_parameter_value Video_In_DMA {color_bits} {16}
set_instance_parameter_value Video_In_DMA {color_planes} {1}
set_instance_parameter_value Video_In_DMA {dma_enabled} {0}

add_instance Video_In_RGB_Resampler altera_up_avalon_video_rgb_resampler 16.1
set_instance_parameter_value Video_In_RGB_Resampler {input_type} {24-bit RGB}
set_instance_parameter_value Video_In_RGB_Resampler {output_type} {16-bit RGB}
set_instance_parameter_value Video_In_RGB_Resampler {alpha} {1023}

add_instance Video_In_Scaler altera_up_avalon_video_scaler 16.1
set_instance_parameter_value Video_In_Scaler {width_scaling} {0.5}
set_instance_parameter_value Video_In_Scaler {height_scaling} {1}
set_instance_parameter_value Video_In_Scaler {include_channel} {0}
set_instance_parameter_value Video_In_Scaler {width_in} {640}
set_instance_parameter_value Video_In_Scaler {height_in} {240}
set_instance_parameter_value Video_In_Scaler {color_bits} {16}
set_instance_parameter_value Video_In_Scaler {color_planes} {1}

# exported interfaces
add_interface edge_detection_control_slave avalon slave
set_interface_property edge_detection_control_slave EXPORT_OF Edge_Detection_Subsystem.edge_detection_control_slave
add_interface sys_clk clock sink
set_interface_property sys_clk EXPORT_OF Sys_Clk.clk_in
add_interface sys_reset reset sink
set_interface_property sys_reset EXPORT_OF Sys_Clk.clk_in_reset
add_interface video_in conduit end
set_interface_property video_in EXPORT_OF Video_In.external_interface
add_interface video_in_dma_control_slave avalon slave
set_interface_property video_in_dma_control_slave EXPORT_OF Video_In_DMA.avalon_dma_control_slave
add_interface video_in_dma_master avalon master
set_interface_property video_in_dma_master EXPORT_OF Video_In_DMA.avalon_dma_master

# connections and connection parameters
add_connection Video_In_Chroma_Resampler.avalon_chroma_source Edge_Detection_Subsystem.video_stream_sink

add_connection Video_In_Clipper.avalon_clipper_source Video_In_Scaler.avalon_scaler_sink

add_connection Video_In_CSC.avalon_csc_source Video_In_RGB_Resampler.avalon_rgb_sink

add_connection Video_In.avalon_decoder_source Video_In_Chroma_Resampler.avalon_chroma_sink

add_connection Video_In_RGB_Resampler.avalon_rgb_source Video_In_Clipper.avalon_clipper_sink

add_connection Video_In_Scaler.avalon_scaler_source Video_In_DMA.avalon_dma_sink

add_connection Edge_Detection_Subsystem.video_stream_source Video_In_CSC.avalon_csc_sink

add_connection Sys_Clk.clk Video_In.clk

add_connection Sys_Clk.clk Video_In_Chroma_Resampler.clk

add_connection Sys_Clk.clk Video_In_CSC.clk

add_connection Sys_Clk.clk Video_In_RGB_Resampler.clk

add_connection Sys_Clk.clk Video_In_Clipper.clk

add_connection Sys_Clk.clk Video_In_Scaler.clk

add_connection Sys_Clk.clk Video_In_DMA.clk

add_connection Sys_Clk.clk Edge_Detection_Subsystem.sys_clk

add_connection Sys_Clk.clk_reset Video_In.reset

add_connection Sys_Clk.clk_reset Video_In_Chroma_Resampler.reset

add_connection Sys_Clk.clk_reset Video_In_CSC.reset

add_connection Sys_Clk.clk_reset Video_In_RGB_Resampler.reset

add_connection Sys_Clk.clk_reset Video_In_Clipper.reset

add_connection Sys_Clk.clk_reset Video_In_Scaler.reset

add_connection Sys_Clk.clk_reset Video_In_DMA.reset

add_connection Sys_Clk.clk_reset Edge_Detection_Subsystem.sys_reset

# interconnect requirements
set_interconnect_requirement {$system} {qsys_mm.clockCrossingAdapter} {HANDSHAKE}
set_interconnect_requirement {$system} {qsys_mm.maxAdditionalLatency} {1}
set_interconnect_requirement {$system} {qsys_mm.enableEccProtection} {FALSE}
set_interconnect_requirement {$system} {qsys_mm.insertDefaultSlave} {FALSE}

save_system {Video_In_Subsystem.qsys}
