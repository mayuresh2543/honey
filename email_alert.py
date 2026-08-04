import smtplib
from email.message import EmailMessage
import os


SENDER_EMAIL = "rjcanirudh11sci326@gmail.com"
SENDER_PASSWORD = "shgmsysblwaxqcia"
RECEIVER_EMAIL = "rjcanirudh11sci326@gmail.com"


def send_alert(message, image_path=None):
    try:
        msg = EmailMessage()
        msg["Subject"] = "🚨 Honeyfile Intrusion Alert"
        msg["From"] = SENDER_EMAIL
        msg["To"] = RECEIVER_EMAIL
        msg.set_content(message)

        if image_path and os.path.exists(image_path):
            with open(image_path, "rb") as img:
                msg.add_attachment(
                    img.read(),
                    maintype="image",
                    subtype="jpeg",
                    filename=os.path.basename(image_path)
                )

        server = smtplib.SMTP("smtp.gmail.com", 587)
        server.ehlo()
        server.starttls()
        server.ehlo()
        server.login(SENDER_EMAIL, SENDER_PASSWORD)
        server.send_message(msg)
        server.quit()
        print("Email alert sent successfully 📧")
    except Exception as e:
        print("Email alert failed ❌", e)
if __name__ == "__main__":
    print("Program started")
    