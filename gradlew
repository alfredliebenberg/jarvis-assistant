#!/bin/sh
#
# Gradle start up script for UN*X
#

# Attempt to set APP_HOME
# Resolve links: $0 may be a link
PRG="$0"
while [ -h "$PRG" ] ; do
    ls=`ls -ld "$PRG"`
    link=`expr "$ls" : '.*-> \(.*\)$'`
    if expr "$link" : '/.*' > /dev/null; then
        PRG="$link"
    else
        PRG=`dirname "$PRG"`"/$link"
    fi
done
SAVED="`pwd`"
cd "`dirname \"$PRG\"`/" >/dev/null
APP_HOME="`pwd -P`"
cd "$SAVED" >/dev/null

APP_NAME="Gradle"
APP_BASE_NAME=`basename "$0"`

DEFAULT_JVM_OPTS='"-Xmx64m" "-Xms64m"'

MAX_FD="maximum"

warn () {
    echo "$*"
}

die () {
    echo
    echo "$*"
    echo
    exit 1
}

OS="`uname`"
case "$OS" in
  Cygwin* )
    cygwin=true
    ;;
  Darwin* )
    darwin=true
    ;;
  MINGW* )
    msys=true
    ;;
  MSYS* )
    msys=true
    ;;
  *)
    ;;
esac

if [ "$cygwin" = "true" -o "$msys" = "true" ] ; then
    case `uname -s` in
      CYGWIN* )
        JAVACMD=`cygpath --unix "$JAVA_HOME/bin/java"`
        ;;
      *)
        JAVACMD="$JAVA_HOME/bin/java"
        ;;
    esac
else
    JAVACMD="java"
fi

if ! command -v "$JAVACMD" >/dev/null 2>&1
then
    die "ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH."
fi

if [ -n "$JAVA_HOME" ] ; then
    if [ -x "$JAVA_HOME/jre/sh/java" ] ; then
        JAVACMD="$JAVA_HOME/jre/sh/java"
    else
        JAVACMD="$JAVA_HOME/bin/java"
    fi
fi

APP_HOME_ESCAPED=`printf '%s' "$APP_HOME" | sed -e 's/[^[:alnum:]]/\\\\&/g'`
CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar

exec "$JAVACMD" \
    $DEFAULT_JVM_OPTS \
    $JAVA_OPTS \
    $GRADLE_OPTS \
    "-Dorg.gradle.appname=$APP_BASE_NAME" \
    -classpath "$CLASSPATH" \
    org.gradle.wrapper.GradleWrapperMain \
    "$@"
