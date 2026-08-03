from flask import Flask, render_template, send_from_directory
import os
import sqlite3
import database

app = Flask(__name__)

database.init_db()

CAPTURED_FOLDER = "captured"


def get_logs():
    conn = sqlite3.connect("logs.db")
    cursor = conn.cursor()
    cursor.execute("SELECT * FROM access_logs ORDER BY id DESC")
    rows = cursor.fetchall()
    conn.close()
    return rows


def get_captured_images():
    if not os.path.exists(CAPTURED_FOLDER):
        return []

    files = []
    for file_name in os.listdir(CAPTURED_FOLDER):
        lower = file_name.lower()
        if lower.endswith((".jpg", ".jpeg", ".png")):
            files.append(file_name)

    files.sort(reverse=True)
    return files


@app.route("/")
def dashboard():
    logs = get_logs()
    images = get_captured_images()

    admin_count = sum(1 for row in logs if str(row[2]).lower() == "admin")
    intruder_count = sum(1 for row in logs if str(row[2]).lower() == "intruder")

    return render_template(
        "dashboard.html",
        logs=logs,
        images=images,
        total_logs=len(logs),
        admin_count=admin_count,
        intruder_count=intruder_count,
        image_count=len(images)
    )


@app.route("/captured/<filename>")
def serve_captured_image(filename):
    return send_from_directory(CAPTURED_FOLDER, filename)


if __name__ == "__main__":
    app.run(debug=True)