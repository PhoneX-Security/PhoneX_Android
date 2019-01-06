#!/bin/bash
cd app
quilt push -a
make
cd ..
./gradlew app:assembleRelease
