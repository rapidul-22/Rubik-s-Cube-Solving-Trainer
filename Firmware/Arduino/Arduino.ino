#include "calibrate.h"
#include "MyServo.h"
#include "SequenceManager.h"
#include "API.h"

void setup() {
    Serial.begin(9600);
    Serial.setTimeout(3000);
    calibrateSetup();   // Initialize EEPROM where calibrations are stored
#if !CALIBRATE
    APISetup();        // Setup API if not in calibration mode
#endif
}

void loop() {
    // Keep torque during the 10-second scan and manual review.
    if (!seqManager.halted) attachAllServos();
    else detachAllServos();

#if CALIBRATE
    calibrateLoop();
#else
    APILoop();  // Listen for API commands
    int res = seqManager.tick();    // If ongoing sequence, keep going
    if (res < 0)
    {
        Serial.print(F("SEQ ERR "));
        Serial.println(res);
    }
#endif
}
