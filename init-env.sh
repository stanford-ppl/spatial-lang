#!/bin/bash

export JAVA_HOME=$(readlink -f $(dirname $(readlink -f $(which java)))/../../)

