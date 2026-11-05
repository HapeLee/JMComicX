Reader mode gesture smoke checks

Use an isolated Android emulator with the small_phone profile (720 x 1280 pixels).
The temporary activity generates local chapter images with explicit page labels;
it does not require an account or real comics. It is not part of the delivered APK.

From the repository root:
  powershell -File tools/reader-smoke/prepare.ps1
  .\gradlew.bat :app:assembleDebug --no-daemon
  android install --device emulator-5554 --apks app/build/outputs/apk/debug/app-debug.apk
  python tools/reader-smoke/checks.py
  python tools/reader-smoke/spreads.py

JMX_SMOKE_DEVICE overrides the emulator serial (default emulator-5554).
JMX_SMOKE_OUTPUT overrides the report directory (default app/build/reader-smoke).

After testing, always remove the temporary activity and rebuild:
  powershell -File tools/reader-smoke/prepare.ps1 -Clean
  .\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --no-daemon

The fixture uses the same volume key dispatcher as MainActivity. Each check waits
for the layout and verifies the recorded chapter/local-page identity. Layouts are
retained for inspection. A crash or unexpected progress stops the check immediately.
The fixture can inject a two-finger pinch via a single-top intent. The checks verify
that a zoomed page pans without changing pages and that double-tap restores paging.
The spreads suite rotates the emulator and checks automatic/forced/disabled pairing,
physical RTL order, cover/wide-page protection, exact second-leaf slider seeks,
whole-spread volume paging, and seamless chapter boundaries. It ends in portrait
with a forced dual-page fixture whose top/bottom colors can be inspected visually.
Long-running memory behavior and device-specific gesture feel still need further
testing on physical devices.
