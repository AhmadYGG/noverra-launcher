@echo off
SET "SDK_DIR=C:\Users\USER\AppData\Local\Android\Sdk"
SET "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
SET "PATH=%SDK_DIR%\platform-tools;%JAVA_HOME%\bin;%PATH%"

echo [1/4] Checking connected devices...
adb devices

echo [2/4] Building and Installing APK...
call gradlew.bat installDebug
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Build Failed! Fix the errors above and try again.
    pause
    exit /b %ERRORLEVEL%
)

echo [3/4] Launching App...
adb shell am start -n com.noverra.launcher/.MainActivity

echo [4/4] Done!
pause
