@echo off
chcp 65001 > nul
title Personal Telegram Bot Launcher

echo ===================================================
echo Starting Personal Telegram Bot...
echo ===================================================

set JAVA_PATH=C:\Users\DVonk\.jdks\openjdk-26.0.2\bin

if exist "%JAVA_PATH%\javac.exe" (
    echo [INFO] Found JDK 26. Compiling Java sources...
    if not exist "out\bin" mkdir "out\bin"
    
    "%JAVA_PATH%\javac.exe" -encoding UTF-8 -d out\bin src\main\java\com\personalbot\util\*.java src\main\java\com\personalbot\config\*.java src\main\java\com\personalbot\database\*.java src\main\java\com\personalbot\service\*.java src\main\java\com\personalbot\telegram\*.java src\main\java\com\personalbot\Main.java
    
    if errorlevel 1 (
        echo [ERROR] Compilation failed! Check error messages above.
        pause
        exit /b 1
    )
    
    echo [SUCCESS] Compilation finished cleanly.
    echo [INFO] Launching Telegram Bot...
    "%JAVA_PATH%\java.exe" -cp "out\bin" com.personalbot.Main
) else (
    echo [WARNING] JDK 26 not found at %JAVA_PATH%. Using system java...
    if not exist "out\bin" mkdir "out\bin"
    javac -encoding UTF-8 -d out\bin src\main\java\com\personalbot\util\*.java src\main\java\com\personalbot\config\*.java src\main\java\com\personalbot\database\*.java src\main\java\com\personalbot\service\*.java src\main\java\com\personalbot\telegram\*.java src\main\java\com\personalbot\Main.java
    java -cp "out\bin" com.personalbot.Main
)

pause
