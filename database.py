import sqlite3
from datetime import datetime


def init_db():

    conn = sqlite3.connect("logs.db")
    cursor = conn.cursor()

    cursor.execute("""
    CREATE TABLE IF NOT EXISTS access_logs (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        file TEXT,
        user TEXT,
        time TEXT
    )
    """)

    conn.commit()
    conn.close()


def insert_log(file, user):

    conn = sqlite3.connect("logs.db")
    cursor = conn.cursor()

    timestamp = datetime.now().strftime("%Y-%m-%d %H:%M:%S")

    cursor.execute(
        "INSERT INTO access_logs (file, user, time) VALUES (?, ?, ?)",
        (file, user, timestamp)
    )

    conn.commit()
    conn.close()


def fetch_logs():
    conn = sqlite3.connect("logs.db")
    cursor = conn.cursor()

    cursor.execute("SELECT * FROM access_logs ORDER BY id DESC")
    rows = cursor.fetchall()

    conn.close()
    return rows