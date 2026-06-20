#include <Arduino.h>
#include <WiFi.h>
#include <time.h>
#include <Firebase_ESP_Client.h>
#include <Preferences.h>
#include "BluetoothSerial.h"
#include <ESP32Servo.h>
#include <Wire.h>
#include <hd44780.h>
#include <hd44780ioClass/hd44780_I2Cexp.h>

// Bawaan library Firebase
#include "addons/TokenHelper.h"
#include "addons/RTDBHelper.h"

// Function Prototypes
void lcdPrintLine(int line, String text);
float ukurJarak(int trigPin, int echoPin);
void updateConfigFromFirebase();
void handleBluetoothProvisioning();

#define API_KEY "AIzaSyDquUtDp7VkA-PnBsYs0VJq80jCla3eBjY"
#define DATABASE_URL "https://cleanbanar-default-rtdb.asia-southeast1.firebasedatabase.app"

#define LCD_SDA 26
#define LCD_SCL 27

Preferences preferences;
BluetoothSerial SerialBT;

String currentDeviceId = "device_01"; // Default, akan ditimpa oleh konfigurasi Bluetooth


FirebaseData fbdo;
FirebaseAuth auth;
FirebaseConfig config;
bool signupOK = false;

// NTP settings untuk sinkronisasi waktu
const char* ntpServer = "pool.ntp.org";
const long gmtOffset_sec = 25200; // WIB (UTC+7)
const int daylightOffset_sec = 0;

// LCD dan Servo
hd44780_I2Cexp lcd;
Servo servoOrganik;
Servo servoNonOrganik;

// =====================================
// KONFIGURASI DINAMIS (DARI FIREBASE)
// =====================================
float tinggiTong = 50.0;
float batasPenuh = 5.0;
float batasJarakTangan = 15.0;

// Konfigurasi Derajat Servo (Dinamis)
int servoDerajatBukaOrganik = 90;
int servoDerajatTutupOrganik = 0;
int servoDerajatBukaNonOrganik = 90;
int servoDerajatTutupNonOrganik = 0;

// Pin Dalam (Ultrasonik Kepenuhan)
int trigOrganik = 5;
int echoOrganik = 18;
int trigNonOrganik = 16;
int echoNonOrganik = 17;

// Pin Luar (Ultrasonik Pendeteksi Tangan)
int trigLuarOrganik = 22;
int echoLuarOrganik = 23;
int trigLuarNonOrganik = 19;
int echoLuarNonOrganik = 21;

// Pin Servo
int pinServoOrganik = 4;
int pinServoNonOrganik = 15;

// =====================================
// VARIABEL STATUS
// =====================================
bool lastFirebaseConnect = false;
unsigned long lastUpdateFirebase = 0;
unsigned long lastCheckConfig = 0;

// Timer Servo
unsigned long waktuBukaOrganik = 0;
bool statusBukaOrganik = false;
unsigned long waktuBukaNonOrganik = 0;
bool statusBukaNonOrganik = false;
const unsigned long DELAY_TUTUP = 3000; // 3 detik terbuka

// Menyimpan SSID & Password sementara dari Bluetooth
String btSsid = "";
String btPass = "";

bool lcdReady = false;

void lcdPrintLine(int line, String text) {
  if (!lcdReady) return; // Mencegah crash jika kabel LCD terlepas
  lcd.setCursor(0, line);
  lcd.print("                "); // clear line 16 chars
  lcd.setCursor(0, line);
  lcd.print(text);
}

