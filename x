#!/bin/sh
rm xbuild.jar
rm -fR bin build
gradle clean bootjar
