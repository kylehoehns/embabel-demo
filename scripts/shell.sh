#!/usr/bin/env bash

export AGENT_APPLICATION=..

#export MAVEN_PROFILE=enable-mcp

./support/check_env.sh || exit 1

cd ..
mvn -Dmaven.test.skip=true spring-boot:run
