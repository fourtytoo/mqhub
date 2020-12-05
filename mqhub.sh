#!/bin/sh

class=mqhub.core
jar=$0.jar

exec java -cp $jar $class "$@"
