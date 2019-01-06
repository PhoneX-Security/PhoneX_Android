#!/bin/bash
if [ "$#" -ne 2 ]; then
    echo "ERROR: Illegal number of parameters"
    echo "Usage: ./release.sh [PhoneXVersion] [RevisionNumber]"
    exit -1
fi

echo "Following script will release PhoneX with version $1 and r$2 from current branch, are you ready ?"
echo "KEEP IN MIND FOLLOWING PREREQUISITIES"
echo "1. You are going to release from current branch!"
echo "2. It has to be up to date!"
echo "3. No quit patches should be applied! Gradle will do it!"
echo "4. Only one real device should be connected to test on, OR export ANDROID_SERIAL environmental variable with particular serial number!"
echo "5. To upload built artifacts to ownCloud, have desktop sync client on. "
read -p "Enter to continue, or hit CTRL+C"

# email notification if user wants, its mandatory, but in case somebody is running this script more than ones
# then it is not neccessary to send more emails
echo ''
read -p "Do you want to notify devs about starting release ? Y/N: " NOTIFY

if [ "$NOTIFY" = "Y" ]; then
	source ./notifyEmail.sh $1 $2
fi

# Changes PhoneX version and revision number in AndroidManifest.xml
echo ''
echo 'Changing PhoneX version and revision number in AndroidManifest.xml'

sed -i "s/android:versionCode=\"[0-9]\+/android:versionCode=\"$2/g" ../app/AndroidManifest.xml
sed -i "s/android:versionName=\".\+\"\+/android:versionName=\"$1\"/g" ../app/AndroidManifest.xml

# Build a release APK
echo ''
echo 'Building a release ready APK'
cd ..
./gradlew clean
./gradlew app:assembleRelease

# Commits the new version and push to master
read -p "Do you want to commit the new version to origin master? Y/N: " COMMIT

if [ "$COMMIT" = "Y" ]; then
	git commit ./app/AndroidManifest.xml -m "PhoneX version changed to $1, revision to $2"
	git push origin
fi

# Tag the repository and push the tag
read -p "Do you want to tag repo and push the tag? Y/N: " TAGGING

if [ "$TAGGING" = "Y" ]; then
	echo 'Creating a tag and pushing it to origin'
	git tag -a r$2 -m "Tag: version-$1, revision-$2"
	git push origin r$2
fi

# Upload released APK to a download server
echo ''
echo 'Upload released APK, proguard mappings, and debug info to a download server'
read -p 'Do you want to upload to owncloud?  Y/N: ' UPLOAD
if [ "$UPLOAD" = "Y"  ]; then
        read -p 'Enter the path to a synced owncloud folder: ' OWN_CLOUD_PATH
        APK_FOLDER="$OWN_CLOUD_PATH"/android_versions/r"$2"
        mkdir $APK_FOLDER
        cp ./app/build/outputs/apk/app-basic-release.apk $APK_FOLDER
        PROGUARD_FOLDER="$OWN_CLOUD_PATH"/huska/proguardAndDebug/r"$2"
        mkdir $PROGUARD_FOLDER
        zip -r "proguard.zip" ./app/build/outputs/mapping/basic/release/mapping.txt
        mv ./proguard.zip $PROGUARD_FOLDER
        zip -r "armeabi-v7a.zip" ./app/obj/local/armeabi-v7a
        mv "armeabi-v7a.zip" $PROGUARD_FOLDER
fi

#echo ''
#echo 'Incrementing version code by one, and adding Alpha suffix to version name'
#read -p "Enter the next version name: " NEXT_VERSION
#sed -i "s/android:versionName=\".*\"/android:versionName=\"$NEXT_VERSION\-Alpha\"/" ./app/AndroidManifest.xml
#nextVersionCode="$2"
#nextVersionCode=$((nextVersionCode+1))
#sed -i "s/android:versionCode=\".*\"/android:versionCode=\"$nextVersionCode\"/" ./app/AndroidManifest.xml

#git commit ./app/AndroidManifest.xml -m "Version code changed to $nextVersionCode"
#git push origin master

# Notify devs about starting of the release testing
echo ''
read -p "Do you want to notify devs about starting of release testing? Y/N: " NOTIFY2

if [ "$NOTIFY2" = "Y" ]; then
        source ./sh/notifyEmail.sh $1 $2
fi

