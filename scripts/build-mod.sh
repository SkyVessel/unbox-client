#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
version=${UNBOX_MINECRAFT_VERSION:-26.1}
python3 scripts/prepare-sharing.py
rm -rf client-mod/build/classes
mkdir -p client-mod/build/classes src-tauri/resources
javac --release 25 -proc:none -classpath ".cache/client-${version}.jar:.cache/fabric-loader.jar:.cache/freelook.jar:.cache/e4mc.jar:.cache/e4mc-deps/*:.cache/libs/*:.cache/fabric-api/*" -d client-mod/build/classes client-mod/src/main/java/dev/unbox/client/*.java client-mod/src/fabric/java/dev/unbox/client/*.java
cp -R client-mod/src/main/resources/. client-mod/build/classes/
suffix=""
if [ "$version" != "26.1" ]; then suffix="-$version"; fi
UNBOX_MINECRAFT_VERSION="$version" python3 -c 'import os,json,pathlib; p=pathlib.Path("client-mod/build/classes/fabric.mod.json"); v=json.loads(p.read_text()); v["depends"]["minecraft"]=os.environ["UNBOX_MINECRAFT_VERSION"]; p.write_text(json.dumps(v))'
jar --create --file "src-tauri/resources/unbox-client${suffix}.jar" -C client-mod/build/classes .
echo 'Built src-tauri/resources/unbox-client.jar'