void setup() {
  Serial.begin(115200);
  Wire.begin(LCD_SDA, LCD_SCL);

  int status = lcd.begin(16, 2);
  if(status) {
    Serial.print("LCD Init failed: ");
    Serial.println(status);
  } else {
    lcdReady = true;
    lcdPrintLine(0, "   Smart Bin");
    lcdPrintLine(1, "   Memulai...");
  }

  preferences.begin("wifi_cfg", false);
  String savedSSID = preferences.getString("ssid", "");
  String savedPASS = preferences.getString("pass", "");
  currentDeviceId = preferences.getString("device_id", "device_01");

  lcdPrintLine(0, "ID Perangkat:");
  lcdPrintLine(1, currentDeviceId);
  Serial.println("Booting dengan ID: " + currentDeviceId);
  delay(3000);

  SerialBT.begin("CleanBanar_" + currentDeviceId);
  Serial.println("Bluetooth Aktif! Siap menerima WiFi...");
  
  // Attach Servo
  ESP32PWM::allocateTimer(0);
  ESP32PWM::allocateTimer(1);
  ESP32PWM::allocateTimer(2);
  ESP32PWM::allocateTimer(3);
  
  servoOrganik.setPeriodHertz(50);
  servoOrganik.attach(pinServoOrganik, 500, 2400);
  servoOrganik.write(servoDerajatTutupOrganik); // Tutup
  
  servoNonOrganik.setPeriodHertz(50);
  servoNonOrganik.attach(pinServoNonOrganik, 500, 2400);
  servoNonOrganik.write(servoDerajatTutupNonOrganik); // Tutup

  pinMode(trigOrganik, OUTPUT); pinMode(echoOrganik, INPUT);
  pinMode(trigNonOrganik, OUTPUT); pinMode(echoNonOrganik, INPUT);
  pinMode(trigLuarOrganik, OUTPUT); pinMode(echoLuarOrganik, INPUT);
  pinMode(trigLuarNonOrganik, OUTPUT); pinMode(echoLuarNonOrganik, INPUT);

  if (savedSSID.length() > 0) {
    lcdPrintLine(0, "Menghubungkan...");
    lcdPrintLine(1, savedSSID.substring(0, 16));
    Serial.println("Mencoba konek ke: " + savedSSID);
    
    // Paksa ESP32 melupakan WiFi lama
    WiFi.mode(WIFI_STA);
    WiFi.disconnect(false, true);
    delay(100);
    
    if (savedPASS.length() > 0) {
      WiFi.begin(savedSSID.c_str(), savedPASS.c_str());
    } else {
      WiFi.begin(savedSSID.c_str());
    }
    
    int retries = 0;
    while (WiFi.status() != WL_CONNECTED && retries < 40) { // Timeout diperpanjang jadi 20 detik (40 * 500ms)
      handleBluetoothProvisioning(); 
      
      delay(500);
      Serial.print(".");
      retries++;
    }
    Serial.println();
    
    if(WiFi.status() == WL_CONNECTED) {
      Serial.println("Terhubung! IP: " + WiFi.localIP().toString());
      lcdPrintLine(0, "WiFi Terhubung!");
      lcdPrintLine(1, WiFi.localIP().toString());
      
      configTime(gmtOffset_sec, daylightOffset_sec, ntpServer);

      config.api_key = API_KEY;
      config.database_url = DATABASE_URL;

      if (Firebase.signUp(&config, &auth, "", "")) {
        Serial.println("Firebase Auth OK");
        signupOK = true;
      } else {
        Serial.printf("%s\n", config.signer.signupError.message.c_str());
      }
      
      config.token_status_callback = tokenStatusCallback;
      Firebase.begin(&config, &auth);
      Firebase.reconnectWiFi(true);
      
      // Sinkronkan konfigurasi di awal
      updateConfigFromFirebase();
      
    } else {
      Serial.println("Gagal konek WiFi. Menunggu config dari Bluetooth...");
      lcdPrintLine(0, "Gagal Terhubung");
      lcdPrintLine(1, "Buka Bluetooth");
    }
  } else {
    Serial.println("WiFi belum diatur. Kirim config via Bluetooth!");
    lcdPrintLine(0, "WiFi Kosong");
    lcdPrintLine(1, "Buka Bluetooth");
  }
}

