#include <ESP8266WiFi.h>
#include "./DNSServer.h"    // Patched lib
#include <ESP8266WebServer.h>

//////////////////////
// WiFi Definitions //
//////////////////////
const char WiFiAPPSK[] = "Intervalometer 9000";
const byte DNS_PORT = 53; // Capture DNS requests on port
IPAddress apIP(10, 10, 10, 1); // Private network for server
String responseHTML =
                      "<!DOCTYPE html><html><head><title>CaptivePortal</title></head><body>"
                      "<h1>Intervalometer 9000</h1><p>Select \"Use this network as is\" to redirect all http requests this device.<br>"
                      "Then start intervals when ready.</p></body></html>";

/////////////////////
// Pin Definitions //
/////////////////////
const int CONTROL_PIN = 2; // Output GPIO2

///////////////
// Variables //
///////////////
DNSServer dnsServer; // Create the DNS object
ESP8266WebServer server(80);
int numTicksVal = 0;
float intervalTimeVal = 0; // Time between every interval
float bulbPressTimeVal = 0; // Exposure time for every shot
char isIntervalRunning = false;

void setup() {
  initHardware();
  setupWiFi();
}

void loop() {
  if (isIntervalRunning) {
    // Take a shot
    PressShutterBulb();

    // Complete all interval once numTicksVal reaches 0
    isIntervalRunning = --numTicksVal > 0;

    // Intervals ended. Restart server
    if (!isIntervalRunning) {
      // TODO TURN OFF INSTEAD?
      setupWiFi();
    }

    // Don't continue before all interval completed
    return;
  }

    dnsServer.processNextRequest();
    server.handleClient();
}

void setupWiFi()
{
  WiFi.mode(WIFI_AP);
  WiFi.softAPConfig(apIP, apIP, IPAddress(255, 255, 255, 0));
  WiFi.softAP(WiFiAPPSK, WiFiAPPSK);

  // If DNSServer is started with "*" for domain name, it will reply with
  // provided IP to all DNS request
  dnsServer.start(DNS_PORT, "*", apIP);

  server.on("/start", HandleClientRequest);
    
  // Reply all bad requests with same HTML
  server.onNotFound([]() {
    server.send(200, "text/html", responseHTML);
  });

  server.begin();
}

void initHardware()
{
  Serial.begin(115200);
  pinMode(CONTROL_PIN, OUTPUT);
}

void HandleClientRequest() {
  intervalTimeVal = server.arg("intervalTime").toFloat();
  numTicksVal = server.arg("numTicks").toInt();
  bulbPressTimeVal = server.arg("bulbPressTime").toFloat();
  Serial.println(intervalTimeVal);
  Serial.println(numTicksVal);
  Serial.println(bulbPressTimeVal);

  // Prepare the response. Start with the common header:
  String s = "HTTP/1.1 200 OK\r\n";
  s += "Content-Type: text/html\r\n\r\n";
  s += "<!DOCTYPE HTML>\r\n<html>\r\n";
  s += "OK!<br>Interval time: " + String(intervalTimeVal) + " num ticks: " + String(numTicksVal) + " bulb press time: " + String(bulbPressTimeVal);
  s += "</html>\n";

  isIntervalRunning = true;

  // Send response to client, give it some time to receive it.
  server.send(200, "text/html", s);
  delay(1000);

  // Close server to save on energy
  dnsServer.stop();
  WiFi.disconnect();
  WiFi.mode(WIFI_OFF);
}

void PressShutterBulb() {
  // START
  // Turn control pin ON (press shutter)
  digitalWrite(CONTROL_PIN, HIGH);

  // Modem sleep instead?
//  WiFi.forceSleepBegin();
  delay(bulbPressTimeVal * 1000);
//  WiFi.forceSleepEnd();

  // RELEASE
  // Turn control pin off (release shutter)
  digitalWrite(CONTROL_PIN, LOW);

  // Sleep the rest of the interval
  float shotTime = bulbPressTimeVal * 1000;
  float intervalActualTime = intervalTimeVal * 1000;

  // Go to deep sleep. No GPIO state to maintain here
  if (shotTime < intervalActualTime) {
    delay(intervalActualTime - shotTime);
  }
}
