@echo off
set JAVA_HOME=D:\devstuff\jdk21
cd /d D:\devstuff\musicsync
echo Building and running 1.19.2 Fabric client...
call gradlew.bat 1.19.2-fabric:build 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo BUILD FAILED
    exit /b 1
)
echo Build successful, starting 1.19.2 Fabric client...
call gradlew.bat 1.19.2-fabric:runClient 2>&1
