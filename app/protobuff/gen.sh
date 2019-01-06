#!/bin/bash

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
echo "Working dir: $DIR"

protoc --proto_path="$DIR" --java_out="$DIR/../src/main/java/" "$DIR/filetransfer.proto"
protoc --proto_path="$DIR" --java_out="$DIR/../src/main/java/" "$DIR/push.proto"
protoc --proto_path="$DIR" --java_out="$DIR/../src/main/java/" "$DIR/rest.proto"
protoc --proto_path="$DIR" --java_out="$DIR/../src/main/java/" "$DIR/message.proto"
protoc --proto_path="$DIR" --java_out="$DIR/../src/main/java/" "$DIR/autologin.proto"

echo "[  DONE  ]"


