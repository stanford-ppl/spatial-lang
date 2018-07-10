rm -rf gen/$1
bin/spatial $1 --sim
bash $1.sim | tee sim.log
