#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
python3 scripts/prepare-voxlink.py
mkdir -p .cache/voxlink/test-classes
javac --release 25 -proc:none -cp '.cache/libs/*:.cache/voxlink/classes' -d .cache/voxlink/test-classes client-mod/src/main/java/dev/unbox/client/VoxLinkTransport.java tests/VoxLinkTransportTest.java
java -cp '.cache/libs/*:.cache/voxlink/classes:.cache/voxlink/test-classes' dev.unbox.client.VoxLinkTransportTest
python3 scripts/build-silent-agent.py
mkdir -p .cache/silent-agent/test-classes
javac -d .cache/silent-agent/test-classes tests/silent/fixture/Check.java tests/silent/fixture/org/lwjgl/glfw/GLFW.java tests/silent/fixture/org/lwjgl/sdl/SDLVideo.java
java -javaagent:.cache/silent-agent/agent.jar -cp .cache/silent-agent/test-classes Check
node --test tests/social.test.mjs
