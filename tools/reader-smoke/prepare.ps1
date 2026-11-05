param([switch]$Clean)

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$manifestPath = Join-Path $repoRoot 'app/src/debug/AndroidManifest.xml'
$activityPath = Join-Path $repoRoot 'app/src/debug/kotlin/dev/jmx/client/ReaderModesSmokeActivity.kt'
$expectedManifest = '<manifest xmlns:android="http://schemas.android.com/apk/res/android"><application><activity android:name=".ReaderModesSmokeActivity" android:exported="true" android:theme="@style/JmxTheme" /></application></manifest>'
$expectedActivity = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'ReaderModesSmokeActivity.kt.template') -Raw

if ($Clean) {
    foreach ($targetPath in @($manifestPath, $activityPath)) {
        if (Test-Path -LiteralPath $targetPath) {
            $content = Get-Content -LiteralPath $targetPath -Raw
            $expected = if ($targetPath -eq $manifestPath) { $expectedManifest } else { $expectedActivity }
            if ($content.Trim() -ne $expected.Trim()) {
                throw "Refusing to remove a modified debug file: $targetPath"
            }
        }
    }
    foreach ($targetPath in @($manifestPath, $activityPath)) {
        if (Test-Path -LiteralPath $targetPath) { Remove-Item -LiteralPath $targetPath }
    }
    Write-Output 'Removed temporary reader smoke activity. Rebuild the debug APK before delivery.'
    exit
}

if ((Test-Path -LiteralPath $manifestPath) -or (Test-Path -LiteralPath $activityPath)) {
    throw 'Debug smoke files already exist. Inspect them before preparing another run.'
}
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $activityPath) | Out-Null
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'ReaderModesSmokeActivity.kt.template') -Destination $activityPath
Set-Content -LiteralPath $manifestPath -Encoding utf8 -Value $expectedManifest
Write-Output 'Prepared temporary debug reader smoke activity. No release sources were changed.'
