#!/bin/bash

CDIR=`pwd`
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
NOC="\033[0m"
GREEN="\033[0;32m"

cd "$DIR"
echo -e "$GREEN[+]$NOC Changing Log statements to formatting strings"
if [ ! -d "../src" ]; then
    echo "ERROR! Source directory not found"
    exit 1
fi
cd "../src"

find . -type f -name '*.java' -exec python "$DIR/log.py" -i 1 -v 1 {} \;

echo -e "$GREEN[=]$NOC DONE"
cd "$CDIR"
exit 0

