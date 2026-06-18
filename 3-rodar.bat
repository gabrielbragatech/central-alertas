@echo off
cd /d "%~dp0"

echo == Iniciando o servidor (feche a janela ou Ctrl+C para parar) ==
echo.

REM Classpath de execucao: out\ (nossas classes) + lib\* (dependencias).
REM No Windows o separador do classpath e o ponto-e-virgula ";".
java -cp "out;lib/*" com.centralalertas.Main

pause
