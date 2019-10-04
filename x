#!/bin/sh
gradle bootJar
./xbuild.jar "$@"
