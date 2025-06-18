#!/usr/bin/env bash

export AGENT_APPLICATION=..
export SPRING_PROFILES_ACTIVE=shell,starwars,docker-desktop

./support/agent.sh
