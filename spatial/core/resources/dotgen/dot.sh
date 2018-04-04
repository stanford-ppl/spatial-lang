#dot -Tpdf main.dot > main.pdf && open main.pdf
if [ "$(uname)" == "Darwin" ]; then
	# Under Mac OS X platform        
	dot -Tsvg main.dot > main.svg && open -a "Safari" main.svg
elif [ "$(expr substr $(uname -s) 1 5)" == "Linux" ]; then
  # Under GNU/Linux platform
	dot -Tsvg main.dot > main.svg && open -a "Google Chrome" main.svg
fi
