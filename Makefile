###.PHONY: spatial 
.PHONY: apps

all: lang

lang:
	bin/init-dsl.sh 0 1 0

repub:
	bin/init-dsl.sh 1 0 0

full:
	bin/init-dsl.sh 1 1 1

apps:
	bin/init-dsl.sh 0 0 1

clean:
	bin/clean-dsl.sh

