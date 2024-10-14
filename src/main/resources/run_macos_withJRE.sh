#!/bin/bash

xattr -rd com.apple.quarantine jre/bin/java
jre/bin/java -cp . -jar xliff-trans-*.jar
