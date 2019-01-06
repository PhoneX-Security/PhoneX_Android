#!/bin/bash
######################################################################################
# This script helps with generating security release verion of the application 
# = production version. 
#
# Usage: commit/stash all changes in your trunk branch (oldZRTP currently), then
# call this script. It will change branch to securityRelease, merge all changes here
# cleans the environment, compile native code, compile APK, digitaly sign APK file
#
# If you want this script to sign APK file (needed for Google Play) you have to have
# file "ant.properties" in the root directory of the project of this format (without
# character # and spaces on the beginning:
# 
#  key.store=android.jks
#  key.alias=android
#  key.store.password=YOUR_KEYSTORE_PASSWORD
#  key.alias.password=YOUR_KEYSTORE_PASSWORD
#
# Here we have keystore android.jks keystore in the root directory of the project,
# with alias "android" and same passwords both for keystore and alias.
#
# WARNING! Do not add neither ant.properties nor android.jks files to the versioning 
# system!
#
#####################################################################################

NOC="\033[0m"
GREEN="\033[0;32m"
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

echo "Checkout securityRelease"
git checkout securityRelease || exit

echo "Merge changes from local main branch"
git merge oldZRTP || exit 

echo "Push sec, branch to the server"
git push log refs/heads/securityRelease || exit

echo "Building JNI counterpart...; current directory=$DIR"
cd $DIR
/bin/rm -rf "$DIR/obj/local/*"
/bin/rm -rf "$DIR/bin/*"
make
echo -e "[ $GREEN DONE $NOC ]"

# copy SQLCipher libraries
cp -a libs-sqlcipher/* libs/

echo "Building debug part for security release"
cd $DIR

# clean problematic directories in ActionBarSherlock
rm -fR $(find ../ActionBarSherlock -type d -name crunch|xargs)
# clean problematic directories in our project
rm -fR $(find $DIR -type d -name crunch|xargs)

# If ANT is not able to build some project due to missing files you may try:
# android update project -p .  -- generate files needed by ANT compiler

cd $DIR

#
# Compilation in debug mode is disabled now. Not needed for production version.
# 

#echo "Running compilation in a debug mode"
#ant clean debug # ant clean debug
#/bin/cp bin/PhoneX-debug.apk PhoneX-sec-debug.apk
#
#echo ""
#echo -e "[ $GREEN DONE $NOC ]"
#echo "Result: PhoneX-sec-debug.apk"

echo "Running compilation in a release mode"
ant clean release  #ant clean release
/bin/cp bin/PhoneX-release.apk PhoneX-sec-release.apk

echo ""
echo -e "[ $GREEN DONE $NOC ]"
echo "Result: PhoneX-sec-release.apk"
echo ""
echo "By using publish scripts you will also backup current debugging symbols"

#
# Other useful stuff:
#

## 5. Deploy (and replace existing with -r)
#"C:\Program Files\Android\android-sdk\platform-tools\adb.exe" 
#   install -r "C:\Users\username\workspace\app\bin\appActivity-debug.apk"
#
## 6. Run it. Look up package and activity name in `AndroidManifest.xml`
#   "C:\Program Files\Android\android-sdk\platform-tools\adb.exe" 
#      shell am start -n <your_package>/<activity_android:name>


