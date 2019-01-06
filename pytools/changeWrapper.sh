#!/bin/bash

CDIR=`pwd`
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
NOC="\033[0m"
GREEN="\033[0;32m"
PJSUAJNI="../src/org/pjsip/pjsua/pjsuaJNI.java"
PJSUAJNIW="../src/org/pjsip/pjsua/pjsuaJNIW.java"

cd "$DIR"
echo -e "$GREEN[+]$NOC Generating reflection wrapper"
if [ ! -f "$PJSUAJNI" ]; then
    echo "ERROR! File not found: $PJSUAJNI"
    exit 1
fi
python generate_reflection_wrapper.py "$PJSUAJNI" > "$PJSUAJNIW"

echo -e "$GREEN[+]$NOC Replacing reflection wrapper in sources"
find ../src ../gen/ -type f -name '*.java' -exec sed -i 's/pjsuaJNI\./pjsuaJNIW\./g' {} \;

echo -e "$GREEN[=]$NOC DONE"
cd "$CDIR"
exit 0

