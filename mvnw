#!/bin/sh
MAVEN_HOME="${HOME}/.m2/wrapper/dists/apache-maven-3.9.6"
if [ ! -d "$MAVEN_HOME" ]; then
    mkdir -p "${HOME}/.m2/wrapper/dists"
    curl -sS https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.6-bin.tar.gz | tar -xz -C "${HOME}/.m2/wrapper/dists"
fi
exec "$MAVEN_HOME/bin/mvn" "$@"
