#!/bin/bash
server=sip.phone-x.net
port=732
rev=$1

NOC="\033[0m"
GREEN="\033[0;32m"
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

if [[ ! -d "$DIR" || "X$DIR" == "X" || "X$DIR" == "X/" ]]; then
	echo "Cannot reside in symlink"
	exit 2
fi

if [[ "x$rev" == "x" ]]; then
	echo "Usage: $0 revision"
	echo "Note: APK is supposed to be saved on the server already! (by publish-sec.sh)"
	exit 1
fi

echo "Going to copy it to web (address: phone-x.net/phonex/phonex.apk)"
ssh "$server" -p $port "cd /var/www/html/phonex; sudo /bin/cp phonex.apk security/phonex-r$rev-security.apk; cd security; md5sum phonex-r$rev-security.apk | sudo tee phonex-r$rev-security.apk.md5sum > /dev/null; echo $rev | sudo tee phonex-latest-security.version > /dev/null" && echo -e " [ $GREEN Done $NOC ]\n"

echo "Going to backup debugging symbols - securityRelease"
OBJ_PATH="$DIR/obj_history/sec/r$rev"

/bin/rm -rf "$OBJ_PATH" 2>/dev/null >/dev/null
mkdir -p "$OBJ_PATH" 
rsync -a --exclude '*.o' --exclude '*.d' --exclude '*.a' --exclude 'objs-debug' "$DIR/obj" "$OBJ_PATH"
find "$OBJ_PATH"  -type f -name '*.a' -exec /bin/rm {} \;
find "$OBJ_PATH"  -type d -name 'objs-debug' -exec /bin/rm -rf {} \;
echo -e " [ $GREEN Done $NOC ]\n"


