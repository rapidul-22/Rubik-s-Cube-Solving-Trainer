"""Desktop logic checks with fake I/O; NOT an AVR build or a hardware test.
Run: python check_firmware.py (requires g++).
"""
from pathlib import Path
import tempfile, subprocess
root=Path(__file__).resolve().parent
with tempfile.TemporaryDirectory() as d:
 p=Path(d)
 (p/'Arduino.h').write_text(r'''
#pragma once
#include <cstdint>
#include <cstring>
#include <cstdlib>
#include <string>
#include <sstream>
#define F(x) x
#define OUTPUT 1
#define constrain(x,a,b) ((x)<(a)?(a):((x)>(b)?(b):(x)))
extern unsigned long clockMs;
inline unsigned long millis(){return clockMs;}
inline void pinMode(int,int){}
struct SerialMock {
 std::string input,output;
 void begin(int){} void setTimeout(int){}
 int available(){return input.size();}
 char read(){char c=input[0];input.erase(0,1);return c;}
 int readBytesUntil(char c,char* b,int n){int i=0;while(i<n&&!input.empty()){char v=read();if(v==c)break;b[i++]=v;}return i;}
 template<class T>void print(T x){std::ostringstream s;s<<x;output+=s.str();}
 template<class T>void println(T x){print(x);output+='\n';}
 void println(){output+='\n';}
};
extern SerialMock Serial;
''')
 (p/'Servo.h').write_text('''#pragma once
struct Servo { int pin=0,pulse=1500;bool on=false;
 void attach(int p,int,int){pin=p;on=true;} void detach(){on=false;}
 void writeMicroseconds(int p){pulse=p;} };
''')
 (p/'EEPROM.h').write_text('''#pragma once
#include <cstring>
struct EepromMock { unsigned char data[4096]{};
 template<class T> void get(int a,T&v){memcpy(&v,data+a,sizeof(T));}
 template<class T> void put(int a,const T&v){memcpy(data+a,&v,sizeof(T));}
};
extern EepromMock EEPROM;
''')
 (p/'main.cpp').write_text(r'''
#include "Arduino.h"
#include "EEPROM.h"
#include "MyServo.h"
#include "calibrate.h"
#include "SequenceManager.h"
#include "API.h"
#include <cassert>
#include <iostream>
unsigned long clockMs=0; SerialMock Serial; EepromMock EEPROM;
void setup(); void loop();
void finish(){for(int i=0;i<2000 && seqManager.isBusy();++i){clockMs+=100;seqManager.tick();}assert(!seqManager.isBusy());}
int main(){
 auto c=readCalibration(RIGHT_SPINNER);assert(c.C_us==1500&&c.R_us==2000&&c.L_us==1000);
 setup();assert(servos[0].getPin()==4&&servos[1].getPin()==5&&servos[2].getPin()==17&&servos[3].getPin()==19);
 assert(servos[0].getState()==STATE_C && servos[0].pulseWidth()==1500);
 ServoCal values[8]={{500,2400,1500,0},{1550,500,1450,0},{500,2400,1500,0},{1600,550,1500,0},{650,2450,1500,0},{885,2500,1500,0},{600,2400,1500,0},{1285,2400,1375,0}};
 for(int i=0;i<8;++i){EEPROM.put(102+i*sizeof(ServoCal),values[i]);auto v=readCalibration(static_cast<ServoType>(i));assert(v.L_us==values[i].L_us&&v.R_us==values[i].R_us&&v.C_us==values[i].C_us);}
 Serial.input="VERSION\n";APILoop();assert(Serial.output.find("AUTOSCAN_HOLD_1")!=std::string::npos);
 assert(seqManager.startMoves("R2",350)==-2);
 std::string longMoves(64,'R');assert(seqManager.startMoves(longMoves.c_str(),350)==-2);
 assert(seqManager.startSequence("r?")==-2);
 assert(seqManager.startMoves("Rr",350)==0);clockMs+=100;seqManager.tick();
 Serial.input="MOVE 350 1 F\n";APILoop();assert(seqManager.orientation==ORIENT_NORMAL);
 assert(seqManager.startSequence("C")==0);assert(seqManager.halted);
 assert(seqManager.startMoves("Ff",350)==0);assert(std::string(seqManager.moveBuf)=="Ff");assert(!seqManager.halted);finish();
 assert(seqManager.startMoves("UuRr",350)==0);finish();assert(seqManager.orientation==ORIENT_NORMAL);
 assert(seqManager.startMoves("DdRr",350)==0);finish();assert(seqManager.orientation==ORIENT_NORMAL);
 assert(seqManager.startSequence("rrrrFLBL350RRLR350fLbRFCBClrlr350") == 0);finish();
 {int c=0,r=0;for(int i=1;i<8;i+=2){c+=servos[i].getState()==STATE_C;r+=servos[i].getState()==STATE_R;}assert(c==2 && r==2);}
 assert(seqManager.startSequence("lCFLBLRRLR350fCfCbCbC350FCBCFCBCRCLC350rClrlrFLBL350RRLR350fRbLFCBCrrrr350") == 0);finish();
 {int c=0,r=0;for(int i=1;i<8;i+=2){c+=servos[i].getState()==STATE_C;r+=servos[i].getState()==STATE_R;}assert(c==2 && r==2);}
 assert(seqManager.startSequence("lrrCFLBLRRLR350fCfCbCbC350FCBCFCBCRCLC350lCbrbrRLLL350BRFR350rLlRRCLCfrfr350") == 0);finish();
 {int c=0,r=0;for(int i=1;i<8;i+=2){c+=servos[i].getState()==STATE_C;r+=servos[i].getState()==STATE_R;}assert(c==2 && r==2);}
 assert(seqManager.startSequence("brfCRLLLBRFR350rCrClClC350RCLCRCLCBCFC350bCfrfrRLLL350BRFR350rRlLRCLCbrbr350") == 0);finish();
 {int c=0,r=0;for(int i=1;i<8;i+=2){c+=servos[i].getState()==STATE_C;r+=servos[i].getState()==STATE_R;}assert(c==2 && r==2);}
 assert(seqManager.startSequence("frbCfrbCRLLLBRFR350rCrClClC350RCLCRCLCBCFC350fC350RLLL350FRBR350fRbL350FLBL350RRLR350fCfCbCbC350RCLC350FCBCrrrrFLBL350RRLR350fLbRFCBClrlr350") == 0);finish();
 {int c=0,r=0;for(int i=1;i<8;i+=2){c+=servos[i].getState()==STATE_C;r+=servos[i].getState()==STATE_R;}assert(c==2 && r==2);}
 assert(seqManager.startSequence("rrlCFLBLRRLR350fCfCbCbC350FCBCFCBCRCLC350rClrlrFLBL350RRLR350fRbLFCBCrrrr350") == 0);finish();
 {int c=0,r=0;for(int i=1;i<8;i+=2){c+=servos[i].getState()==STATE_C;r+=servos[i].getState()==STATE_R;}assert(c==2 && r==2);}
 assert(seqManager.startSequence("lrrCFLBLRRLR350fCfCbCbC350FCBCFCBCRCLC350lC350RLLL350FRBR350fLbR350FLBL350RRLR350fCfCbCbC350RCLC350FCBCrrrrFLBL350RRLR350fLbRFCBClrlr350") == 0);finish();
 {int c=0,r=0;for(int i=1;i<8;i+=2){c+=servos[i].getState()==STATE_C;r+=servos[i].getState()==STATE_R;}assert(c==2 && r==2);}
 assert(seqManager.startSequence("lCFLBLRRLR350fCfCbCbC350FCBCFCBCRCLC350rClrlrFLBL350RRLR350fRbLFCBCrrrr350") == 0);finish();
 {int c=0,r=0;for(int i=1;i<8;i+=2){c+=servos[i].getState()==STATE_C;r+=servos[i].getState()==STATE_R;}assert(c==2 && r==2);}
 assert(seqManager.startSequence("lrrCFLBLRRLR350fCfCbCbC350FCBCFCBCRCLC350lCbrbrRLLL350BRFR350rLlRRCLCfrfr350") == 0);finish();
 {int c=0,r=0;for(int i=1;i<8;i+=2){c+=servos[i].getState()==STATE_C;r+=servos[i].getState()==STATE_R;}assert(c==2 && r==2);}
 assert(seqManager.startSequence("brfCRLLLBRFR350rCrClClC350RCLCRCLCBCFC350bCfrfrRLLL350BRFR350rRlLRCLCbrbr350") == 0);finish();
 {int c=0,r=0;for(int i=1;i<8;i+=2){c+=servos[i].getState()==STATE_C;r+=servos[i].getState()==STATE_R;}assert(c==2 && r==2);}
 assert(seqManager.startSequence("frbCfrbCRLLLBRFR350rCrClClC350RCLCRCLCBCFC350fC350RLLL350FRBR350fRbL350FLBL350RRLR350fCfCbCbC350RCLC350FCBCrrrrFLBL350RRLR350fLbRFCBClrlr350") == 0);finish();
 {int c=0,r=0;for(int i=1;i<8;i+=2){c+=servos[i].getState()==STATE_C;r+=servos[i].getState()==STATE_R;}assert(c==2 && r==2);}
 assert(seqManager.startSequence("rrlCFLBLRRLR350fCfCbCbC350FCBCFCBCRCLC350rClrlrFLBL350RRLR350fRbLFCBCrrrr350") == 0);finish();
 {int c=0,r=0;for(int i=1;i<8;i+=2){c+=servos[i].getState()==STATE_C;r+=servos[i].getState()==STATE_R;}assert(c==2 && r==2);}
 assert(seqManager.startSequence("lrrCFLBLRRLR350fCfCbCbC350FCBCFCBCRCLC350lC350RLLL350FRBR350fLbR350FLBL350RRLR350fCfCbCbC350RCLC350FCBC350") == 0);finish();
 for(int i=0;i<8;i++) assert(servos[i].getState()==STATE_C);
 Serial.input="PING\n";APILoop();assert(Serial.output.find("PONG")!=std::string::npos);
 std::cout<<"PASS: startup C, EEPROM records, pins, input validation, busy guard, abort/restart, U/D orientation, full scan/review commands, VERSION and PING\n";
}
''')
 sources=[str(x) for x in (root/'Firmware/Arduino').glob('*.cpp')]
 subprocess.run(['g++','-std=c++11','-I'+d,'-I'+str(root/'Firmware/Arduino'),*sources,'-x','c++',str(root/'Firmware/Arduino/Arduino.ino'),str(p/'main.cpp'),'-o',str(p/'check')],check=True)
 subprocess.run([str(p/'check')],check=True)
