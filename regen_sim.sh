rm -rf gen/$1
bin/spatial $1 --sim
cd gen/$1 && make arria10 | tee make.log