void loop() {
  handleBluetoothProvisioning();

  static unsigned long lastWifiRetryTime = 0;
  if (WiFi.status() != WL_CONNECTED) {
    // Coba hubungkan ulang setiap 30 detik jika WiFi terputus atau gagal di awal
    if (millis() - lastWifiRetryTime > 30000) {
      Serial.println("Mencoba menghubungkan ulang ke WiFi...");
      lcdPrintLine(0, "Menghubungkan...");
      WiFi.reconnect();
      lastWifiRetryTime = millis();
      
      unsigned long startWait = millis();
      while(WiFi.status() != WL_CONNECTED && millis() - startWait < 5000) {
         handleBluetoothProvisioning();
         delay(100);
      }
      
      if(WiFi.status() != WL_CONNECTED) {
         lcdPrintLine(0, "Gagal Terhubung");
         lcdPrintLine(1, "Buka Bluetooth");
      } else {
         lcdPrintLine(0, "WiFi Terhubung!");
         lcdPrintLine(1, WiFi.localIP().toString());
      }
    }
  }

  // ====== BACA SENSOR ======
  // Inisialisasi ke tinggiTong supaya persentase = 0% (kosong) saat pertama boot
  static float jarakOrgDalam = 50.0;
  static float jarakNonOrgDalam = 50.0;
  static unsigned long lastBacaDalam = 0;

  // Sensor Luar (Tangan) selalu dibaca agar sangat responsif
  float jarakOrgLuar = ukurJarak(trigLuarOrganik, echoLuarOrganik);
  delay(50); // Jeda aman 50ms antar sensor ultrasonik
  
  float jarakNonOrgLuar = ukurJarak(trigLuarNonOrganik, echoLuarNonOrganik);
  delay(50);

  // Sensor Dalam HANYA aktif 1x setiap 2 detik, DAN HANYA saat penutup TERTUTUP.
  if (millis() - lastBacaDalam > 2000) {
    if (!statusBukaOrganik) {
      float bacaOrg = ukurJarak(trigOrganik, echoOrganik);
      if (bacaOrg > 0.5 && bacaOrg < 500.0) jarakOrgDalam = bacaOrg;
      delay(50);
    }
    if (!statusBukaNonOrganik) {
      float bacaNonOrg = ukurJarak(trigNonOrganik, echoNonOrganik);
      if (bacaNonOrg > 0.5 && bacaNonOrg < 500.0) jarakNonOrgDalam = bacaNonOrg;
      delay(50);
    }
    lastBacaDalam = millis();
  }

  // ====== HITUNG KEPENUHAN ======
  int persenOrg = 100 - ((jarakOrgDalam - batasPenuh) / (tinggiTong - batasPenuh) * 100.0);
  int persenNonOrg = 100 - ((jarakNonOrgDalam - batasPenuh) / (tinggiTong - batasPenuh) * 100.0);

  persenOrg = constrain(persenOrg, 0, 100);
  persenNonOrg = constrain(persenNonOrg, 0, 100);

  bool penuhOrg = (persenOrg >= 90);
  bool penuhNonOrg = (persenNonOrg >= 90);

  String statOrg = penuhOrg ? "Penuh" : "Normal";
  String statNon = penuhNonOrg ? "Penuh" : "Normal";

  // ====== LOGIKA SERVO ORGANIK ======
  static int countOrg = 0;
  if (jarakOrgLuar > 3.0 && jarakOrgLuar <= batasJarakTangan) {
    countOrg++;
    if (countOrg >= 2) { // Harus terdeteksi 2x berturut-turut
      if (!statusBukaOrganik) {
        servoOrganik.write(servoDerajatBukaOrganik); // Buka
        statusBukaOrganik = true;
      }
      waktuBukaOrganik = millis();
    }
  } else {
    countOrg = 0;
  }

  if (statusBukaOrganik && (millis() - waktuBukaOrganik > DELAY_TUTUP)) {
    servoOrganik.write(servoDerajatTutupOrganik); // Tutup
    statusBukaOrganik = false;
  }

  // ====== LOGIKA SERVO NON-ORGANIK ======
  static int countNonOrg = 0;
  if (jarakNonOrgLuar > 3.0 && jarakNonOrgLuar <= batasJarakTangan) {
    countNonOrg++;
    if (countNonOrg >= 2) { // Harus terdeteksi 2x berturut-turut
      if (!statusBukaNonOrganik) {
        servoNonOrganik.write(servoDerajatBukaNonOrganik); // Buka
        statusBukaNonOrganik = true;
      }
      waktuBukaNonOrganik = millis();
    }
  } else {
    countNonOrg = 0;
  }

  if (statusBukaNonOrganik && (millis() - waktuBukaNonOrganik > DELAY_TUTUP)) {
    servoNonOrganik.write(servoDerajatTutupNonOrganik); // Tutup
    statusBukaNonOrganik = false;
  }

  // ====== UPDATE LCD ======
  String lcdLine0 = "O:";
  if (penuhOrg) lcdLine0 += "FULL";
  else if (statusBukaOrganik) lcdLine0 += "BUKA";
  else { lcdLine0 += String(persenOrg); lcdLine0 += "%"; }

  lcdLine0 += " N:";
  if (penuhNonOrg) lcdLine0 += "FULL";
  else if (statusBukaNonOrganik) lcdLine0 += "BUKA";
  else { lcdLine0 += String(persenNonOrg); lcdLine0 += "%"; }

  lcdPrintLine(0, lcdLine0);
  if (Firebase.ready()) {
    lcdPrintLine(1, "Net:OK Cloud:OK");
  } else {
    lcdPrintLine(1, "Net:OK Cloud:X ");
  }

  // ====== UPDATE & SINKRONISASI FIREBASE (Tiap 5 Detik) ======
  if (Firebase.ready() && signupOK && (millis() - lastUpdateFirebase > 5000)) {
    lastUpdateFirebase = millis();

    // 1. Ambil config & cek perintah restart
    updateConfigFromFirebase();

    // 2. Kirim update status & sensor ke Firebase (Single request PATCH)
    time_t now;
    time(&now);
    
    FirebaseJson jsonUpdate;
    if(now > 10000) {
      String tsStr = String((double)now * 1000.0, 0); 
      jsonUpdate.set("terakhirTerlihat", tsStr.toDouble());
      jsonUpdate.set("bins/organik/terakhirUpdate", tsStr.toDouble());
      jsonUpdate.set("bins/nonOrganik/terakhirUpdate", tsStr.toDouble());
    }
    jsonUpdate.set("statusKoneksi", "ONLINE");
    jsonUpdate.set("ipAddress", WiFi.localIP().toString());
    jsonUpdate.set("kekuatanSinyal", WiFi.RSSI());
    jsonUpdate.set("ssid", WiFi.SSID());
    
    jsonUpdate.set("bins/organik/persentaseIsi", persenOrg);
    jsonUpdate.set("bins/organik/status", statOrg);
    
    jsonUpdate.set("bins/nonOrganik/persentaseIsi", persenNonOrg);
    jsonUpdate.set("bins/nonOrganik/status", statNon);
    
    if (Firebase.RTDB.updateNode(&fbdo, "/cleanbanar/devices/" + currentDeviceId, &jsonUpdate)) {
      Serial.println("Update Firebase OK");
    } else {
      Serial.printf("Gagal update Firebase: %s\n", fbdo.errorReason().c_str());
    }
  }

  delay(100);
}

