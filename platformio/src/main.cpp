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
  servoOrganik.write(0); // Tutup
  
  servoNonOrganik.setPeriodHertz(50);
  servoNonOrganik.attach(pinServoNonOrganik, 500, 2400);
  servoNonOrganik.write(0); // Tutup

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
    // Hapus return; dan delay panjang di sini agar kode terus berjalan ke bawah untuk membaca sensor secara offline!
  }

  // Cek konfigurasi Firebase tiap 30 detik
  if (millis() - lastCheckConfig > 30000) {
    updateConfigFromFirebase();
    lastCheckConfig = millis();
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
  // Ini 100% mencegah sensor luar mati/hang akibat tabrakan gelombang ultrasonik!
  if (millis() - lastBacaDalam > 2000) {
    if (!statusBukaOrganik) {
      float bacaOrg = ukurJarak(trigOrganik, echoOrganik);
      // Hanya pakai jika nilai wajar (bukan timeout/noise)
      if (bacaOrg > 0.5 && bacaOrg < 500.0) jarakOrgDalam = bacaOrg;
      delay(50);
    }
    if (!statusBukaNonOrganik) {
      float bacaNonOrg = ukurJarak(trigNonOrganik, echoNonOrganik);
      // Hanya pakai jika nilai wajar (bukan timeout/noise)
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
        servoOrganik.write(90); // Buka
        statusBukaOrganik = true;
      }
      waktuBukaOrganik = millis();
    }
  } else {
    countOrg = 0;
  }

  if (statusBukaOrganik && (millis() - waktuBukaOrganik > DELAY_TUTUP)) {
    servoOrganik.write(0); // Tutup
    statusBukaOrganik = false;
  }

  // ====== LOGIKA SERVO NON-ORGANIK ======
  static int countNonOrg = 0;
  if (jarakNonOrgLuar > 3.0 && jarakNonOrgLuar <= batasJarakTangan) {
    countNonOrg++;
    if (countNonOrg >= 2) { // Harus terdeteksi 2x berturut-turut
      if (!statusBukaNonOrganik) {
        servoNonOrganik.write(90); // Buka
        statusBukaNonOrganik = true;
      }
      waktuBukaNonOrganik = millis();
    }
  } else {
    countNonOrg = 0;
  }

  if (statusBukaNonOrganik && (millis() - waktuBukaNonOrganik > DELAY_TUTUP)) {
    servoNonOrganik.write(0); // Tutup
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

  // ====== UPDATE FIREBASE (Tiap 5 Detik) ======
  if (Firebase.ready() && signupOK && (millis() - lastUpdateFirebase > 5000)) {
    lastUpdateFirebase = millis();

    time_t now;
    time(&now);
    
    FirebaseJson jsonUpdate;
    if(now > 10000) {
      // Ubah ke double lalu format ke string tanpa desimal untuk mencegah parsing error di Kotlin
      String tsStr = String((double)now * 1000.0, 0); 
      jsonUpdate.set("terakhirTerlihat", tsStr.toDouble()); // Atau biarkan string tapi di Android tetap bisa parse double string tanpa E
    }
    jsonUpdate.set("statusKoneksi", "ONLINE");
    jsonUpdate.set("ipAddress", WiFi.localIP().toString());
    jsonUpdate.set("kekuatanSinyal", WiFi.RSSI());
    jsonUpdate.set("ssid", WiFi.SSID());
    
    Firebase.RTDB.updateNode(&fbdo, "/cleanbanar/devices/" + currentDeviceId, &jsonUpdate);

    // Update Organik
    FirebaseJson jsonOrg;
    jsonOrg.set("persentaseIsi", persenOrg);
    jsonOrg.set("status", statOrg);
    if(now > 10000) {
      String tsStr = String((double)now * 1000.0, 0);
      jsonOrg.set("terakhirUpdate", tsStr.toDouble());
    }
    Firebase.RTDB.updateNode(&fbdo, "/cleanbanar/devices/" + currentDeviceId + "/bins/organik", &jsonOrg);

    // Update Non-Organik
    FirebaseJson jsonNonOrg;
    jsonNonOrg.set("persentaseIsi", persenNonOrg);
    jsonNonOrg.set("status", statNon);
    if(now > 10000) {
      String tsStr = String((double)now * 1000.0, 0);
      jsonNonOrg.set("terakhirUpdate", tsStr.toDouble());
    }
    Firebase.RTDB.updateNode(&fbdo, "/cleanbanar/devices/" + currentDeviceId + "/bins/nonOrganik", &jsonNonOrg);

    // Cek perintah restart
    if (Firebase.RTDB.getBool(&fbdo, "/cleanbanar/devices/" + currentDeviceId + "/perintah/restart")) {
      if (fbdo.boolData() == true) {
        lcdPrintLine(1, "Memulai Ulang..");
        Firebase.RTDB.setBool(&fbdo, "/cleanbanar/devices/" + currentDeviceId + "/perintah/restart", false);
        delay(1000);
        ESP.restart();
      }
    }

    Serial.println("Update Firebase OK");
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
    Serial.println("Mengambil konfigurasi dari Firebase...");
    String pathConfig = "/cleanbanar/devices/" + currentDeviceId + "/config";
    
    if(Firebase.RTDB.getFloat(&fbdo, pathConfig + "/tinggiTong")) tinggiTong = fbdo.floatData();
    if(Firebase.RTDB.getFloat(&fbdo, pathConfig + "/batasPenuh")) batasPenuh = fbdo.floatData();
    if(Firebase.RTDB.getFloat(&fbdo, pathConfig + "/batasJarakTangan")) batasJarakTangan = fbdo.floatData();
    
    // Update Pins
    int tO, eO, tL_O, eL_O, sO;
    int tN, eN, tL_N, eL_N, sN;

    String pPath = pathConfig + "/pins";
    if(Firebase.RTDB.getInt(&fbdo, pPath + "/trigOrganik")) tO = fbdo.intData(); else tO = trigOrganik;
    if(Firebase.RTDB.getInt(&fbdo, pPath + "/echoOrganik")) eO = fbdo.intData(); else eO = echoOrganik;
    if(Firebase.RTDB.getInt(&fbdo, pPath + "/trigLuarOrganik")) tL_O = fbdo.intData(); else tL_O = trigLuarOrganik;
    if(Firebase.RTDB.getInt(&fbdo, pPath + "/echoLuarOrganik")) eL_O = fbdo.intData(); else eL_O = echoLuarOrganik;
    if(Firebase.RTDB.getInt(&fbdo, pPath + "/servoOrganik")) sO = fbdo.intData(); else sO = pinServoOrganik;
    
    if(Firebase.RTDB.getInt(&fbdo, pPath + "/trigNonOrganik")) tN = fbdo.intData(); else tN = trigNonOrganik;
    if(Firebase.RTDB.getInt(&fbdo, pPath + "/echoNonOrganik")) eN = fbdo.intData(); else eN = echoNonOrganik;
    if(Firebase.RTDB.getInt(&fbdo, pPath + "/trigLuarNonOrganik")) tL_N = fbdo.intData(); else tL_N = trigLuarNonOrganik;
    if(Firebase.RTDB.getInt(&fbdo, pPath + "/echoLuarNonOrganik")) eL_N = fbdo.intData(); else eL_N = echoLuarNonOrganik;
    if(Firebase.RTDB.getInt(&fbdo, pPath + "/servoNonOrganik")) sN = fbdo.intData(); else sN = pinServoNonOrganik;

    // Terapkan PIN baru jika berbeda
    if (tO != trigOrganik || eO != echoOrganik) { trigOrganik = tO; echoOrganik = eO; pinMode(trigOrganik, OUTPUT); pinMode(echoOrganik, INPUT); }
    if (tN != trigNonOrganik || eN != echoNonOrganik) { trigNonOrganik = tN; echoNonOrganik = eN; pinMode(trigNonOrganik, OUTPUT); pinMode(echoNonOrganik, INPUT); }
    
    if (tL_O != trigLuarOrganik || eL_O != echoLuarOrganik) { trigLuarOrganik = tL_O; echoLuarOrganik = eL_O; pinMode(trigLuarOrganik, OUTPUT); pinMode(echoLuarOrganik, INPUT); }
    if (tL_N != trigLuarNonOrganik || eL_N != echoLuarNonOrganik) { trigLuarNonOrganik = tL_N; echoLuarNonOrganik = eL_N; pinMode(trigLuarNonOrganik, OUTPUT); pinMode(echoLuarNonOrganik, INPUT); }
    
    if (sO != pinServoOrganik) {
      pinServoOrganik = sO;
      servoOrganik.detach();
      servoOrganik.attach(pinServoOrganik, 500, 2400);
    }
    
    if (sN != pinServoNonOrganik) {
      pinServoNonOrganik = sN;
      servoNonOrganik.detach();
      servoNonOrganik.attach(pinServoNonOrganik, 500, 2400);
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