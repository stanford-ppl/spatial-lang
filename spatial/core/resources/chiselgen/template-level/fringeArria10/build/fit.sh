# file=~/quartus_16_fringe_ref/spatial-lang-arria10/gen/InOutArg/verilog-arria10/Top.v
# ip_dir=./ip
# design=pr_region_alternate
# ip=pr_region_alternate_Top_0.ip
#
# # regenerate qsys ip files
# cp $file ./arria10_fringe_templates
# rm -rf $ip_dir/$design/pr_region_alternate_Top_0
# qsys-generate ${design}.qsys --block-symbol-file --family="Arria 10" --part=10AS066N3F40E2SG --quartus-project=ghrd_10as066n2.qpf --rev=pr_alternate_synth
# qsys-generate ${design}.qsys --synthesis=VERILOG --family="Arria 10" --part=10AS066N3F40E2SG --quartus-project=ghrd_10as066n2.qpf --rev=pr_alternate_synth
#
#
# # regenerate ips and resynthesize the design
# quartus_ipgenerate --run_default_mode_op ghrd_10as066n2 -c pr_alternate_synth
# this step will need many other args...
# quartus_syn --read_settings_files=on --write_settings_files=off ghrd_10as066n2 -c pr_alternate_synth

# implement the design in pr_alternate_fit
quartus_cdb ghrd_10as066n2 -c pr_base --export_pr_static_block root_partition --snapshot final --file pr_base_static.qdb | tee report_0.log # can be skipped...
quartus_cdb ghrd_10as066n2 -c pr_alternate_synth --export_block root_partition --snapshot synthesized --file pr_alternate_synth.qdb | tee report_1.log
quartus_cdb ghrd_10as066n2 -c pr_alternate_fit --import_block root_partition --file pr_base_static.qdb | tee report_2.log
quartus_cdb ghrd_10as066n2 -c pr_alternate_fit --import_block pr_region --file pr_alternate_synth.qdb | tee report_3.log
quartus_fit ghrd_10as066n2 -c pr_alternate_fit | tee report_qfit.log
quartus_sta ghrd_10as066n2 -c pr_alternate_fit | tee report_qsta.log
quartus_asm ghrd_10as066n2 -c pr_alternate_fit | tee report_qasm.log

# generate rbf for all
quartus_cpf --hps -c output_files/pr_base.sof output_files/pr_base.rbf | tee report_cpf_hps.log
quartus_cpf -c output_files/pr_base.pr_region.pmsf output_files/pr_region_default.rbf | tee report_prdefault.log
quartus_cpf -c output_files/pr_alternate_fit.pr_region.pmsf output_files/pr_region_alt.rbf | tee report_pralt.log
