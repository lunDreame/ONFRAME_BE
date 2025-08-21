#!/usr/bin/env python3
import os
import sys
import json
import time
import signal
import serial
import socket
from math import floor
from datetime import datetime, timezone
from typing import Optional, Dict

import paho.mqtt.client as mqtt

PORT = '/dev/serial0'
BAUD = 9600

MQTT_HOST = '127.0.0.1'
MQTT_PORT = 1883
MQTT_USER = ''
MQTT_PASS = ''
SENSOR_ID = 'aq1'
TOPIC = f'onframe/air/{SENSOR_ID}'
QOS = 1
RETAIN = True

PRINT_EVERY = 1


# Breakpoint tables: (C_low, C_high, I_low, I_high)
# PM2.5 uses 0.1 µg/m³ truncation; PM10 uses 1 µg/m³ truncation.
PM25_BREAKPOINTS = [
    (0.0,   12.0,   0,   50),
    (12.1,  35.4,  51,  100),
    (35.5,  55.4, 101,  150),
    (55.5, 150.4, 151,  200),
    (150.5, 250.4, 201, 300),
    (250.5, 350.4, 301, 400),
    (350.5, 500.4, 401, 500),
]

PM10_BREAKPOINTS = [
    (0,    54,    0,   50),
    (55,  154,   51,  100),
    (155, 254,  101,  150),
    (255, 354,  151,  200),
    (355, 424,  201,  300),
    (425, 504,  301,  400),
    (505, 604,  401,  500),
]

def _truncate_pm25(v: float) -> float:
    # Truncate to 0.1 µg/m³ (not round)
    return floor(v * 10) / 10.0

def _truncate_pm10(v: float) -> int:
    # Truncate to integer µg/m³ (not round)
    return int(floor(v))

def _calc_aqi_from_breakpoints(conc: float, bps) -> Optional[int]:
    for (clow, chigh, ilow, ihigh) in bps:
        if clow <= conc <= chigh:
            # Linear mapping: I = (Ihi-Ilow)/(Chi-Clow) * (C - Clow) + Ilow
            idx = ( (ihigh - ilow) / (chigh - clow) ) * (conc - clow) + ilow
            # EPA publishes AQI as integer; standard practice is round to nearest int
            return int(round(idx))
    # Above the last breakpoint: cap at 500
    if conc > bps[-1][1]:
        return 500
    # Below the first breakpoint (shouldn't happen)
    return None

def aqi_category(aqi: int) -> str:
    if aqi <= 50:   return "Good"
    if aqi <= 100:  return "Moderate"
    if aqi <= 150:  return "Unhealthy for Sensitive Groups"
    if aqi <= 200:  return "Unhealthy"
    if aqi <= 300:  return "Very Unhealthy"
    return "Hazardous"

def compute_aqi(pm25: float, pm10: float) -> Dict[str, Optional[int]]:
    c25 = _truncate_pm25(pm25)
    c10 = _truncate_pm10(pm10)
    aqi25 = _calc_aqi_from_breakpoints(c25, PM25_BREAKPOINTS)
    aqi10 = _calc_aqi_from_breakpoints(c10, PM10_BREAKPOINTS)
    if aqi25 is None and aqi10 is None:
        overall = None
    elif aqi25 is None:
        overall = aqi10
    elif aqi10 is None:
        overall = aqi25
    else:
        overall = max(aqi25, aqi10)
    return {
        "aqi": overall,
        "aqi_pm25": aqi25,
        "aqi_pm10": aqi10,
        "aqi_category": aqi_category(overall) if overall is not None else None
    }


