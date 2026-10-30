# OPlusX

OnePlus 13 / Android 15 / ColorOS 15.0.0.821 personal LSPosed toolbox.

## Stage 0

The diagnostic-only module captures the SystemUI status-bar View hierarchy and queries the anticipated output route for `AudioAttributes.USAGE_MEDIA`. It does not change SystemUI.

## Build

GitHub Actions workflow: [OPlusX Android Build](../../.github/workflows/oplusx-build.yml).

A successful workflow run publishes the debug APK as the `OPlusX-debug-apk` artifact. Open the repository Actions tab, select the workflow run, and download that artifact.

The debug APK is for private testing and LSPosed installation. It is not a production-signed release.
