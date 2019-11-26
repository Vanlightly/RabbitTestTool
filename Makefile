SHELL := bash# we want bash behaviour in all shell invocations
PLATFORM := $(shell uname)

### DEPS ###
#
DOCKER_DARWIN := /usr/local/bin/docker
ifeq ($(PLATFORM),Darwin)
DOCKER ?= $(DOCKER_DARWIN)
$(DOCKER):
	@brew cask install docker
endif
DOCKER_LINUX := /usr/bin/docker
ifeq ($(PLATFORM),Linux)
DOCKER ?= $(DOCKER_LINUX)
$(DOCKER): $(CURL)
	@sudo apt-get update && \
	sudo apt-get install apt-transport-https gnupg-agent && \
	$(CURL) -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo apt-key add - && \
	APT_KEY_DONT_WARN_ON_DANGEROUS_USAGE=true apt-key finger | \
	  grep --quiet "9DC8 5822 9FC7 DD38 854A  E2D8 8D81 803C 0EBF CD88" && \
	echo "deb https://download.docker.com/linux/ubuntu $$(lsb_release -c -s) stable" | \
	  sudo tee /etc/apt/sources.list.d/docker.list && \
	sudo apt-get update && sudo apt-get install docker-ce docker-ce-cli containerd.io && \
	sudo adduser $$USER docker && newgrp docker && sudo service restart docker
endif

CURL ?= /usr/bin/curl
ifeq ($(PLATFORM),Linux)
$(CURL):
	@sudo apt-get update && sudo apt-get install curl
endif

### TARGETS ###
#
.DEFAULT_GOAL := help

.PHONY: build
build: $(DOCKER) ## b  | Build JAR
	@$(DOCKER) run \
	  --rm \
	  --interactive --tty \
	  --workdir /workspace \
	  --volume $(CURDIR):/workspace \
	  --volume rabbittesttool-maven-cache:/root/.m2 \
	  maven:3.6-jdk-8 \
	  mvn clean package
.PHONY: b
b: build

.PHONY: help
help:
	@awk -F"[:#]" '/^[^\.][a-zA-Z\._\-]+:+.+##.+$$/ { printf "\033[36m%-24s\033[0m %s\n", $$1, $$4 }' $(MAKEFILE_LIST) \
	| sort
