# This is called by regression_run.sh

import gspread
import pygsheets
import sys
import os
from oauth2client.service_account import ServiceAccountCredentials
import datetime
import socket

#1 = branch
#2 = tid
#3 = appname
#4 = pass
#5 = cycles
#6 = hash
#7 = apphash
#8 = csv list of properties

# tid = sys.argv[2]

def write(wksh, row, col, txt):
	try:
		wksh.update_cell((row,col),txt)
	except:
		print("WARN: pygsheets failed... -_-")

def readAllVals(wksh):
	try:
		return wksh.get_all_values()
	except:
		print("WARN: pygsheets failed... -_-")
		exit()

# # gspread auth
# json_key = '/home/mattfel/regression/synth/key.json'
# scope = [
#     'https://spreadsheets.google.com/feeds',
#     'https://www.googleapis.com/auth/drive'
# ]
# credentials = ServiceAccountCredentials.from_json_keyfile_name(json_key, scope)

# pygsheets auth
json_key = '/home/mattfel/regression/synth/pygsheets_key.json'
gc = pygsheets.authorize(outh_file = json_key)

# sh = gc.open(sys.argv[1] + " Performance")
if (sys.argv[1] == "fpga"):
	try: 
		sh = gc.open_by_key("1CMeHtxCU4D2u12m5UzGyKfB3WGlZy_Ycw_hBEi59XH8")
	except:
		print("WARN: Couldn't get sheet")
		exit()
elif (sys.argv[1] == "develop"):
	try: 
		sh = gc.open_by_key("13GW9IDtg0EFLYEERnAVMq4cGM7EKg2NXF4VsQrUp0iw")
	except:
		print("WARN: Couldn't get sheet")
		exit()
elif (sys.argv[1] == "retime"):
	try: 
		sh = gc.open_by_key("1glAFF586AuSqDxemwGD208yajf9WBqQUTrwctgsW--A")
	except:
		print("WARN: Couldn't get sheet")
		exit()
elif (sys.argv[1] == "syncMem"):
	try: 
		sh = gc.open_by_key("1TTzOAntqxLJFqmhLfvodlepXSwE4tgte1nd93NDpNC8")
	except:
		print("WARN: Couldn't get sheet")
		exit()
elif (sys.argv[1] == "pre-master"):
	try: 
		sh = gc.open_by_key("18lj4_mBza_908JU0K2II8d6jPhV57KktGaI27h_R1-s")
	except:
		print("WARN: Couldn't get sheet")
		exit()
elif (sys.argv[1] == "master"):
	try: 
		sh = gc.open_by_key("1eAVNnz2170dgAiSywvYeeip6c4Yw6MrPTXxYkJYbHWo")
	except:
		print("WARN: Couldn't get sheet")
		exit()
else:
	print("No spreadsheet for " + sys.argv[4])
	exit()

# Get column
worksheet = sh.worksheet_by_title('Timestamps') # Select worksheet by index
lol = readAllVals(worksheet)
if (sys.argv[3] in lol[0]):
	col=lol[0].index(sys.argv[3])+1
	print("Col is %d" % col)
else:
	col=len(lol[0])+1
	print("Col is %d" % col)
	worksheet = sh.worksheet_by_title('Timestamps')
	write(worksheet, 1,col,sys.argv[3])
	worksheet = sh.worksheet_by_title('Properties')
	write(worksheet, 1,col,sys.argv[3])
	worksheet = sh.worksheet_by_title('Runtime')
	write(worksheet, 1,2*col-7,sys.argv[3])
# Find row, since tid is now unsafe
tid = -1
for i in range(2, len(lol)):
	if (lol[i][0] == sys.argv[6] and lol[i][1] == sys.argv[7] and lol[i][4] == socket.gethostname()):
		tid = i + 1
		break


# Page 0 - Timestamps
stamp = datetime.datetime.now().strftime('%Y-%m-%d %H:%M:%S')

worksheet = sh.worksheet_by_title('Timestamps') # Select worksheet by index
write(worksheet, tid,col, stamp)

# Page 1 - Runtime
worksheet = sh.worksheet_by_title('Runtime') # Select worksheet by index
write(worksheet, tid,2*col-7,sys.argv[5])
write(worksheet, tid,2*col-6,sys.argv[4])

# Page 2 - Properties
worksheet = sh.worksheet_by_title('Properties') # Select worksheet by index
write(worksheet, tid,col,sys.argv[4])
lol = readAllVals(worksheet)
for prop in sys.argv[8].split(","):
	# Find row
	found = False
	for i in range(2, len(lol)):
		if (lol[i][0] == prop):
			write(worksheet, i+1, col, prop)
			found = True
	if (found == False):
		write(worksheet, len(lol)+1,4, prop)
		write(worksheet, len(lol),col, prop)

# Page 3 - STATUS
worksheet = sh.worksheet_by_title('STATUS')
write(worksheet, 22,3,stamp)
write(worksheet, 22,4,os.uname()[1])