#include <Servo.h>

/* 
 Arduino and android powered sidereal tracking mount
 The motor runs at about the right speed for a short exposure.
 some simple calculus is needed to determine a function for drive speed, but it isn't needed for now.
 Luke Krasner
 luke@lukekrasner.com
 */


//variable deffinitions
Servo hAlignServo;
Servo vAlignServo;
Servo panServo;
Servo tiltServo;
/*
communication protocal:
 byte 1- header in HEX
 byte 2- size of message (not including header or size byte) in DEC
 byte 3- message
 byte 4- halt; 0x2E
 */

const byte align = 0x01;
const byte camera = 0x02;

const byte left = 0x11;
const byte right = 0x12;
const byte up = 0x13;
const byte down = 0x14;

const byte powerOff = 0x20;
const byte powerOn = 0x21;
const byte trackOff = 0x30;
const byte trackOn = 0x31;

const byte halt = 0x2E;

//all the pins used
const int hAlignPin = 4;
const int vAlignPin = 5;
const int tiltPin = 6;
const int panPin = 7;
const int trackPin = 3;
const int tachPin = 2;
const int powerPin = 48;

//Servo positions
int hAlignPos = 90;
int vAlignPos = 90;
int tiltPos = 90;
int panPos = 90;

//buffer to read the data into
byte data[3];

//useless, but arduino is stupid and I need this to tell how many bytes were read even though I don't care.
int bytesRead;

void setup(){
  Serial.begin(9600);  //start the serial conection @ 9600 bits/s
  
  //attach the servos.
  vAlignServo.attach(vAlignPin);
  hAlignServo.attach(hAlignPin);
  panServo.attach(panPin);
  tiltServo.attach(tiltPin);
  
  //set the power pin (relay) as an output and turn it off to start with.
  pinMode(powerPin, OUTPUT);
  digitalWrite(powerPin, LOW);
}


void loop(){  //keep it clean and call other functions

  getData();
  setServos();

}



void getData(){  //gets data from serial if it available and sets new positions/speeds
  if(Serial.available() >=3){  //do nothing if there is nothing to read.
    for(int i = 0; i<3; i++){  //read 3 bytes from the buffer
      data[i] = Serial.read();
    }
    //check integrity.  If it does not end in halt, try again. this might form a chain of failures, but it will catch up quickly.
    if(data[2] = halt);
    else getData();
  }
  switch (data[0]) {  //camera or alignment?

    case align:
      switch (data[1]) {  //what to do to the alignment.
  
      case powerOn:  //power and track stuff works in either mode
        digitalWrite(powerPin, HIGH);
        break;
  
      case powerOff:
        digitalWrite(powerPin, LOW);
        break;
        
      case trackOn:
        analogWrite(trackPin, 200);
        break;
        
      case trackOff:
        analogWrite(trackPin, 0);
        break;
  
      case left:
        hAlignPos--;
        break;
        
      case right:
        hAlignPos++;
        break;
        
      case up:
        vAlignPos--;
        break;
        
      case down:
        vAlignPos++;
        break;
    }
    break;

  case camera:
    switch (data[1]){  //what to do with the pan and tilt.
      
      case powerOn:  //power and track stuff works in camera or align mode.
        digitalWrite(powerPin, HIGH);
        break;
  
      case powerOff:
        digitalWrite(powerPin, LOW);
        break;
        
      case trackOn:
        analogWrite(trackPin, 200);
        break;
        
      case trackOff:
        analogWrite(trackPin, 0);
        break;
        
      case left:
        panPos--;
        break;
        
      case right:
        panPos++;
        break;
        
      case up:
        tiltPos--;
        break;
        
      case down:
        tiltPos++;
        break;

    }
  }
  //reset buffer for new reading.
  data[0] = 0;
  data[1] = 0;
  data[2] = 0;
}

void setServos(){  //sets all the servos to their positions

  hAlignServo.write(hAlignPos);
  vAlignServo.write(vAlignPos);
  tiltServo.write(tiltPos);
  panServo.write(panPos);

}




