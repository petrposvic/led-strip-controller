#include <SoftwareSerial.h>

// RGB led strip
#define PIN_B      9
#define PIN_R      10
#define PIN_G      11

// Bluetooth
#define PIN_BT_RX   4
#define PIN_BT_TX   3

SoftwareSerial Bluetooth =  SoftwareSerial(PIN_BT_TX, PIN_BT_RX);

// 0 gentle color replacing
// 1 single color only
// 2 single color only
// 3 turned off
int mode = 3, nextMode = mode;

int spd = 20, nextSpd = spd;

// Values for next mode
int nextR = 0, nextG = 0, nextB = 0;

char ch;

void setup() {
  pinMode(PIN_R, OUTPUT);
  pinMode(PIN_G, OUTPUT);
  pinMode(PIN_B, OUTPUT);

  Serial.begin(115200);
  Bluetooth.begin(19200);

  // Firstly turn on red
  analogWrite(PIN_R, nextR);
  analogWrite(PIN_G, nextG);
  analogWrite(PIN_B, nextB);
}

void loop() {
  if (mode == 0) {
    gentle();
  } else

  if (mode == 1 || mode == 2 || mode == 3) {
    check();
    delay(100);
  }
}

void gentle() {
  Serial.println("g+");
  fade_in(PIN_G);
  if (check()) return;

  Serial.println("r-");
  fade_out(PIN_R);
  if (check()) return;

  Serial.println("b+");
  fade_in(PIN_B);
  if (check()) return;

  Serial.println("g-");
  fade_out(PIN_G);
  if (check()) return;

  Serial.println("r+");
  fade_in(PIN_R);
  if (check()) return;

  Serial.println("g-");
  fade_in(PIN_G);
  if (check()) return;

  // Here is RGB

  Serial.println("r-");
  fade_out(PIN_R);
  if (check()) return;

  Serial.println("b -> r");
  fade_swap(PIN_B, PIN_R);
  if (check()) return;

  Serial.println("g -> b");
  fade_swap(PIN_G, PIN_B);
  if (check()) return;

  Serial.println("b-");
  fade_out(PIN_B);
  if (check()) return;
}

/**
 * return True for interrupt current mode.
 */
boolean check() {

  boolean ret = false;
  bt_proceed();

  if (mode != nextMode) {
    Serial.print("set mode ");
    Serial.print(nextMode);
    Serial.print(" from mode ");
    Serial.println(mode);

    mode = nextMode;

    analogWrite(PIN_R, nextR);
    analogWrite(PIN_G, nextG);
    analogWrite(PIN_B, nextB);

    ret = true;
  }

  if (spd != nextSpd) {
    Serial.print("set speed ");
    Serial.print(nextSpd);
    Serial.print(" from speed ");
    Serial.println(spd);

    spd = nextSpd;

    // Speed change shouldn't interrupt current mode
    // ret = true;
  }

  return ret;
}

void bt_proceed() {
  while (Bluetooth.available()) {
    ch = Bluetooth.read();

    if (ch == '0') {
      nextMode = 0;
      Bluetooth.println("OK mode 0");
      Serial.println("Set mode 0");

      // This mode starts with red
      nextR = 255;
      nextG = 0;
      nextB = 0;

      if (Bluetooth.available() && parse_speed(Bluetooth.read())) {
        // New nextSpd from parse_speed
      } else {
        nextSpd = 20;
      }

      Serial.print("Next speed ");
      Serial.println(nextSpd);
    } else

    if (ch == '1') {

      // Change mode for color swap. There are two modes, because
      // colors change only if modes change.
      if (mode != 1) {
        nextMode = 1;
      } else {
        nextMode = 2;
      }

      Bluetooth.println("OK mode 1 or 2");
      Serial.println("Set mode 1 or 2");

      if (Bluetooth.available()) {
        nextR = Bluetooth.read() * 2;
        if (Bluetooth.available()) {
          nextG = Bluetooth.read() * 2;
          if (Bluetooth.available()) {
            nextB = Bluetooth.read() * 2;
          } else {
            nextB = 0;
          }
        } else {
          nextG = 0;
          nextB = 0;
        }
      }

      Serial.print("Next colors (");
      Serial.print(nextR);
      Serial.print(",");
      Serial.print(nextG);
      Serial.print(",");
      Serial.print(nextB);
      Serial.println(")");
    }

    Serial.println(ch);
  }
}

boolean parse_speed(char ch) {
  Serial.print("-- parse_speed(");
  Serial.print(ch);
  Serial.println(") --");

  if (ch == '1') {
    nextSpd = 1;
  } else

  if (ch == '2') {
    nextSpd = 5;
  } else

  if (ch == '3') {
    nextSpd = 10;
  } else

  if (ch == '4') {
    nextSpd = 15;
  } else

  if (ch == '5') {
    nextSpd = 20;
  }
}

void fade_in(int pin) {
  for (int i = 0; i <= 255; i++) {
    analogWrite(pin, i);
    delay(spd);
  }
}

void fade_out(int pin) {
  for (int i = 255; i >= 0; i--) {
    analogWrite(pin, i);
    delay(spd);
  }
}

void fade_swap(int pin1, int pin2) {
  for (int i = 0; i <= 255; i++) {
    analogWrite(pin1, 255 - i);
    analogWrite(pin2, i);
    delay(spd);
  }
}

