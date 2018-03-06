rm -rf gen/$1
bin/spatial $1 --synth --retime --instrumentation --naming
cd gen/$1 && make arria10 | tee make.log
