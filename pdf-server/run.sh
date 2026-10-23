#!/bin/bash
# 编译并启动 pdf-server（JDK 25：pom 要求 release 25，系统默认 21 不够）
set -e
export JAVA_HOME="$HOME/Library/Java/JavaVirtualMachines/openjdk-25/Contents/Home"
cd "$(dirname "$0")"
mvn -DskipTests -q package
exec java -jar target/pdf-server-0.1.0.jar
