import cv2
import os
from datetime import datetime


def capture_intruder():

    if not os.path.exists("captured"):
        os.mkdir("captured")

    cam = cv2.VideoCapture(0)

    ret, frame = cam.read()

    cam.release()

    if ret:

        filename = datetime.now().strftime("%Y%m%d_%H%M%S.jpg")

        filepath = os.path.join("captured", filename)

        cv2.imwrite(filepath, frame)

        print("Intruder image saved:", filepath)

        return filepath

    return None