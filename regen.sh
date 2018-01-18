rm -rf gen/$1
bin/spatial $1 --synth
cd gen/$1 && make arria10-hw
