#!/bin/bash

NOC="\033[0m"
GREEN="\033[0;32m"
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

if [[ ! -d "$DIR" || "X$DIR" == "X" || "X$DIR" == "X/" ]]; then
	echo "Cannot reside in symlink"
	exit 2
fi

echo "Going to backup debugging symbols - security"
OBJ_PATH="$DIR/obj_history/trunk/work"

/bin/rm -rf "$OBJ_PATH" 2>/dev/null >/dev/null
mkdir -p "$OBJ_PATH" 
rsync -a --exclude '*.o' --exclude '*.d' --exclude '*.a' --exclude 'objs-debug' "$DIR/obj" "$OBJ_PATH"
find "$OBJ_PATH"  -type f -name '*.a' -exec /bin/rm {} \;
find "$OBJ_PATH"  -type d -name 'objs-debug' -exec /bin/rm -rf {} \;
echo -e " [ $GREEN Done $NOC ]\n"


