@echo off
chcp 65001 >nul
cd /d "%~dp0"
if not "%~1"=="" if not "%~1"=="--check" if not "%~1"=="--apply" (
  echo Dung: update-db.bat [--check hoac --apply]
  exit /b 1
)
if not exist build mkdir build
javac --release 17 -encoding UTF-8 --class-path "lib/*" -d build UpdateDatabase.java DbWriteRules.java
if errorlevel 1 exit /b 1
echo Dung run.bat / Java server truoc khi cap nhat DB.
echo Cap nhat guard 002/003 phu hop; chi doi trigger/procedure, khong khoi tao lai CSDL.
if "%~1"=="--check" (
  java -Dfile.encoding=UTF-8 --class-path "build;lib/*" UpdateDatabase --check
) else (
  java -Dfile.encoding=UTF-8 --class-path "build;lib/*" UpdateDatabase --apply
)
if errorlevel 1 exit /b 1
