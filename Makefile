###.PHONY: spatial
.PHONY: apps spatial compile publish-local clean

BRANCH?=fpga

all: spatial

spatial:
	$(info $$BRANCH is [${BRANCH}])
	sbt spatial/assembly

apps:
	sbt apps/assembly

compile:
	sbt compile

publish-local:
	cd scala-virtualized; \
	sbt publish-local; \
	cd ../argon; \
	sbt publish-local; \
	cd ../spatial; \
	sbt publish-local

switch:
	sh -c 'git pull'
	sh -c 'git checkout origin/${BRANCH}'
	sh -c 'git pull'
	sh -c 'git submodule foreach --recursive git pull'
	sh -c 'git submodule foreach --recursive checkout origin/${BRANCH}'

	
clean:
	sbt clean
