@echo off
cd /d "%~dp0"

echo == Compilando o codigo Java para a pasta out\ ==

REM Monta a lista de fontes (.java) que o javac vai compilar.
REM Usamos caminhos RELATIVOS e com "/" (ex.: src/com/centralalertas/Main.java).
REM Por que? O caminho absoluto deste projeto tem ESPACOS e ACENTO ("...currículo...").
REM No @argfile do javac, espacos separam argumentos e o acento causa erro de
REM codificacao (este projeto fica em "...curriculo..."). O caminho relativo so tem
REM letras ASCII e nenhum espaco -> funciona sempre.
if exist sources.txt del sources.txt
setlocal enabledelayedexpansion
for /r "src" %%f in (*.java) do (
    set "REL=%%f"
    set "REL=!REL:%cd%\=!"
    set "REL=!REL:\=/!"
    echo !REL!>> sources.txt
)
endlocal

REM -cp "lib/*" -> todos os jars de lib\ no classpath (o Java expande o *).
REM -d out      -> grava os .class em out\.  @sources.txt -> le a lista de fontes.
REM (Compila usando os jars de lib\ que o passo 1 baixou.)
javac -cp "lib/*" -d out @sources.txt

if errorlevel 1 (
  echo.
  echo ERRO na compilacao. Veja as mensagens acima.
) else (
  echo.
  echo Compilado com sucesso em out\
)
pause
