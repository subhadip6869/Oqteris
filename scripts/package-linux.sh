#!/usr/bin/env bash
# ==============================================================================
# Oqteris Desktop - Linux Native Packaging Script with Optimized Bundled JRE
# Target outputs: .deb and .rpm installers with zero host JRE dependency
# Adheres to OriginSD Desktop Application Master Architecture Standard
# ==============================================================================

set -euo pipefail

APP_NAME="Oqteris"
APP_VERSION="1.1.0"
APP_VENDOR="OriginSD"
APP_DESCRIPTION="Oqteris PDF Reader"
MAIN_CLASS="org.originsd.oqteris.ui.OqterisLauncher"
ICON_PATH="src/main/resources/icons/oqteris-app-icon.png"

echo "=== [1/4] Building Application JAR with Maven ==="
mvn clean package -DskipTests --batch-mode

SHADED_JAR=$(ls target/oqteris-*-all.jar | head -n 1)
JAR_FILENAME=$(basename "$SHADED_JAR")

echo "=== [2/4] Generating Optimized Bundled JRE with jlink ==="
rm -rf target/custom-runtime

MODULES="java.base,java.compiler,java.desktop,java.instrument,java.naming,java.net.http,java.rmi,java.scripting,java.sql,java.xml,jdk.crypto.ec,jdk.localedata,jdk.unsupported"

jlink --no-header-files \
      --no-man-pages \
      --strip-debug \
      --compress=2 \
      --add-modules "$MODULES" \
      --output target/custom-runtime

echo "Optimized JRE created at target/custom-runtime"

echo "=== [3/4] Packaging Native DEB Installer with jpackage ==="
mkdir -p dist

# Stage only the shaded JAR into a dedicated input folder so that intermediate build files,
# unshaded jars, and target/custom-runtime do not leak into the /app directory
rm -rf target/package-input
mkdir -p target/package-input
cp "$SHADED_JAR" target/package-input/

jpackage \
  --type deb \
  --dest dist/ \
  --name "$APP_NAME" \
  --app-version "$APP_VERSION" \
  --vendor "$APP_VENDOR" \
  --copyright "© 2026 OriginSD" \
  --description "$APP_DESCRIPTION" \
  --icon "$ICON_PATH" \
  --input target/package-input \
  --main-jar "$JAR_FILENAME" \
  --main-class "$MAIN_CLASS" \
  --runtime-image target/custom-runtime \
  --linux-shortcut \
  --linux-menu-group "Office;Viewer;"

echo "=== [4/4] Packaging Native RPM Installer with jpackage (if rpmbuild available) ==="
if command -v rpmbuild &> /dev/null; then
  jpackage \
    --type rpm \
    --dest dist/ \
    --name "$APP_NAME" \
    --app-version "$APP_VERSION" \
    --vendor "$APP_VENDOR" \
    --copyright "© 2026 OriginSD" \
    --description "$APP_DESCRIPTION" \
    --icon "$ICON_PATH" \
    --input target/package-input \
    --main-jar "$JAR_FILENAME" \
    --main-class "$MAIN_CLASS" \
    --runtime-image target/custom-runtime \
    --linux-shortcut \
    --linux-menu-group "Office;Viewer;"
fi

echo "=== Native Linux Packages generated successfully in dist/ ==="
ls -lh dist/
