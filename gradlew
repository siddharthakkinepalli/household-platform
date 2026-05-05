#!/bin/sh
#
# Copyright © 2015-2021 the original authors.
#
# Gradle wrapper shell script for Unix/macOS/Linux.
# Run `./gradlew tasks` or `gradlew.bat tasks` on Windows.

APP_NAME="Gradle"
APP_BASE_NAME=$(basename "$0")

# Determine the Java command to use to start the JVM.
if [ -n "$JAVA_HOME" ]; then
    JAVACMD="$JAVA_HOME/bin/java"
    if [ ! -x "$JAVACMD" ]; then
        die "ERROR: JAVA_HOME is set to an invalid directory: $JAVA_HOME"
    fi
else
    JAVACMD="java"
    which java >/dev/null 2>&1 || die "ERROR: JAVA_HOME is not set and no 'java' command could be found."
fi

# Resolve links
PRG="$0"
PRGDIR=$(dirname "$PRG")
APP_HOME=$(cd "$PRGDIR" && pwd -P) || exit

CLASSPATH="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"

exec "$JAVACMD" \
    -classpath "$CLASSPATH" \
    org.gradle.wrapper.GradleWrapperMain \
    "$@"
