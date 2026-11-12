const int pinIN1 = 4;
const int pinIN2 = 3;

void setup() {
  pinMode(pinIN1, OUTPUT);
  pinMode(pinIN2, OUTPUT);
}

void loop() {
  digitalWrite(pinIN1, HIGH);
  digitalWrite(pinIN2, LOW);
  delay(4000);

  digitalWrite(pinIN1, LOW);
  digitalWrite(pinIN2, LOW);
  delay(2000); 

}
