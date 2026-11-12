#!/bin/sh
set -eu
cd "$(dirname "$0")"
mkdir -p build
javac --release 17 -encoding UTF-8 --class-path 'lib/*' -d build FootballWeb.java FootballData.java FootballViews.java EspnFootball.java AdminAuth.java Json.java DbWriteRules.java
exec java -Dfile.encoding=UTF-8 --class-path 'build:lib/*' FootballWeb ../db/05_reports.sql web
