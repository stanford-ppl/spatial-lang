###.PHONY: spatial

all: dsl

dsl:
	bin/init-dsl.sh 0 1

repub:
	bin/init-dsl.sh 1 0

both:
	bin/init-dsl.sh 1 1

clean:
	bin/clean-dsl.sh

