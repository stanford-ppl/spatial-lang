cp ~/spatial-lang-arria10/spatial/core/resources/cppgen/fringeArria10/FringeContextArria10.h ~/spatial-lang-arria10/gen/$1/cpp/fringeArria10/FringeContextArria10.h
cd ~/spatial-lang-arria10/gen/$1/cpp && make clean && make
scp Top root@arria10:~/BasicMemTests/$1
