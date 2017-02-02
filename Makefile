###.PHONY: spatial 
.PHONY: apps

all: dsl

dsl:
	bin/init-dsl.sh 0 1 0

repub:
	bin/init-dsl.sh 1 0 0

both:
	bin/init-dsl.sh 1 1 0

apps:
	bin/init-dsl.sh 0 0 1

clean:
	bin/clean-dsl.sh

