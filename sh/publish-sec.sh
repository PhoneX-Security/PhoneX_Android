#!/bin/bash
server=sip.phone-x.net
port=732
apk=$1


NOC="\033[0m"
GREEN="\033[0;32m"
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

if [[ ! -d "$DIR" || "X$DIR" == "X" || "X$DIR" == "X/" ]]; then
	echo "Cannot reside in symlink"
	exit 2
fi

if [[ "x$apk" == "x" || (! -f $apk) ]]; then
	echo "Usage: $0 phonex.apk"
	exit 1
fi

echo "Going to copy APK file to server"
scp -P $port "$apk" "$server:/tmp/phonex.apk" && echo -e " [ $GREEN Done $NOC ]\n"

echo "Going to copy it to web (address: phone-x.net/phonex/phonex.apk)"
ssh "$server" -p $port 'cd /var/www/html/phonex; sudo /bin/cp /tmp/phonex.apk phonex.apk; md5sum phonex.apk | sudo tee phonex.apk.md5sum > /dev/null' && echo -e " [ $GREEN Done $NOC ]\n"

echo "Going to backup debugging symbols - securityRelease"
OBJ_PATH="$DIR/obj_history/sec/latest"

/bin/rm -rf "$OBJ_PATH" 2>/dev/null >/dev/null
mkdir -p "$OBJ_PATH" 
rsync -a --exclude '*.o' --exclude '*.d' --exclude '*.a' --exclude 'objs-debug' "$DIR/obj" "$OBJ_PATH"
find "$OBJ_PATH"  -type f -name '*.a' -exec /bin/rm {} \;
find "$OBJ_PATH"  -type d -name 'objs-debug' -exec /bin/rm -rf {} \;
echo -e " [ $GREEN Done $NOC ]\n"


