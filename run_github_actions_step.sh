#!/bin/sh -ex
eval $(xbuild.jar https://github.com/xbuild-jar/xbuild-jar.git ${GITHUB_REF?})
./xbuildfile
./deploy-github
