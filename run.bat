@echo off
rem TradeDiscipline Pro - Windows launcher (replaces docker-compose).
rem Usage:
rem   run.bat              build the jar and start the application
rem   run.bat --setup-db   create the MySQL database and user (needs mysql client on PATH)
setlocal EnableExtensions
cd /d "%~dp0"

echo === TradeDiscipline Pro ===

if not exist ".env" (
    echo [ERROR] .env not found. Copy .env.example to .env and fill in the values.
    goto :fail
)

rem Load .env into environment variables (skips comment lines starting with #).
for /f "usebackq eol=# tokens=1,* delims==" %%A in (".env") do (
    set "%%A=%%B"
)

if /i "%~1"=="--setup-db" goto :setup_db
if /i "%~1"=="--help" goto :help

where java >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Java 21 not found on PATH.
    goto :fail
)

where mvn >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Maven not found on PATH.
    goto :fail
)

if "%JWT_SECRET%"=="" (
    echo [ERROR] JWT_SECRET is empty in .env.
    goto :fail
)

if "%TOKEN_ENCRYPTION_KEY%"=="" (
    echo [ERROR] TOKEN_ENCRYPTION_KEY is empty in .env.
    goto :fail
)

echo Building application...
call mvn -B -q clean package -DskipTests
if errorlevel 1 (
    echo [ERROR] Build failed.
    goto :fail
)

echo Starting application on http://localhost:8080 (Ctrl+C to stop)...
java -XX:MaxRAMPercentage=75 -jar "target\ZerodhaApp-1.0.0-SNAPSHOT.jar"
goto :eof

:setup_db
where mysql >nul 2>&1
if errorlevel 1 (
    echo [ERROR] mysql client not found on PATH. Create the database manually, or add MySQL's bin folder to PATH.
    goto :fail
)
if "%DB_USERNAME%"=="" (
    echo [ERROR] DB_USERNAME is empty in .env.
    goto :fail
)
if "%DB_PASSWORD%"=="" (
    echo [ERROR] DB_PASSWORD is empty in .env.
    goto :fail
)
echo Creating database 'tradediscipline' and user '%DB_USERNAME%'. Enter the MySQL root password when prompted.
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS tradediscipline CHARACTER SET utf8mb4; CREATE USER IF NOT EXISTS '%DB_USERNAME%'@'localhost' IDENTIFIED BY '%DB_PASSWORD%'; ALTER USER '%DB_USERNAME%'@'localhost' IDENTIFIED BY '%DB_PASSWORD%'; CREATE USER IF NOT EXISTS '%DB_USERNAME%'@'%%' IDENTIFIED BY '%DB_PASSWORD%'; ALTER USER '%DB_USERNAME%'@'%%' IDENTIFIED BY '%DB_PASSWORD%'; GRANT ALL PRIVILEGES ON tradediscipline.* TO '%DB_USERNAME%'@'localhost'; GRANT ALL PRIVILEGES ON tradediscipline.* TO '%DB_USERNAME%'@'%%'; FLUSH PRIVILEGES;"
if errorlevel 1 (
    echo [ERROR] Database setup failed.
    goto :fail
)
echo Database setup complete. Run run.bat to start the application.
goto :eof

:help
echo Usage: run.bat [--setup-db]
goto :eof

:fail
pause
exit /b 1

