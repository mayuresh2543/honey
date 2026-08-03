import os
import time
from face_auth import check_admin_face
from camera import capture_intruder
from email_alert import send_alert
import database

BASE_DIR = os.path.dirname(os.path.abspath(__file__))

REAL_FILE = os.path.join(BASE_DIR, "log", "admin_passwords.txt")
DECOY_FILE = os.path.join(BASE_DIR, "decoy_environment", "admin_passwords.txt")
LOCK_FILE = os.path.join(BASE_DIR, "trigger.lock")


# Prevent multiple simultaneous runs
if os.path.exists(LOCK_FILE):
    print("Trigger already running...")
    raise SystemExit

open(LOCK_FILE, "w").close()

try:
    print("Starting honeyfile access check...")

    is_admin = check_admin_face()

    if is_admin:
        print("Admin verified ✅")
        database.insert_log(REAL_FILE, "Admin")

        if os.path.exists(REAL_FILE):
            os.system(f'notepad "{REAL_FILE}"')
        else:
            print("Real file not found ❌")
            print(REAL_FILE)

    else:
        print("Intruder detected 🚨")
        database.insert_log(REAL_FILE, "Intruder")

        image_path = capture_intruder()
        send_alert("Intruder tried opening honeyfile!", image_path)

        if os.path.exists(DECOY_FILE):
            os.system(f'notepad "{DECOY_FILE}"')
        else:
            print("Decoy file not found ❌")
            print(DECOY_FILE)

finally:
    time.sleep(1)
    if os.path.exists(LOCK_FILE):
        os.remove(LOCK_FILE)