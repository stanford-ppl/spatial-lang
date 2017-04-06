###.PHONY: spatial
.PHONY: apps

all: lang

lang:
	bin/init-dsl.sh 1

apps:
	bin/init-dsl.sh 2

release:
	bin/init-dsl.sh 3

clean:
	bin/clean-dsl.sh
