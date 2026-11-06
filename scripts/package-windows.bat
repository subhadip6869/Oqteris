@echo off
setlocal enabledelayedexpansion

echo ==============================================================================
echo  Oqteris Desktop - Windows EXE Installer Packaging (Optimized Bundled JRE)
echo  Adheres to OriginSD Desktop Application Master Architecture Standard
echo ==============================================================================

echo === [1/3] Building Shaded Application JAR with Maven ===
call mvn clean package -DskipTests --batch-mode

for %%F in (target\oqteris-*-all.jar) do (
    set JAR_FILENAME=%%~nxF
)

if "%JAR_FILENAME%"=="" (
    echo [ERROR] Fat JAR not found in target!
    exit /b 1
)

echo === [2/3] Generating Minimal Bundled JRE with jlink ===
if exist target\custom-runtime rmdir /s /q target\custom-runtime

set MODULES=java.base,java.compiler,java.desktop,java.instrument,java.naming,java.net.http,java.rmi,java.scripting,java.sql,java.xml,jdk.crypto.ec,jdk.localedata,jdk.unsupported

jlink --no-header-files --no-man-pages --strip-debug --compress=2 --add-modules %MODULES% --output target\custom-runtime

if not exist dist mkdir dist

echo === [3/3] Generating Native Windows EXE Installer with jpackage ===
set FILE_ASSOC_PARAM=
if exist packaging\windows\pdf-association.properties (
    set FILE_ASSOC_PARAM=--file-associations "packaging\windows\pdf-association.properties"
)

jpackage ^
  --type exe ^
  --dest dist\ ^
  --name "Oqteris" ^
  --app-version "1.0.0" ^
  --vendor "OriginSD" ^
  --copyright "© 2026 OriginSD" ^
  --description "Dedicated cross-platform desktop PDF reader and editor" ^
  --icon "src\main\resources\icons\oqteris-app-icon.ico" ^
  --input target\ ^
  --main-jar "%JAR_FILENAME%" ^
  --main-class "org.originsd.oqteris.ui.OqterisLauncher" ^
  --runtime-image target\custom-runtime ^
  --win-menu ^
  --win-shortcut ^
  --win-dir-chooser ^
  %FILE_ASSOC_PARAM%

echo === Native Windows EXE Installer Created in dist\ ===
dir dist\*.exe
