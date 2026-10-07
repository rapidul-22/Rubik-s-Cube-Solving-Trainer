// ACTUAL HARDWARE PIN MAP FOR MAHIN'S ROBOT
// Physical wiring:
// RIGHT Spinner -> D4
// RIGHT Slider  -> D5
// LEFT Spinner  -> A3 (Arduino pin 17)
// LEFT Slider   -> A5 (Arduino pin 19)
// FRONT Spinner -> A1 (Arduino pin 15)
// FRONT Slider  -> A2 (Arduino pin 16)
// BACK Spinner  -> D2
// BACK Slider   -> D3

#define RIGHT_SLIDER_PIN 5
#define RIGHT_SPINNER_PIN 4

#define LEFT_SLIDER_PIN 19
#define LEFT_SPINNER_PIN 17

#define FRONT_SLIDER_PIN 16
#define FRONT_SPINNER_PIN 15

#define BACK_SLIDER_PIN 3
#define BACK_SPINNER_PIN 2

// true = calibration program
// false = normal robot API program
#define CALIBRATE false

#define SEQUENCE_BUFFER_SIZE 256
#define MOVE_BUFFER_SIZE 64
