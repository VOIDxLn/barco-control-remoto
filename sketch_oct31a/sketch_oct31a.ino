#include <Wire.h>
#include <Adafruit_MPU6050.h>
#include <Adafruit_Sensor.h>

Adafruit_MPU6050 mpu;

#define I2C_SDA 8
#define I2C_SCL 9

void setup() {
  Serial.begin(115200);
  while (!Serial) delay(10);

  Serial.println("Inicializando bus I2C Personalizado...");
  Wire.begin(I2C_SDA, I2C_SCL);


  Serial.println("Buscando chip MPU6050...");
  if(!mpu.begin(0x68, &Wire)) {
    Serial.println("no se pudo encontrar el MPU6050! Verifica las conexiones.");
    while (1) { delay(10); }
  }

  Serial.println("MPU6050 encontro con éxito.");

  mpu.setAccelerometerRange(MPU6050_RANGE_8_G);
  mpu.setGyroRange(MPU6050_RANGE_500_DEG);
  mpu.setFilterBandwidth(MPU6050_BAND_21_HZ);
  delay(100);
}

void loop() {
  sensors_event_t a, g, temp;
  mpu.getEvent(&a, &g, &temp);

  Serial.print("Giroscopio X: "); Serial.print(g.gyro.x);
  Serial.print(", Y: "); Serial.print(g.gyro.y);
  Serial.print(", Z: "); Serial.println(g.gyro.z);

  delay(500);
}
