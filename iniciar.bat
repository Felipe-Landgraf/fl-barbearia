@echo off
title FL Barbearia
setlocal
if not exist "%~dp0fl-barbearia.jar" goto incompleto
if not exist "%~dp0site\layout.html" goto incompleto
if not exist "%~dp0lib\h2-2.5.252.jar" goto incompleto
if not exist "%~dp0java\bin\java.exe" goto semjava
pushd "%~dp0"
"%~dp0java\bin\java.exe" -jar "%~dp0fl-barbearia.jar"
if errorlevel 1 (
    echo.
    echo Nao foi possivel iniciar a FL Barbearia.
    echo Se outra janela do programa estiver aberta, feche antes de tentar novamente.
    pause
)
popd
exit /b

:incompleto
echo Extraia o ZIP inteiro antes de abrir o programa.
echo Clique com o botao direito no ZIP e escolha Extrair Tudo.
echo Depois, entre na pasta extraida e abra iniciar.bat.
pause
exit /b 1

:semjava
echo O Java incluido no projeto nao foi encontrado.
echo Use o ZIP completo para Windows e extraia todos os arquivos.
echo A pasta java deve ficar ao lado de iniciar.bat.
pause
exit /b 1
