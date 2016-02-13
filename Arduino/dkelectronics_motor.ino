
#include <AFMotor.h>
AF_DCMotor motor(2, MOTOR12_1KHZ);
// create motor #2, 1KHz pwm

AF_DCMotor motorBack(1, MOTOR12_1KHZ);

#define F 0x7
#define B 0x6
#define L 0x2
#define R 0x3
#define STOP 0x9
#define STRAIGHT 0x5


void setup() {
 Serial.begin(57600); 
 //set up Serial library at 9600 bps
 Serial.println("Motor test!");

 motor.setSpeed(255);  // set the speed to 200/255
 motorBack.setSpeed(255 );
}
void loop() {

  if (Serial.available()) {
    //Use with Serial/Bluetooth
//    int command = Serial.parseInt();

//Use the line below with the Lunar
int command = Serial.read();


    Serial.println(command);
    switch (command) {
      case F:
        motorBack.run(FORWARD);
      break;
      case B:
        motorBack.run(BACKWARD);
      break; 
      case L:
        motorBack.run(FORWARD);
        motor.run(FORWARD);
      break; 
      case R:
        motorBack.run(FORWARD);
        motor.run(BACKWARD);
      break;
      case STRAIGHT:
        motorBack.run(FORWARD);
        motor.run(RELEASE);
        break;
      case STOP:
        motorBack.run(RELEASE);
        motor.run(RELEASE);
      break;
    }//end switch
     
  }//end if
  
 //Serial.print("tick");

// motorBack.run(FORWARD);
// delay(1000);
// motor.run(FORWARD); // turn it on going forward
// delay(1000);
// //Serial.print("tock");
// motor.run(BACKWARD); // the other way
// delay(1000);
//
// //Serial.print("tack");
// motor.run(RELEASE); //stopped
// motorBack.run(RELEASE);
// delay(10000);
} 
