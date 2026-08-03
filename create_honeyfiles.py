import os

FILES = [

    "salary_records.xlsx",

    "admin_passwords.txt",

    "confidential_project.docx"

]


DIR = "honeyfiles"


def create():

    if not os.path.exists(DIR):

        os.mkdir(DIR)


    for file in FILES:

        with open(os.path.join(DIR, file), "w") as f:

            f.write("Confidential data. Unauthorized access prohibited.")


if __name__ == "__main__":

    create()

    print("Honeyfiles created successfully")