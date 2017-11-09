import gspread
import sys
import os
from oauth2client.service_account import ServiceAccountCredentials
import datetime

#1 = tid
#2 = appname1
#3 = lut
#4 = reg
#5 = ram
#6 = uram
#7 = dsp
#8 = lut as logic
#9 = lut as mem
#10 = synth time
#11 = timing met?
#12 = backend

tid = sys.argv[1]

json_key = '/home/mattfel/regression/synth/key.json'
scope = [
    'https://spreadsheets.google.com/feeds',
    'https://www.googleapis.com/auth/drive'
]
credentials = ServiceAccountCredentials.from_json_keyfile_name(json_key, scope)

gc = gspread.authorize(credentials)

if (sys.argv[12] == "Zynq"):
	sh = gc.open("Zynq Regression") # Open by name
	word="Slice"
elif (sys.argv[12] == "AWS"):
	sh = gc.open("AWS Regression") # Open by name
	word="CLB"

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


# Page 0 - Timestamps
stamp = datetime.datetime.now().strftime('%Y-%m-%d %H:%M:%S')

worksheet = sh.worksheet('Timestamps') # Select worksheet by index
worksheet.update_cell(tid,col, stamp)

# Page 1 - Slice LUT
worksheet = sh.worksheet(word + ' LUTs') # Select worksheet by index
worksheet.update_cell(tid,col,sys.argv[3])

# Page 2 - Slice Reg
worksheet = sh.worksheet(word + ' Regs') # Select worksheet by index
worksheet.update_cell(tid,col,sys.argv[4])

# Page 3 - Mem
worksheet = sh.worksheet('BRAMs') # Select worksheet by index
worksheet.update_cell(tid,col,sys.argv[5])

if (sys.argv[12] == "AWS"):
	# Page 4 - URAM
	worksheet = sh.worksheet('URAMs') # Select worksheet by index
	worksheet.update_cell(tid,col,sys.argv[6])

# Page 5 - DSP
worksheet = sh.worksheet('DSPs') # Select worksheet by index
worksheet.update_cell(tid,col,sys.argv[7])

# Page 6 - LUT as Logic
worksheet = sh.worksheet('LUT as Logic') # Select worksheet by index
worksheet.update_cell(tid,col,sys.argv[8])

# Page 7 - LUT as Memory
worksheet = sh.worksheet('LUT as Memory') # Select worksheet by index
worksheet.update_cell(tid,col,sys.argv[9])

# Page 8 - Synth time
worksheet = sh.worksheet('Synth Time') # Select worksheet by index
worksheet.update_cell(tid,col,float(sys.argv[10]) / 3600.)

# Page 9 - Timing met
worksheet = sh.worksheet('Timing Met') # Select worksheet by index
worksheet.update_cell(tid,col,sys.argv[11])

# Tell last update
worksheet = sh.worksheet('STATUS')
worksheet.update_cell(22,3,stamp)
worksheet.update_cell(22,4,os.uname()[1])