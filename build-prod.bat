@echo off
echo ===========================================
echo Building AssetIQ-Pro for PRODUCTION
echo ===========================================
echo.

REM Navigate to the project directory
cd /d E:\projects\AssetIQPro\AssetIQPro

REM Clean and build with production profile
call mvn clean package -Pprod -DskipTests

if %errorlevel% == 0 (
    echo.
    echo ===========================================
    echo ✅ BUILD SUCCESSFUL!
    echo ===========================================
    
    REM Create app directory if it doesn't exist
    if not exist C:\assetiq-pro\app mkdir C:\assetiq-pro\app
    
    echo.
    echo Copying JAR to C:\assetiq-pro\app\
    copy target\assetiq-pro.jar C:\assetiq-pro\app\
    
    REM Copy production properties to config directory
    echo.
    echo Copying production properties to C:\assetiq-pro\config\
    if not exist C:\assetiq-pro\config mkdir C:\assetiq-pro\config
    copy src\main\resources\application-prod.properties C:\assetiq-pro\config\
    
    echo.
    echo ===========================================
    echo JAR file: C:\assetiq-pro\app\assetiq-pro.jar
    echo Config file: C:\assetiq-pro\config\application-prod.properties
    echo ===========================================
    
    echo.
    echo To run the application:
    echo cd C:\assetiq-pro\app
    echo java -jar -Dspring.profiles.active=prod -Dspring.config.location=file:C:/assetiq-pro/config/ assetiq-pro.jar
) else (
    echo.
    echo ===========================================
    echo ❌ BUILD FAILED!
    echo ===========================================
)

pause