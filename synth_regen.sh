CMD="make zcu"

rm -rf gen/$1
bin/spatial $1 --synth --retime --instrumentation
echo "Working on synthesizing"
cd gen/$1 && screen -S $1 -dm ${CMD}
