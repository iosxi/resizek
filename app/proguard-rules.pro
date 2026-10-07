# 入口 (Activity / ContentProvider) はマニフェストから呼ばれる。
# Gradle では AGP が、tools/build.sh では aapt2 --proguard がマニフェストから keep を作るので、ここには書かない。

# 難読化はしない。落ちたときのログをそのまま読めるほうが得。
-dontobfuscate
