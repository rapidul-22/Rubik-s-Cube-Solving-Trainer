# Verification boundaries

Executed here:
- `python3 check_firmware.py`: PASS. Desktop g++ compiled all final firmware sources against fake Arduino/Servo/EEPROM interfaces. Includes all physical scan command strings, two-contact/two-release target state checks, final C targets, calibration read format, pin map, abort/restart, U/D orientation and API version/PING.
- `java -cp AndroidApp/app/libs/twophase.jar SolverCheck.java`: PASS. Actual supplied solver JAR runs on JVM. A solved cube is accepted; a generated valid cube produces moves. The app explicitly skips solver motion for the known solved facelet string.
- Gradle wrapper and solver JAR ZIP integrity checked; XML and package contents checked.

Blocked / not executed:
- Gradle 8.13 download failed with `java.net.SocketException: Network is unreachable`.
- No Android SDK is installed here. Android compilation, included Kotlin/JUnit tests and APK installation have not been verified.
- No AVR compiler/real Nano, phone camera, Bluetooth module or robot was available. Desktop C++ does not reproduce AVR integer widths, SRAM limits, pulse timing or mechanics.
- Perspective correction and Lab classification are source implementations, not a measured accuracy improvement on Mahin's phone. Test on solved and simple-scramble cubes first.
- YouTube URL could not be opened. Exact video parity and camera clearance are unverified.

Run the provided build/unit tests and hardware acceptance steps before treating this as a demonstrated working robot.
