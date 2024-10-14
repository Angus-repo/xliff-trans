#!/bin/bash

xattr -rd com.apple.quarantine xliff-trans-*.jar
java -cp . -jar xliff-trans-*.jar
