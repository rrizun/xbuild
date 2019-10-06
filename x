#!/bin/sh -ex
gradle bootJar
./xbuild.jar "$@"
