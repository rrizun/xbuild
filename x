#!/bin/sh -ex
gradle jar
java -jar build/libs/xbuild-jar.jar "$@"