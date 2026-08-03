import sqlite3

conn = sqlite3.connect("logs.db")

cursor = conn.cursor()

cursor.execute("DELETE FROM access_logs")

cursor.execute(
    "DELETE FROM sqlite_sequence WHERE name='access_logs'"
)

conn.commit()

conn.close()

print("Database reset successfully ✅")