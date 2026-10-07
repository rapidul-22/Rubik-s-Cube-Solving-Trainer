#include <Arduino.h>
#include <string.h>
#include "MyServo.h"
#include "SequenceManager.h"
#include "Config.h"
#include "API.h"
#include "Types.h"
#include "calibrate.h"

#define MAX_TOKENS  5

static char rxBuf[SEQUENCE_BUFFER_SIZE];    // This is extremely wasteful for the SEQ command, but oh well fuck it
static char* tokens[MAX_TOKENS];

void APISetup()
{
    Serial.println(F("Listening for API commands, type 'HELP' for list of commands."));
    Serial.setTimeout(2000); // safer for long lines
}

static int tokenize(char* line, char** tokens, int maxTokens)
{
    int count = 0;

    while (*line && count < maxTokens)
    {
        // Skip leading spaces
        while (*line == ' ')
            ++line;

        if (*line == '\0')
            break;

        tokens[count++] = line;

        // Advance to next space
        while (*line && *line != ' ')
            ++line;

        // Null-terminate token
        if (*line)
        {
            *line = '\0';
            ++line;
        }
    }

    return count;
}

void APILoop()
{
    if (!Serial.available())
        return;

    int len = Serial.readBytesUntil('\n', rxBuf, SEQUENCE_BUFFER_SIZE - 1);
    rxBuf[len] = '\0';

    // Trim trailing CR (Windows serial)
    if (len > 0 && rxBuf[len - 1] == '\r')
        rxBuf[len - 1] = '\0';

    if (rxBuf[0] == '\0')
        return;

    int tokenCount = tokenize(rxBuf, tokens, MAX_TOKENS);
    if (tokenCount == 0)
        return;

    const char* cmd = tokens[0];

    if (strcmp(cmd, "CAL") == 0) { printCalibrations(); return; }
    if (strcmp(cmd, "VERSION") == 0) { Serial.println(F("AUTOSCAN_HOLD_1")); return; }
    // --- HELP ---
    if (strcmp(cmd, "HELP") == 0)
    {
        Serial.println(F("Commands:"));
        Serial.println(F("HELP"));
        Serial.println(F("PING"));
        Serial.println(F("STATUS [servo]"));
        Serial.println(F("SEQ <string>|C"));
        Serial.println(F("MOVE <delay_ms> <orientation> <moves>"));
        return;
    }

    // --- SEQ ---
    if (strcmp(cmd, "SEQ") == 0)
    {
        if (tokenCount != 2)
        {
            Serial.println(F("ERR args"));
            return;
        }

        int res = seqManager.startSequence(tokens[1]);
        if (res == 0) Serial.println(F("OK"));
        else if (res == -1) Serial.println(F("ERR busy"));
        else if (res == -2) Serial.println(F("ERR format"));
        else Serial.println(F("ERR"));

        return;
    }

    // --- MOVE ---
    if (strcmp(cmd, "MOVE") == 0)
    {
        if (tokenCount != 4)
        {
            Serial.println(F("ERR args"));
            return;
        }

        if (seqManager.isBusy()) { Serial.println(F("ERR busy")); return; }
        int delay = atoi(tokens[1]);
        if (delay < 0)
        {
            Serial.println(F("ERR delay"));
            return;
        }

        int inputOrientation = atoi(tokens[2]);
        if (inputOrientation == 0)
            seqManager.orientation = ORIENT_NORMAL;
        else if (inputOrientation == 1)
            seqManager.orientation = ORIENT_INVERT;
        else
        {
            Serial.println(F("ERR orientation"));
            return;
        }

        int res = seqManager.startMoves(tokens[3], delay);
        if (res == 0) Serial.println(F("OK"));
        else if (res == -1) Serial.println(F("ERR busy"));
        else if (res == -2) Serial.println(F("ERR format"));
        else Serial.println(F("ERR"));

        return;
    }

    // --- STATUS ---
    if (strcmp(cmd, "STATUS") == 0)
    {
        if (tokenCount == 1)
        {
            Serial.println(seqManager.isBusy() ? "BUSY" : "IDLE");
            return;
        }

        if (tokenCount != 2)
        {
            Serial.println(F("ERR args"));
            return;
        }

        ServoType servo;
        if (!parseServoType(tokens[1][0], servo))
        {
            Serial.println(F("ERR servo_type"));
            return;
        }

        switch (servos[servo].getState())
        {
            case STATE_R: Serial.println('R'); break;
            case STATE_L: Serial.println('L'); break;
            case STATE_C: Serial.println('C'); break;
            case STATE_r: Serial.println('r'); break;
            case STATE_l: Serial.println('l'); break;
            default: Serial.println(F("ERR state")); break;
        }
        return;
    }

    // --- PING ---
    if (strcmp(cmd, "PING") == 0)
    {
        Serial.println(F("PONG"));
        return;
    }

    // --- UNKNOWN ---
    Serial.print(F("ERR cmd: "));
    Serial.println(cmd);
}
