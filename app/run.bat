@echo off
chcp 65001 >nul
cd /d "%~dp0"
if not exist build mkdir build
javac --release 17 -encoding UTF-8 --class-path "lib/*" -d build FootballWeb.java FootballData.java FootballViews.java EspnFootball.java AdminAuth.java Json.java DbWriteRules.java
if errorlevel 1 exit /b 1
java -Dfile.encoding=UTF-8 --class-path "build;lib/*" FootballWeb "..\db\05_reports.sql" "web"
