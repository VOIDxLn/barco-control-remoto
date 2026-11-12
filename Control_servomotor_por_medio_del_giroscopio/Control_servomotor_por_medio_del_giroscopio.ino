#include <Adafruit_MPU6050.h>
#include <Adafruit_Sensor.h>
#include <Wire.h>
#include <ESP32Servo.h>

Servo servo;
Adafruit_MPU6050 mpu;

#define I2C_SDA 8
#define I2C_SCL 9

// Definición de pines
const int servoPin = 3;
const int pinIN1 = 4;
const int pinIN2 = 5;
const int trigPin = 6;
const int echoPin = 7;
const int enAPin = 2; // Pin PWM para regular la velocidad de la hélice

float anguloActual = 90.0;
float offsetAccelY = 0.0; 

// Configuración PWM moderna para ESP32
const int freq = 30000;
const int resolution = 8;

void setup() {
  Serial.begin(115200);

  // Configuración de pines de control de motor y ultrasonido
  pinMode(pinIN1, OUTPUT);
  pinMode(pinIN2, OUTPUT);
  pinMode(trigPin, OUTPUT);
  pinMode(echoPin, INPUT);
  pinMode(enAPin, OUTPUT);

  // Inicializar canal PWM para el control de velocidad del motor
  ledcAttach(enAPin, freq, resolution);

  // Configuración segura del Servo (exactamente como en tu código sencillo)
  servo.attach(servoPin);

  // Inicialización del bus I2C y MPU6050
  Wire.begin(I2C_SDA, I2C_SCL);

  if(!mpu.begin(0x68, &Wire)) {
    Serial.println("No se pudo encontrar el MPU6050!");
    while (1) { delay(10); }
  }

  servo.write(90); // Centrar timón inmediatamente

  mpu.setAccelerometerRange(MPU6050_RANGE_8_G);
  mpu.setGyroRange(MPU6050_RANGE_500_DEG);
  mpu.setFilterBandwidth(MPU6050_BAND_21_HZ);

  // Mantener motor apagado durante los primeros segundos
  digitalWrite(pinIN1, LOW);
  digitalWrite(pinIN2, LOW);
  ledcWrite(enAPin, 0);

  Serial.println("Sistema encendido. Centrando timón y esperando 3 segundos...");
  delay(3000);

  // Iniciar avance hacia adelante
  digitalWrite(pinIN1, HIGH);
  digitalWrite(pinIN2, LOW);

  // Rampa de aceleración suave (Soft-Start)
  for (int velocidad = 0; velocidad <= 180; velocidad += 15) {
    ledcWrite(enAPin, velocidad);
    delay(50);
  }
  
  Serial.println("¡Navegación autónoma iniciada!");
}

void loop() {
  // 1. Lectura del sensor ultrasónico (HC-SR04)
  digitalWrite(trigPin, LOW);
  delayMicroseconds(2);
  digitalWrite(trigPin, HIGH);
  delayMicroseconds(10);
  digitalWrite(trigPin, LOW);

  long duracion = pulseIn(echoPin, HIGH, 20000);
  int distancia = (duracion == 0) ? 999 : (duracion * 0.034 / 2);
  
  int anguloDeseado = 90;

  // 2. Lógica de evasión de obstáculos o estabilización por MPU6050
  if (distancia < 25) {
    Serial.println("¡OBSTÁCULO DETECTADO! Esquivando...");
    anguloDeseado = 80; // Giro evasivo
  } else {
    sensors_event_t a, g, temp;
    mpu.getEvent(&a, &g, &temp);

    float accelY = a.acceleration.y - offsetAccelY;

    if (accelY > -0.6 && accelY < 0.6) {
      anguloDeseado = 90;
    } else {
      anguloDeseado = map((long)(accelY * 10), -15, 15, 80, 100);
      anguloDeseado = constrain(anguloDeseado, 80, 100);
    }
  }

  // 3. Suavizado del movimiento del timón
  anguloActual = anguloActual + (anguloDeseado - anguloActual) * 0.15;
  servo.write((int)anguloActual);

  // 4. Depuración por puerto serie
  Serial.print("Distancia: "); Serial.print(distancia);
  Serial.print("cm | Deseado: "); Serial.print(anguloDeseado);
  Serial.print(" | Suavizado (Enviado): "); Serial.println((int)anguloActual);

  delay(20);
}