#!/bin/sh

# Script for creating the window into /remote/regression/mapping for the gh README

# Clean old dir and repo
rm -rf /home/mattfel/Window
cd /home/mattfel
git clone git@github.com:mattfel1/Window
cd Window
git checkout --orphan master2
rm -f window
rm -f window.png

# Make window
echo "text 0,0 \"" > /home/mattfel/Window/window
echo -e "Current tests found in /remote/regression/mapping: \n" >> /home/mattfel/Window/window
lines=`ls -l /remote/regression/mapping | wc -l`
ls -l /remote/regression/mapping | awk '{print $3, $9}' >> /home/mattfel/Window/window
d=`date`
h=`hostname`
echo -e "\nLast updated on ${h} at ${d}" >> /home/mattfel/Window/window
echo "\"" >> /home/mattfel/Window/window
height=$(($lines*25+80))
`convert -size 800x${height} xc:black -pointsize 20 -fill white -draw @/home/mattfel/Window/window /home/mattfel/Window/window.png`

# Upload window

git add window
git add window.png
git commit -am "upd8"
git branch -D master
git branch -m master
git push -f origin master
