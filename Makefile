###.PHONY: spatial
.PHONY: apps spatial compile publish-local clean

BRANCH?=fpga

all: spatial

spatial:
	$(info $$BRANCH is [${BRANCH}])
	sbt spatial/compile

apps:
	sbt apps/compile

compile:
	sbt compile

publish-local:
	cd scala-virtualized; \
	sbt publishLocal; \
	cd ../argon; \
	sbt publish-local; \
	cd ../spatial; \
	sbt publishLocal

assembly:
	sbt spatial/assembly

switch:
	sh -c 'git pull'
	sh -c 'git checkout origin/${BRANCH}'
	sh -c 'git pull'
	sh -c 'git submodule foreach --recursive git pull'
	sh -c 'git submodule foreach --recursive checkout origin/${BRANCH}'

resources:
	bash bin/update_resources.sh

clean:
	sbt clean

sim-clean:
	rm *.sim
	rm *.vcs

synth-clean:
	rm -rf ./gen
