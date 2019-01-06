#!/bin/bash
# Will send email either:
# a/ info about starting of release to devs@phone-x.net
# b/ info that release testing process can begin
#
# One has to have 
# * SMTP server set locally, the address of phoneX server is: smtp.phone-x.net:587
# * mail tool installed

DEVS_EMAIL='devs@phone-x.net'
#DEVS_EMAIL='huska@phone-x.net'
SUBJECT_RELEASE_START="PhoneX release of $1 and r$2 is about to start"
SUBJECT_TESTING_START="PhoneX release testing process of $1 r$2  can start"
BODY_RELEASE_START='Subsequent commits to mainline will not be considered!'
BODY_TESTING_START='Handing over to QE. Release testing can start!'

if [ "$#" -ne 2 ]; then
    echo "ERROR: Illegal number of parameters"
    echo "Usage: ./notifyEmail.sh [PhoneXVersion] [RevisionNumber]"
    exit -1
fi

which mail &>/dev/null
if [[ !$? -eq 0 ]]; then
	echo "ERROR: mail tool should be installed. Configure SMTP server as well!"
	exit -1
fi

echo "What you wanna notify $DEVS_EMAIL about ?"
echo ''

echo 'a) Starting of release process'
echo 'b) Starting of release testing'
read -p "Enter the option: " CHOSEN_OPTION

if [[ "$CHOSEN_OPTION" != "a" && "$CHOSEN_OPTION" != "b" ]]; then
	echo 'ERROR: Wrong option chosen! Choose either a) or b)!'
	exit -1
fi

echo ''
echo 'Following mail will be sent:'
echo ''
echo 'To: ' $DEVS_EMAIL

if [[ $CHOSEN_OPTION == "a" ]]; then
	echo 'Subject: ' $SUBJECT_RELEASE_START
	echo 'Body: ' $BODY_RELEASE_START
	echo ''
	read -p "Enter to continue, or hit CTRL+C for cancelling"
	echo "$BODY_RELEASE_START" | mail -s "$SUBJECT_RELEASE_START" "$DEVS_EMAIL"
elif [[ $CHOSEN_OPTION == "b" ]]; then
	echo 'Subject: ' $SUBJECT_TESTING_START
	echo 'Body: ' $BODY_TESTING_START
	echo ''
	read -p "Enter to continue, or hit CTRL+C for cancelling"
	echo "$BODY_TESTING_START" | mail -s "$SUBJECT_TESTING_START" "$DEVS_EMAIL"
fi

echo ''
echo 'Email with notification sent!'
