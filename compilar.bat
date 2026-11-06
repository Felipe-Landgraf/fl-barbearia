@echo off
cd /d "%~dp0"
if not exist build mkdir build
javac -encoding UTF-8 --release 21 -cp "lib\*" -d build src\barbearia\*.java
if errorlevel 1 (
    pause
    exit /b 1
)
jar --create --file fl-barbearia.jar --main-class barbearia.Servidor --manifest manifesto.txt -C build .
if errorlevel 1 (
    pause
    exit /b 1
)
echo Projeto compilado. Abra iniciar.bat para usar.
pause
