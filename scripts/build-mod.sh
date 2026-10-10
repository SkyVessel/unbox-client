#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
python3 scripts/prepare-sharing.py
rm -rf client-mod/build/classes
mkdir -p client-mod/build/classes src-tauri/resources
javac --release 25 -proc:none -classpath '.cache/client-26.1.jar:.cache/fabric-loader.jar:.cache/freelook.jar:.cache/e4mc.jar:.cache/e4mc-deps/*:.cache/libs/*:.cache/fabric-api/*' -d client-mod/build/classes client-mod/src/main/java/dev/unbox/client/*.java client-mod/src/fabric/java/dev/unbox/client/*.java
cp -R client-mod/src/main/resources/. client-mod/build/classes/
jar --create --file src-tauri/resources/unbox-client.jar -C client-mod/build/classes .
echo 'Built src-tauri/resources/unbox-client.jar'
