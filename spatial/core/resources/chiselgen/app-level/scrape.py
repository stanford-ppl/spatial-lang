import gspread
import sys
from oauth2client.service_account import ServiceAccountCredentials
import datetime

#1 = tid
#2 = appname1
#3 = lut
#4 = reg
#5 = ram
#6 = dsp

tid = sys.argv[1]

json_key = '/home/mattfel/regression/zynq/key.json'
scope = [
    'https://spreadsheets.google.com/feeds',
    'https://www.googleapis.com/auth/drive'
]
credentials = ServiceAccountCredentials.from_json_keyfile_name(json_key, scope)

gc = gspread.authorize(credentials)

sh = gc.open("Zynq Regression") # Open by name

# Get column
worksheet = sh.get_worksheet(0) # Select worksheet by index
lol = worksheet.get_all_values()
if (sys.argv[2] in lol[0]):
	col=lol[0].index(sys.argv[2])+1
else:
	col=len(lol[0])+1
	for x in range(0,5):
		worksheet = sh.get_worksheet(x)
		worksheet.update_cell(1,col,sys.argv[2])		


# Page 0 - Timestamps
stamp = datetime.datetime.now().strftime('%Y-%m-%d %H:%M:%S')

worksheet = sh.get_worksheet(0) # Select worksheet by index
worksheet.update_cell(tid,col, stamp)

# Page 1 - Slice LUT
worksheet = sh.get_worksheet(1) # Select worksheet by index
worksheet.update_cell(tid,col,sys.argv[3])

# Page 2 - Slice Reg
worksheet = sh.get_worksheet(2) # Select worksheet by index
worksheet.update_cell(tid,col,sys.argv[4])

# Page 3 - Mem
worksheet = sh.get_worksheet(3) # Select worksheet by index
worksheet.update_cell(tid,col,sys.argv[5])

# Page 4 - DSP
worksheet = sh.get_worksheet(4) # Select worksheet by index
worksheet.update_cell(tid,col,sys.argv[6])

