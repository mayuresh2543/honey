import cv2
import os

if not os.path.exists("faces"):
    os.makedirs("faces")

cam = cv2.VideoCapture(0)

print("Capturing admin face... Look at camera")

ret, frame = cam.read()

cam.release()

if ret:
    frame = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
    cv2.imwrite("faces/admin.jpg", frame)
    print("Admin image saved successfully ✅")
else:
    print("Camera failed ❌")