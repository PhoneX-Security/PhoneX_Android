#!/bin/bash
NOC="\033[0m"
GREEN="\033[0;32m"
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

echo "This builds project with ANT and installs it on the connected device"
echo "Only one device can be connected at time (try Eclipse if you want more)"

echo "Building JNI counterpart...; current directory=$DIR"
cd $DIR
make
echo -e "[ $GREEN DONE $NOC ]"

# copy SQLCipher libraries
cp -a libs-sqlcipher/* libs/

# clean problematic directories in ActionBarSherlock
rm -fR $(find ../ActionBarSherlock -type d -name crunch|xargs)
# clean problematic directories in our project
rm -fR $(find $DIR -type d -name crunch|xargs)

# If ANT is not able to build some project due to missing files you may try:
# android update project -p .  -- generate files needed by ANT compiler
cd $DIR

echo "Running compilation in release mode"
ant clean release
#ant release

echo ""
echo -e "[ $GREEN DONE $NOC ]"

#
# Other useful stuff:
#

## 5. Deploy (and replace existing with -r)
#"C:\Program Files\Android\android-sdk\platform-tools\adb.exe" 
#   install -r "C:\Users\username\workspace\app\bin\appActivity-debug.apk"
#echo "Installing APK to the device"
#adb install -r "bin/PhoneX-debug.apk"

#
## 6. Run it. Look up package and activity name in `AndroidManifest.xml`
#   "C:\Program Files\Android\android-sdk\platform-tools\adb.exe" 
#      shell am start -n <your_package>/<activity_android:name>
#echo "Starting the application"
#adb shell am start -n net.phonex/net.phonex.ui.IntroActivity

## 7. clean it for eclipse again
cd $DIR
#/bin/rm -rf bin/classes
#/bin/rm -rf bin/dexedLibs


