# This is called by scrape.sh

import gspread
import sys
import os
from oauth2client.service_account import ServiceAccountCredentials
import datetime

#1 = tid
#2 = appname
#3 = timeout
#4 = runtime
#5 = pass?
#6 = args
#7 = backend
#8 = locked board

tid = sys.argv[1]

json_key = '/home/mattfel/regression/synth/key.json'
scope = [
    'https://spreadsheets.google.com/feeds',
    'https://www.googleapis.com/auth/drive'
]
credentials = ServiceAccountCredentials.from_json_keyfile_name(json_key, scope)

gc = gspread.authorize(credentials)

if (sys.argv[7] == "Zynq"):
	sh = gc.open("Zynq Regression") # Open by name
elif (sys.argv[7] == "ZCU"):
	sh = gc.open("ZCU Regression") # Open by name
elif (sys.argv[7] == "AWS"):
	sh = gc.open("AWS Regression") # Open by name

# Get column
worksheet = sh.get_worksheet(0) # Select worksheet by index
lol = worksheet.get_all_values()
if (sys.argv[2] in lol[0]):
	col=lol[0].index(sys.argv[2])+1
else:
	col=len(lol[0])+1
	numsheets = len(sh.worksheets())
	for x in range(0,numsheets):
		worksheet = sh.get_worksheet(x)
		worksheet.update_cell(1,col,sys.argv[2])		


# Page 10 - Results
worksheet = sh.worksheet("Runtime")
if (sys.argv[3] == "1"):
	worksheet.update_cell(tid,col, sys.argv[6] + "\nTimed Out!\nFAILED")
elif (sys.argv[8] == "0"):
	worksheet.update_cell(tid,col, sys.argv[6] + "\n" + sys.argv[4] + "\n" + sys.argv[5])
else:
	worksheet.update_cell(tid,col, sys.argv[6] + "\n" + sys.argv[8] + "\nUnknown?")