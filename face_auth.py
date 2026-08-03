import face_recognition
import cv2
import os


def check_admin_face():

    print("Checking admin authentication...")

    BASE_DIR = os.path.dirname(os.path.abspath(__file__))
    admin_path = os.path.join(BASE_DIR, "faces", "admin.jpg")

    if not os.path.exists(admin_path):
        print("admin.jpg missing ❌")
        return False

    admin_image = cv2.imread(admin_path)

    if admin_image is None:
        print("Failed to load admin.jpg ❌")
        return False

    admin_image = cv2.cvtColor(admin_image, cv2.COLOR_BGR2RGB)

    admin_encodings = face_recognition.face_encodings(admin_image)

    if len(admin_encodings) == 0:
        print("No face found inside admin.jpg ❌")
        return False

    admin_encoding = admin_encodings[0]

    cam = cv2.VideoCapture(0, cv2.CAP_DSHOW)

    ret, frame = cam.read()

    cam.release()

    if not ret:
        print("Camera failed ❌")
        return False

    rgb_frame = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)

    face_locations = face_recognition.face_locations(rgb_frame)

    if len(face_locations) == 0:
        print("No face detected ❌")
        return False

    encodings = face_recognition.face_encodings(rgb_frame, face_locations)

    for encoding in encodings:
        match = face_recognition.compare_faces(
            [admin_encoding],
            encoding,
            tolerance=0.5
        )

        if match[0]:
            print("Admin verified ✅")
            return True

    return False


if __name__ == "__main__":
    result = check_admin_face()
    print("Result:", result)