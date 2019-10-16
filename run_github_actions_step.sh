#!/bin/sh -ex
eval $(xbuild.jar ${GITHUB_REF?})
./xbuildfile
./deploy-github
