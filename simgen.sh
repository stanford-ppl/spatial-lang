rm -rf gen/$1

new_app_name="sim_${1}_${2}_`date +%s`"
echo -e "\e[1;31mApp Name: ${new_app_name}\e[0m"
bin/spatial $1 --sim
bash $1.sim | tee sim.log