// ===========================================
// FUNGSI BANTUAN
// ===========================================

float ukurJarak(int trigPin, int echoPin) {
  digitalWrite(trigPin, LOW);
  delayMicroseconds(2);
  digitalWrite(trigPin, HIGH);
  delayMicroseconds(10);
  digitalWrite(trigPin, LOW);
  
  long duration = pulseIn(echoPin, HIGH, 30000); // Timeout 30ms agar tidak hang
  if (duration == 0) return 999.0;
  
  float jarak = duration * 0.034 / 2;
  return jarak;
}

void updateConfigFromFirebase() {
  if (Firebase.ready() && signupOK) {
    Serial.println("Mengambil data perangkat dari Firebase...");
    if (Firebase.RTDB.getJSON(&fbdo, "/cleanbanar/devices/" + currentDeviceId)) {
      FirebaseJson &json = fbdo.jsonObject();
      FirebaseJsonData jsonData;
      
      // 1. Cek perintah restart
      json.get(jsonData, "perintah/restart");
      if (jsonData.success && jsonData.type == "boolean" && jsonData.boolValue == true) {
        Serial.println("Menerima perintah restart dari Firebase! Mereset flag...");
        lcdPrintLine(0, "Perintah Restart");
        lcdPrintLine(1, "Memulai Ulang..");
        
        // Ubah status ke OFFLINE sebelum restart
        Firebase.RTDB.setString(&fbdo, "/cleanbanar/devices/" + currentDeviceId + "/statusKoneksi", "OFFLINE");
        
        // Reset flag restart di Firebase sebelum reboot agar tidak loop reboot
        Firebase.RTDB.setBool(&fbdo, "/cleanbanar/devices/" + currentDeviceId + "/perintah/restart", false);
        delay(1000);
        ESP.restart();
      }
      
      // 2. Baca konfigurasi dasar
      json.get(jsonData, "config/tinggiTong");
      if (jsonData.success) tinggiTong = jsonData.floatValue;
      
      json.get(jsonData, "config/batasPenuh");
      if (jsonData.success) batasPenuh = jsonData.floatValue;
      
      json.get(jsonData, "config/batasJarakTangan");
      if (jsonData.success) batasJarakTangan = jsonData.floatValue;
      
      // 3. Baca konfigurasi derajat servo
      json.get(jsonData, "config/servoDerajatBukaOrganik");
      if (jsonData.success) servoDerajatBukaOrganik = jsonData.intValue;
      
      json.get(jsonData, "config/servoDerajatTutupOrganik");
      if (jsonData.success) servoDerajatTutupOrganik = jsonData.intValue;
      
      json.get(jsonData, "config/servoDerajatBukaNonOrganik");
      if (jsonData.success) servoDerajatBukaNonOrganik = jsonData.intValue;
      
      json.get(jsonData, "config/servoDerajatTutupNonOrganik");
      if (jsonData.success) servoDerajatTutupNonOrganik = jsonData.intValue;
      
      // 4. Baca konfigurasi PIN
      int tO = trigOrganik, eO = echoOrganik;
      int tL_O = trigLuarOrganik, eL_O = echoLuarOrganik;
      int sO = pinServoOrganik;
      
      int tN = trigNonOrganik, eN = echoNonOrganik;
      int tL_N = trigLuarNonOrganik, eL_N = echoLuarNonOrganik;
      int sN = pinServoNonOrganik;
      
      json.get(jsonData, "config/pins/trigOrganik"); if (jsonData.success) tO = jsonData.intValue;
      json.get(jsonData, "config/pins/echoOrganik"); if (jsonData.success) eO = jsonData.intValue;
      json.get(jsonData, "config/pins/trigLuarOrganik"); if (jsonData.success) tL_O = jsonData.intValue;
      json.get(jsonData, "config/pins/echoLuarOrganik"); if (jsonData.success) eL_O = jsonData.intValue;
      json.get(jsonData, "config/pins/servoOrganik"); if (jsonData.success) sO = jsonData.intValue;
      
      json.get(jsonData, "config/pins/trigNonOrganik"); if (jsonData.success) tN = jsonData.intValue;
      json.get(jsonData, "config/pins/echoNonOrganik"); if (jsonData.success) eN = jsonData.intValue;
      json.get(jsonData, "config/pins/trigLuarNonOrganik"); if (jsonData.success) tL_N = jsonData.intValue;
      json.get(jsonData, "config/pins/echoLuarNonOrganik"); if (jsonData.success) eL_N = jsonData.intValue;
      json.get(jsonData, "config/pins/servoNonOrganik"); if (jsonData.success) sN = jsonData.intValue;
      
      // Terapkan PIN baru jika berbeda
      if (tO != trigOrganik || eO != echoOrganik) { 
        trigOrganik = tO; echoOrganik = eO; 
        pinMode(trigOrganik, OUTPUT); pinMode(echoOrganik, INPUT); 
      }
      if (tN != trigNonOrganik || eN != echoNonOrganik) { 
        trigNonOrganik = tN; echoNonOrganik = eN; 
        pinMode(trigNonOrganik, OUTPUT); pinMode(echoNonOrganik, INPUT); 
      }
      if (tL_O != trigLuarOrganik || eL_O != echoLuarOrganik) { 
        trigLuarOrganik = tL_O; echoLuarOrganik = eL_O; 
        pinMode(trigLuarOrganik, OUTPUT); pinMode(echoLuarOrganik, INPUT); 
      }
      if (tL_N != trigLuarNonOrganik || eL_N != echoLuarNonOrganik) { 
        trigLuarNonOrganik = tL_N; echoLuarNonOrganik = eL_N; 
        pinMode(trigLuarNonOrganik, OUTPUT); pinMode(echoLuarNonOrganik, INPUT); 
      }
      
      if (sO != pinServoOrganik) {
        pinServoOrganik = sO;
        servoOrganik.detach();
        servoOrganik.attach(pinServoOrganik, 500, 2400);
        servoOrganik.write(servoDerajatTutupOrganik);
      }
      
      if (sN != pinServoNonOrganik) {
        pinServoNonOrganik = sN;
        servoNonOrganik.detach();
        servoNonOrganik.attach(pinServoNonOrganik, 500, 2400);
        servoNonOrganik.write(servoDerajatTutupNonOrganik);
      }
      
      Serial.println("Konfigurasi berhasil disinkronkan.");
    } else {
      Serial.printf("Gagal mengambil data perangkat: %s\n", fbdo.errorReason().c_str());
    }
  }
}

