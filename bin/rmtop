#!/bin/bash

app=$1

grep $app results.log
echo "rm gen/$app/Top.vcd ?"
read answer
if echo "$answer" | grep -iq "^y" ;then
	rm gen/$app/Top.vcd
	echo Yes
else
	echo No
fi
