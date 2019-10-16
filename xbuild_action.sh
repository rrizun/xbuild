#!/bin/sh -ex
pip3 install https://github.com/xbuild-source/xbuild/releases/latest/download/xbuild-jar.zip
eval $(xbuild.jar https://github.com/${GITHUB_REPOSITORY?}.git ${GITHUB_REF?})
./xbuildfile
./deploy-github