void handleBluetoothProvisioning() {
  if (SerialBT.available()) {
    String incoming = SerialBT.readStringUntil('\n');
    incoming.trim();
    
    if (incoming == "SCAN_WIFI") {
      Serial.println("Memindai WiFi di sekitar...");
      int n = WiFi.scanNetworks();
      String list = "WIFI_LIST:";
      if (n == 0) {
        // Kosong
      } else {
        for (int i = 0; i < n; ++i) {
          list += WiFi.SSID(i);
          if (i < n - 1) list += ",";
        }
      }
      SerialBT.println(list);
      WiFi.scanDelete(); // Bersihkan dari memori
      Serial.println("Daftar WiFi dikirim via Bluetooth.");
      return;
    }

    if (incoming.startsWith("SET_WIFI:")) {
      lcdPrintLine(0, "Data Diterima!");
      delay(1000);
      
      incoming.replace("SET_WIFI:", "");
      int firstComma = incoming.indexOf(',');
      int lastComma = incoming.lastIndexOf(',');
      
      if (firstComma > 0 && lastComma > firstComma) {
        btSsid = incoming.substring(0, firstComma);
        btPass = incoming.substring(firstComma + 1, lastComma);
        String btDeviceId = incoming.substring(lastComma + 1);
        
        preferences.putString("device_id", btDeviceId);
        preferences.putString("ssid", btSsid);
        preferences.putString("pass", btPass);
        
        Serial.println("Menerima Config: SSID=" + btSsid + ", ID=" + btDeviceId);
        
        SerialBT.println("SUCCESS");
        lcdPrintLine(0, "Config ID:");
        lcdPrintLine(1, btDeviceId);
        
        // Ubah status ke OFFLINE di Firebase jika siap
        if (Firebase.ready() && signupOK) {
          Firebase.RTDB.setString(&fbdo, "/cleanbanar/devices/" + currentDeviceId + "/statusKoneksi", "OFFLINE");
        }
        
        delay(3000);
        lcdPrintLine(0, "Memulai Ulang..");
        delay(1000);
        ESP.restart();
      } else {
        SerialBT.println("ERROR:Format Invalid");
        lcdPrintLine(0, "Format BT Salah");
        delay(2000);
      }
    }
  }
}