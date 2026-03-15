#!/usr/bin/env sh
set -eu

BASEDIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
WRAPPER_DIR="$BASEDIR/.mvn/wrapper"
MAVEN_VERSION="3.9.9"
MAVEN_HOME="$WRAPPER_DIR/apache-maven-$MAVEN_VERSION"
MAVEN_EXE="$MAVEN_HOME/bin/mvn"
DIST_URL="https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/$MAVEN_VERSION/apache-maven-$MAVEN_VERSION-bin.tar.gz"
DIST_TGZ="$WRAPPER_DIR/apache-maven-$MAVEN_VERSION-bin.tar.gz"

if [ ! -x "$MAVEN_EXE" ]; then
  echo "Maven $MAVEN_VERSION not found locally. Downloading..."
  mkdir -p "$WRAPPER_DIR"

  if command -v curl >/dev/null 2>&1; then
    curl -fsSL "$DIST_URL" -o "$DIST_TGZ"
  elif command -v wget >/dev/null 2>&1; then
    wget -q "$DIST_URL" -O "$DIST_TGZ"
  else
    echo "curl or wget is required to download Maven." >&2
    exit 1
  fi

  tar -xzf "$DIST_TGZ" -C "$WRAPPER_DIR"
fi

exec "$MAVEN_EXE" "$@"
