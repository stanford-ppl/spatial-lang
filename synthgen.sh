CMD="make zcu"
APP_DIR="./apps/src/"
new_app_name="synth_${1}_`date +%s`"
echo -e "\e[1;31mApp Name: ${new_app_name}\e[0m"
cp ${APP_DIR}/${1}.scala ${APP_DIR}/${new_app_name}.scala
sed -i "s/object ${1} extends SpatialApp {.*/object ${new_app_name} extends SpatialApp {/g" ${APP_DIR}/${new_app_name}.scala
bin/spatial ${new_app_name} --synth --retime --instrumentation
echo -e "\e[1;31mWorking on synthesizing ${new_app_name} at `date`\e[0m"
cd gen/${new_app_name} && screen -S ${new_app_name} -dm ${CMD}
