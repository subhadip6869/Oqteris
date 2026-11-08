<#
 ==============================================================================
 Oqteris Desktop - Windows Native Packaging Script with Optimized Bundled JRE
 Target output: Native Windows EXE installer (with zero host JRE dependency)
 Adheres to OriginSD Desktop Application Master Architecture Standard
 ==============================================================================
#>

$ErrorActionPreference = "Stop"

$APP_NAME = "Oqteris"
$APP_VERSION = "1.1.0"
$APP_VENDOR = "OriginSD"
$APP_DESCRIPTION = "Oqteris PDF Reader"
$MAIN_CLASS = "org.originsd.oqteris.ui.OqterisLauncher"
$ICON_PATH = "src\main\resources\icons\oqteris-app-icon.ico"
$FILE_ASSOC = "packaging\windows\pdf-association.properties"

Write-Host "=== [1/3] Building Shaded Application JAR with Maven ===" -ForegroundColor Cyan
mvn clean package -DskipTests --batch-mode

$shadedJar = Get-ChildItem target/oqteris-*-all.jar | Select-Object -First 1
if (-not $shadedJar) {
    throw "Fat JAR not found in target/!"
}
$jarFilename = $shadedJar.Name

Write-Host "=== [2/3] Generating Minimal Bundled JRE with jlink ===" -ForegroundColor Cyan
if (Test-Path target/custom-runtime) {
    Remove-Item -Recurse -Force target/custom-runtime
}

$modules = "java.base,java.compiler,java.desktop,java.instrument,java.naming,java.net.http,java.rmi,java.scripting,java.sql,java.xml,jdk.crypto.ec,jdk.localedata,jdk.unsupported"

jlink --no-header-files `
      --no-man-pages `
      --strip-debug `
      --compress=2 `
      --add-modules $modules `
      --output target/custom-runtime

Write-Host "Optimized JRE created at target/custom-runtime" -ForegroundColor Green

if (-not (Test-Path dist)) {
    New-Item -ItemType Directory -Path dist | Out-Null
}

Write-Host "=== [3/3] Generating Native Windows EXE Installer with jpackage ===" -ForegroundColor Cyan
# Stage only the shaded JAR into a dedicated input folder so that intermediate build files,
# unshaded jars, and target/custom-runtime do not leak into the /app directory
$packageInput = "target\package-input"
if (Test-Path $packageInput) {
    Remove-Item -Recurse -Force $packageInput
}
New-Item -ItemType Directory -Path $packageInput | Out-Null
Copy-Item $shadedJar.FullName -Destination $packageInput

$jpackageArgs = @(
    "--type", "exe",
    "--dest", "dist",
    "--name", $APP_NAME,
    "--app-version", $APP_VERSION,
    "--vendor", $APP_VENDOR,
    "--copyright", "© 2026 OriginSD",
    "--description", $APP_DESCRIPTION,
    "--icon", $ICON_PATH,
    "--input", $packageInput,
    "--main-jar", $jarFilename,
    "--main-class", $MAIN_CLASS,
    "--runtime-image", "target/custom-runtime",
    "--win-menu",
    "--win-shortcut",
    "--win-dir-chooser"
)

if (Test-Path $FILE_ASSOC) {
    $jpackageArgs += @("--file-associations", $FILE_ASSOC)
}

& jpackage @jpackageArgs

Write-Host "`n=== Native Windows EXE Installer Created Successfully in dist/ ===" -ForegroundColor Green
Get-ChildItem dist/*.exe
