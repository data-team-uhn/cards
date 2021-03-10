#!/usr/bin/python

import os
import sys
import requests
from requests.auth import HTTPBasicAuth

CARDS_URL = "http://localhost:8080"
if "CARDS_URL" in os.environ:
  CARDS_URL = os.environ["CARDS_URL"].rstrip('/')

ADMIN_PASSWORD = "admin"
if "ADMIN_PASSWORD" in os.environ:
  ADMIN_PASSWORD = os.environ["ADMIN_PASSWORD"]

new_password = input("New admin password: ")
form_data = {}
form_data['newPwd'] = new_password
form_data['newPwdConfirm'] = new_password
form_data['oldPwd'] = ADMIN_PASSWORD

change_req = requests.post(CARDS_URL + "/system/userManager/user/admin.changePassword.html", data=form_data, auth=HTTPBasicAuth('admin', ADMIN_PASSWORD))
if change_req.status_code != 200:
  print("Error while setting admin password")
  sys.exit(-1)
print("Set admin password")
