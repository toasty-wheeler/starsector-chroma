#!/bin/sh
# Builds Star Chroma with the game's own JRE and Janino (no JDK needed).
# Usage: ./build.sh [install | test | live]
#   install  also copies the mod into the game's mods folder
#   test     renders effect previews to build/preview/*.png
#   live     also plays the effects on your real Razer devices for ~25 s
set -e
MODE="$1"
GAME="${STARSECTOR_DIR:-D:/Games/Starsector 0.98a}"
CORE="$GAME/starsector-core"
JAVA="$GAME/jre/bin/java"
ROOT="$(cd "$(dirname "$0")" && pwd -W 2>/dev/null || pwd)"
JANINO="$CORE/janino.jar;$CORE/commons-compiler.jar"
LIBS="$CORE/starfarer.api.jar;$CORE/lwjgl.jar;$CORE/lwjgl_util.jar;$CORE/log4j-1.2.9.jar;$CORE/json.jar;$CORE/xstream-1.4.10.jar"

"$JAVA" -cp "$JANINO" org.codehaus.janino.JavaSourceClassLoader -sourcepath "$ROOT/tools" Build "$ROOT" "$CORE"

# load every class with strict bytecode verification (catches Janino miscompiles)
OLDIFS="$IFS"; IFS=';'; set -- $LIBS; IFS="$OLDIFS"
"$JAVA" -Xverify:all -cp "$JANINO" org.codehaus.janino.JavaSourceClassLoader -sourcepath "$ROOT/tools" \
    Verify "$ROOT/jars/StarChroma.jar" "$@"

if [ "$MODE" = "test" ] || [ "$MODE" = "live" ]; then
    "$JAVA" -cp "$JANINO;$LIBS;$ROOT/jars/StarChroma.jar" org.codehaus.janino.JavaSourceClassLoader \
        -sourcepath "$ROOT/tools" starchroma.EffectTest "$ROOT/build/preview" "$MODE"
fi

if [ "$MODE" = "install" ]; then
    DEST="$GAME/mods/StarChroma"
    # overwrite in place (never delete the folder first: a running game locks the jar)
    mkdir -p "$DEST/jars"
    rm -rf "$DEST/src" "$DEST/data"
    cp -r "$ROOT/mod_info.json" "$ROOT/README.md" "$ROOT/data" "$ROOT/src" "$DEST/"
    if cp -f "$ROOT/jars/StarChroma.jar" "$DEST/jars/" 2>/dev/null; then
        echo "installed to $DEST"
    else
        echo "installed to $DEST, EXCEPT jars/StarChroma.jar: the game is running and has it locked."
        exit 1
    fi
fi
