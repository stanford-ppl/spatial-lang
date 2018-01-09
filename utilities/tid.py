import gspread
import pygsheets
import sys
from oauth2client.service_account import ServiceAccountCredentials
from datetime import datetime, timezone
import os
import time

# This script sits in /home/mattfel/regression/synth

# arg 1 = hash
# arg 2 = timestamp
# arg 3 = apphash
# arg 4 = backend



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

if (sys.argv[4] == "Zynq"):
	perf=False
	sh = gc.open_by_key("1jZxVO8VFODR8_nEGBHfcmfeIJ3vo__LCPdjt4osb3aE")
elif (sys.argv[4] == "AWS"):
	perf=False
	sh = gc.open_by_key("19G95ZMMoruIsi1iMHYJ8Th9VUSX87SGTpo6yHsSCdvU")
elif (sys.argv[4] == "ZCU"):
	perf=False
	sh = gc.open_by_key("181pQqQXV_DsoWZyRV4Ve3y9QI6I0VIbVGS3TT0zbEv8")
elif (sys.argv[4] == "fpga"):
	perf=True
	sh = gc.open_by_key("1CMeHtxCU4D2u12m5UzGyKfB3WGlZy_Ycw_hBEi59XH8")
elif (sys.argv[4] == "develop"):
	perf=True
	sh = gc.open_by_key("13GW9IDtg0EFLYEERnAVMq4cGM7EKg2NXF4VsQrUp0iw")
elif (sys.argv[4] == "retime"):
	perf=True
	sh = gc.open_by_key("1glAFF586AuSqDxemwGD208yajf9WBqQUTrwctgsW--A")
elif (sys.argv[4] == "syncMem"):
	perf=True
	sh = gc.open_by_key("1TTzOAntqxLJFqmhLfvodlepXSwE4tgte1nd93NDpNC8")
elif (sys.argv[4] == "pre-master"):
	perf=True
	sh = gc.open_by_key("18lj4_mBza_908JU0K2II8d6jPhV57KktGaI27h_R1-s")
elif (sys.argv[4] == "master"):
	perf=True
	sh = gc.open_by_key("1eAVNnz2170dgAiSywvYeeip6c4Yw6MrPTXxYkJYbHWo")
else:
	print("No spreadsheet for " + sys.argv[4])
	exit()

t=time.strftime("%Y-%m-%d %H:%M:%S", time.localtime())

worksheet = sh.worksheet_by_title("Timestamps")
lol = worksheet.get_all_values()
lolhash = [x[0] for x in lol if x[0] != '']
# id = len(lolhash) + 1
freq = os.environ['CLOCK_FREQ_MHZ']
if ("hash" in lol[1]):
	hcol=lol[1].index("hash")
if ("app hash" in lol[1]):
	acol=lol[1].index("app hash")
if ("test timestamp" in lol[1]):
	ttcol=lol[1].index("test timestamp")

# # Oldest last
# lasthash=lol[id-2][hcol]
# lastapphash=lol[id-2][acol]
# lasttime=lol[id-2][ttcol]

# Oldest first
lasthash=lol[2][hcol]
lastapphash=lol[2][acol]
lasttime=lol[2][ttcol]

if (perf):
	new_entry=True
else:
	new_entry=(lasthash != sys.argv[1] or lastapphash != sys.argv[2])
if (new_entry):
	link='=HYPERLINK("https://github.com/stanford-ppl/spatial-lang/tree/' + sys.argv[1] + '", "' + sys.argv[1] + '")'
	alink='=HYPERLINK("https://github.com/stanford-ppl/spatial-apps/tree/' + sys.argv[2] + '", "' + sys.argv[2] + '")'
	numsheets = len(sh.worksheets())
	for x in range(0,numsheets):
		# worksheet = sh.get_worksheet(x) # Select worksheet by index
		worksheet = sh.worksheet('index', x)
		if (worksheet.title != "STATUS" and worksheet.title != "Properties"):
			worksheet.insert_rows(row = 2, values = [link, alink, t, freq + ' MHz', os.uname()[1] ])
			# worksheet.update_cell(id,1, link)
			# worksheet.update_cell(id,2, alink)
			# worksheet.update_cell(id,3, t)
			# worksheet.update_cell(id,4, freq + ' MHz')
			# worksheet.update_cell(id,5, os.uname()[1])
	sys.stdout.write(str(3))
else:
	# get time difference
	FMT = '%Y-%m-%d %H:%M:%S'
	tdelta = datetime.strptime(t, FMT) - datetime.strptime(lasttime, FMT)
	# Do new test anyway if results are over 24h old
	if (tdelta.total_seconds() > 129600):
		link='=HYPERLINK("https://github.com/stanford-ppl/spatial-lang/tree/' + sys.argv[1] + '", "' + sys.argv[1] + '")'
		alink='=HYPERLINK("https://github.com/stanford-ppl/spatial-apps/tree/' + sys.argv[2] + '", "' + sys.argv[2] + '")'
		numsheets = len(sh.worksheets())
		for x in range(0,numsheets):
			# worksheet = sh.get_worksheet(x) # Select worksheet by index
			worksheet = sh.worksheet('index', x)
			if (worksheet.title != "STATUS" and worksheet.title != "Properties"):
				worksheet.insert_rows(row = 2, values = [link, alink, t, freq + ' MHz', os.uname()[1] ])
				# worksheet.update_cell(id,1, link)
				# worksheet.update_cell(id,2, alink)
				# worksheet.update_cell(id,3, t)
				# worksheet.update_cell(id,4, freq + ' MHz')
				# worksheet.update_cell(id,5, os.uname()[1])
		sys.stdout.write(str(3))
	else:
		worksheet = sh.worksheet_by_title("STATUS")
		udates = [x[0] for x in worksheet.get_all_values() if x[0] != '']
		st=len(udates) + 1
		if (st > 20):
			last = udates[-1]
			for x in range(1, st):
				worksheet.update_cell((x,1), '')
			worksheet.update_cell((1,1),last)
			st=2
		worksheet.update_cell((st,1), 'Skipped test at ' + t + ' on ' + os.uname()[1] + ' because hashes (' + sys.argv[1] + ' and ' + sys.argv[2] + ') match and only ' + str(float(tdelta.total_seconds()) / 3600.0) + ' hours elapsed since last test (' + lasttime + ') and 24 hours are required')
		worksheet.update_cell((st+1,1), '')
		sys.stdout.write("-1")


# sh.share('feldman.matthew1@gmail.com', perm_type='user', role='writer')
