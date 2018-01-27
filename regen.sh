rm -rf gen/$1
bin/spatial $1 --synth --instrumentation
cd gen/$1 && make arria10
