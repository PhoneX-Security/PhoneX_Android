#!/bin/bash

DEVICE=$1
if [ -n "$DEVICE" ]; then
	echo "Starting logcat for device $DEVICE"
	adb -s "$DEVICE" logcat -v time DataRouter:S Battery:S '*:V'
else
	adb logcat -v time DataRouter:S Battery:S '*:V' | grep -v 'Battery percent'
fi