def parse_line(line: str) -> Optional[Dict]:
    """
    Input Example: "24.5,36.4,652,29,30,48,57"
    Meaning:        temp, humi, co2(ppm), tvoc(ppb), pm1, pm25, pm10
    """
    parts = [p.strip() for p in line.split(',')]
    if len(parts) != 7:
        return None
    try:
        t   = float(parts[0])   # °C
        rh  = float(parts[1])   # %
        co2 = int(parts[2])     # ppm
        tvoc= int(parts[3])     # ppb
        pm1 = int(parts[4])     # μg/m³ (옵션)
        pm25= int(parts[5])
        pm10= int(parts[6])

        if not (-40.0 <= t <= 85.0 and 0.0 <= rh <= 100.0):
            return None

        aqi_info = compute_aqi(pm25, pm10)

        data = {
            "temp": t,
            "humidity": rh,
            "co2": co2,
            "tvoc": tvoc,
            "pm1": pm1,
            "pm25": pm25,
            "pm10": pm10,
            "aqi": aqi_info["aqi"],
            #"aqi_pm25": aqi_info["aqi_pm25"],
            #"aqi_pm10": aqi_info["aqi_pm10"],
            #"aqi_category": aqi_info["aqi_category"],
            "ts": datetime.now(timezone.utc).isoformat(timespec='seconds')
        }
        return data
    except ValueError:
        return None

def make_client() -> mqtt.Client:
    cid = f"onframe-pub-{SENSOR_ID}-{int(time.time())}"
    client = mqtt.Client(client_id=cid, clean_session=True)

    if MQTT_USER:
        client.username_pw_set(MQTT_USER, MQTT_PASS)

    def on_connect(c, udata, flags, rc, props=None):
        if rc == 0:
            print(f"[MQTT] Connected to {MQTT_HOST}:{MQTT_PORT}")
        else:
            print(f"[MQTT] Connect failed rc={rc}")

    def on_disconnect(c, udata, rc, props=None):
        print(f"[MQTT] Disconnected rc={rc}")

    client.on_connect = on_connect
    client.on_disconnect = on_disconnect
    client.connect_async(MQTT_HOST, MQTT_PORT, keepalive=30)
    client.loop_start()
    return client

stop = False
def _signal_handler(sig, frame):
    global stop
    stop = True
signal.signal(signal.SIGINT, _signal_handler)
signal.signal(signal.SIGTERM, _signal_handler)

def main():
    # 직렬 포트 열기
    try:
        ser = serial.Serial(PORT, baudrate=BAUD, timeout=1)
        print(f"[SERIAL] Opened {PORT} @ {BAUD}bps")
    except serial.SerialException as e:
        print(f"[SERIAL] Failed to open {PORT}: {e}", file=sys.stderr)
        sys.exit(1)

    client = make_client()
    last_print = 0
    msg_count = 0
    backoff = 1

    while not stop:
        try:
            raw = ser.readline()
            if not raw:
                continue
            line = raw.decode('utf-8', 'ignore').strip()
            if not line:
                continue

            parsed = parse_line(line)
            if not parsed:
                #print(f"[PARSE] Skip invalid line: {line}")
                continue

            payload = json.dumps(parsed, ensure_ascii=False, separators=(',', ':'))
            if not client.is_connected():
                if backoff < 8:
                    backoff *= 2
                time.sleep(backoff)
                continue
            else:
                backoff = 1

            r = client.publish(TOPIC, payload=payload, qos=QOS, retain=RETAIN)
            r.wait_for_publish(timeout=2.0)

            msg_count += 1
            if msg_count % max(1, PRINT_EVERY) == 0:
                print(f"[PUB] {TOPIC} {payload}")

        except (serial.SerialException, UnicodeDecodeError) as e:
            print(f"[SERIAL] Error: {e}", file=sys.stderr)
            time.sleep(1)
        except (socket.error, OSError) as e:
            print(f"[MQTT] Socket/OS error: {e}", file=sys.stderr)
            time.sleep(1)
        except Exception as e:
            print(f"[ERR] Unexpected: {e}", file=sys.stderr)
            time.sleep(0.5)

    try:
        client.loop_stop()
        client.disconnect()
    except Exception:
        pass
    try:
        ser.close()
    except Exception:
        pass
    print("[EXIT] Bye")

if __name__ == "__main__":
    main()