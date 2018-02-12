rm -rf gen/$1
bin/spatial $1 --synth --retime
cd gen/$1 && make arria10 | tee make.log && scp ${1}.tar.gz root@arria10.stanford.edu:~/
