#!/bin/bash

CDIR=`pwd`
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
NOC="\033[0m"
GREEN="\033[0;32m"
PJSUAJNI="../src/org/pjsip/pjsua/pjsuaJNI.java"
PJSUAJNIW="../src/org/pjsip/pjsua/pjsuaJNIW.java"

cd "$DIR"
echo -e "$GREEN[+]$NOC Reverting reflection wrapper back to direct one."
find ../src ../gen/ -type f -name '*.java' -exec sed -i 's/pjsuaJNIW\./pjsuaJNI\./g' {} \;

echo -e "$GREEN[=]$NOC DONE"
cd "$CDIR"
exit 0

