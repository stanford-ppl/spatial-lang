# This is called by scrape.sh

import gspread
import sys
import os
from oauth2client.service_account import ServiceAccountCredentials
import datetime

#1 = branch
#2 = tid
#3 = appname
#3 = pass
#4 = cycles

tid = sys.argv[2]

json_key = '/home/mattfel/regression/synth/key.json'
scope = [
    'https://spreadsheets.google.com/feeds',
    'https://www.googleapis.com/auth/drive'
]
credentials = ServiceAccountCredentials.from_json_keyfile_name(json_key, scope)

gc = gspread.authorize(credentials)

sh = gc.open(sys.argv[1] + " Performance")

# Get column
worksheet = sh.worksheet('Timestamps') # Select worksheet by index
lol = worksheet.get_all_values()
if (sys.argv[3] in lol[0]):
	col=lol[0].index(sys.argv[3])+1
	print("Col is %d" % col)
else:
	col=len(lol[0])+1
	print("Col is %d" % col)
	worksheet = sh.worksheet('Timestamps')
	worksheet.update_cell(1,col,sys.argv[3])
	worksheet = sh.worksheet('Runtime')
	worksheet.update_cell(1,2*col-7,sys.argv[3])


# Page 0 - Timestamps
stamp = datetime.datetime.now().strftime('%Y-%m-%d %H:%M:%S')

worksheet = sh.worksheet('Timestamps') # Select worksheet by index
worksheet.update_cell(tid,col, stamp)

# Page 1 - Runtime
worksheet = sh.worksheet('Runtime') # Select worksheet by index
worksheet.update_cell(tid,2*col-7,sys.argv[5])
worksheet.update_cell(tid,2*col-6,sys.argv[4])
