#include <Arduino.h>
#include <EEPROM.h>
#include "MyServo.h"
#include "calibrate.h"
#include "Config.h"

#define EEPROM_MAGIC 0xCB02
#define EEPROM_MAGIC_ADDR 100
#define EEPROM_CALIBRATION_START_ADDR 102 // After magic number

// Select one servo and calibrate one at a time
static int selectedServo = 0;
static ServoState mode = STATE_C;
static const int step = 5;

void initEEPROM()
{
    uint16_t magic;
    EEPROM.get(EEPROM_MAGIC_ADDR, magic);

    if (magic != EEPROM_MAGIC)
    {
        for (int i = 0; i < NUM_SERVOS; i++)
        {
            ServoCal initCal = {1000, 2000, 1500, 0};
            EEPROM.put(EEPROM_CALIBRATION_START_ADDR + i * sizeof(ServoCal), initCal);
        }

        EEPROM.put(EEPROM_MAGIC_ADDR, EEPROM_MAGIC);
    }
}

ServoCal readCalibration(ServoType type)
{
    // MyServo objects are created before setup() runs. On a fresh Nano the
    // EEPROM may not contain valid calibration data yet, so use a safe
    // neutral calibration until initEEPROM() creates the EEPROM records.
    uint16_t magic;
    EEPROM.get(EEPROM_MAGIC_ADDR, magic);

    if (magic != EEPROM_MAGIC)
    {
        ServoCal safeCal = {1000, 2000, 1500, 0};
        return safeCal;
    }

    ServoCal cal;
    EEPROM.get(EEPROM_CALIBRATION_START_ADDR + type * sizeof(ServoCal), cal);
    return cal;
}

void writeCalibration(ServoType type, ServoCal cal)
{
    Serial.print(F("Writing calibration for servo type: "));
    char buffer[20];
    servoTypeToString(type, buffer, sizeof(buffer));
    Serial.print(buffer);
    Serial.print(F("\n"));
    EEPROM.put(EEPROM_CALIBRATION_START_ADDR + type * sizeof(ServoCal), cal);
}

void writeAllCalibrations()
{
    Serial.println(F("Writing all calibrations to EEPROM..."));
    for (int i = 0; i < NUM_SERVOS; i++)
    {
        ServoCal cal = servos[i].getCalibration();
        writeCalibration(static_cast<ServoType>(i), cal);
    }
    Serial.println(F("Done writing calibrations, please set #define CALIBRATE false and re-upload the sketch."));
}

// Print status of selected servo
void printStatus()
{
    Serial.print(F("Selected Servo: "));
    Serial.print(selectedServo);
    Serial.print(F(" ("));
    char buffer[20];
    servoTypeToString(servos[selectedServo].getType(), buffer, sizeof(buffer));
    Serial.print(buffer);
    Serial.print(F(")"));
    Serial.print(F(" | Mode: "));
    ServoCal cal = servos[selectedServo].getCalibration();
    switch (mode)
    {
    case STATE_C:
        Serial.print(F("CENTER"));
        Serial.print(F(" | Center us: "));
        Serial.print(cal.C_us);
        Serial.print(F(" | Deviation: "));
        Serial.print(cal.CD_us);
        Serial.println(F(" us"));
        Serial.print(F(" | Pulse: "));
        Serial.print(servos[selectedServo].pulseWidth());
        break;
    case STATE_L:
        Serial.print(F("LEFT"));
        Serial.print(F(" | Pulse: "));
        Serial.print(servos[selectedServo].pulseWidth());
        Serial.println(F(" us"));
        break;
    case STATE_R:
        Serial.print(F("RIGHT"));
        Serial.print(F(" | Pulse: "));
        Serial.print(servos[selectedServo].pulseWidth());
        Serial.println(F(" us"));
        break;
    }
}

void printCalibrations()
{
    char buffer[20];

    Serial.println();
    Serial.println(F("Idx Pin Type     L(us) R(us) C(us) Dev(us)"));
    Serial.println(F("--- --- -------- ----- ----- ----- -------"));

    for (int i = 0; i < NUM_SERVOS; i++)
    {
        ServoCal cal = servos[i].getCalibration();

        Serial.print(i);
        Serial.print(F("   "));
        Serial.print(servos[i].getPin());
        Serial.print(F("   "));

        servoTypeToString(servos[i].getType(), buffer, sizeof(buffer));
        Serial.print(buffer);
        Serial.print(F(" "));

        Serial.print(cal.L_us);
        Serial.print(F("  "));
        Serial.print(cal.R_us);
        Serial.print(F("  "));
        Serial.print(cal.C_us);
        Serial.print(F("  "));
        Serial.println(cal.CD_us);
    }

    Serial.println();
}


void printHelp()
{
    Serial.println(F("Servo calibration tool commands:"));
    Serial.println(F("0-7 : select servo"));
    Serial.println(F("c   : set state CENTER to calibrate center"));
    Serial.println(F("l   : set state LEFT to calibrate left"));
    Serial.println(F("r   : set state RIGHT to calibrate right"));
    Serial.println(F("+/- : increase/decrease step size"));
    Serial.println(F("*// : increase/decrease step size * 10"));
    Serial.println(F("</> : increase/decrease center deviation (spinners only)"));
    Serial.println(F("p   : Print current calibration values"));
    Serial.println(F("w   : Write calibration to EEPROM"));
    Serial.println();
}

void calibrateSetup()
{
    initEEPROM();

#if CALIBRATE // Only setup if in calibration mode, otherwise this is not your concern
    for (int i = 0; i < NUM_SERVOS; i++)
    {
        pinMode(servos[i].getPin(), OUTPUT);
        servos[i].setState(STATE_C);
    }
    printStatus();
#endif
}

void calibrateLoop()
{
    if (!Serial.available())
        return;

    char c = Serial.read();

    if (c == 'h')
    {
        printHelp();
    }
    else if (c >= '0' && c <= '7')
    {
        selectedServo = c - '0';
        printStatus();
    }
    else if (c == 'c')
    {
        mode = STATE_C;
        servos[selectedServo].setState(STATE_C);
        printStatus();
    }
    else if (c == 'l')
    {
        mode = STATE_L;
        servos[selectedServo].setState(STATE_L);
        printStatus();
    }
    else if (c == 'r')
    {
        mode = STATE_R;
        servos[selectedServo].setState(STATE_R);
        printStatus();
    }
    else if (c == '+')
    {
        servos[selectedServo].adjustCalibration(step);
        printStatus();
    }
    else if (c == '-')
    {
        servos[selectedServo].adjustCalibration(-step);
        printStatus();
    }
    else if (c == '*')
    {
        servos[selectedServo].adjustCalibration(step * 10);
        printStatus();
    }
    else if (c == '/')
    {
        servos[selectedServo].adjustCalibration(-step * 10);
        printStatus();
    }
    else if (c == '>')
    {
        servos[selectedServo].adjustDeviation(step);
        printStatus();
    }
    else if (c == '<')
    {
        servos[selectedServo].adjustDeviation(-step);
        printStatus();
    }
    else if (c == 'p')
    {
        printCalibrations();
    }
    else if (c == 'w')
    {
        writeAllCalibrations();
    }
}
