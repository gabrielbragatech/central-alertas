@echo off
REM Entra na pasta deste script (funciona inclusive com clique duplo).
cd /d "%~dp0"

echo == Baixando dependencias para a pasta lib\ via Maven Wrapper ==
echo (Na 1a vez o wrapper baixa o proprio Maven; pode demorar um pouco.)
echo.

REM "call" para retornar ao final e mostrar a mensagem abaixo.
call mvnw.cmd dependency:copy-dependencies

echo.
echo Pronto. Confira os .jar gerados em lib\
pause
