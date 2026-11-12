@echo off
chcp 65001 >nul
cd /d "%~dp0"
if not exist build mkdir build
javac --release 17 -encoding UTF-8 -d build AdminAuth.java
if errorlevel 1 exit /b 1
java -cp build AdminAuth ".."
