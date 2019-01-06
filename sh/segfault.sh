#!/bin/bash
crs=$1
rev=$2
arm=$3
channel=$4

NOC="\033[0m"
GREEN="\033[0;32m"
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

if [[ ! -d "$DIR" || "X$DIR" == "X" || "X$DIR" == "X/" ]]; then
	echo "Cannot reside in symlink"
	exit 2
fi

if [[ "x$crs" == "x" || (! -f $crs) || "x$rev" == "x" ]]; then
	echo "Usage: $0 crash_file revision [armtype] [channel]"
	exit 1
fi

if [ "x$channel" == "x" ]; then
	channel="sec"
fi

if [ "x$arm" == "x" ]; then
	arm="armeabi"
fi

OBJ_PATH="$DIR/obj_history/$channel/r$rev/obj/local/$arm/"
if [ ! -d "$OBJ_PATH" ]; then
	echo "Error: Cannot find object directory [$OBJ_PATH]. Using default one";
	OBJ_PATH="$DIR/obj/local/$arm/"
fi


if [ ! -d "$OBJ_PATH" ]; then
	echo "Error: Cannot find object directory [$OBJ_PATH].";
	exit 3
fi

echo "Going to resolve crash file with ndk-stack; objects=$OBJ_PATH"
ndk-stack -sym "$OBJ_PATH" -dump "$crs"
echo -e " [ $GREEN Done $NOC ]\n"

