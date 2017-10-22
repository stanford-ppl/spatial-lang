import gspread
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


json_key = '/home/mattfel/regression/synth/key.json'
scope = [
    'https://spreadsheets.google.com/feeds',
    'https://www.googleapis.com/auth/drive'
]
credentials = ServiceAccountCredentials.from_json_keyfile_name(json_key, scope)

gc = gspread.authorize(credentials)

if (sys.argv[4] == "Zynq"):
	sh = gc.open("Zynq Regression") # Open by name
elif (sys.argv[4] == "AWS"):
	sh = gc.open("AWS Regression") # Open by name

t=time.strftime("%Y-%m-%d %H:%M:%S", time.localtime())

worksheet = sh.get_worksheet(0)
lol = worksheet.get_all_values()
id = len(lol) + 1
freq = os.environ['CLOCK_FREQ_MHZ']
if ("hash" in lol[1]):
	hcol=lol[1].index("hash")
if ("app hash" in lol[1]):
	acol=lol[1].index("app hash")
if ("test timestamp" in lol[1]):
	ttcol=lol[1].index("test timestamp")

lasthash=lol[-1][hcol]
lastapphash=lol[-1][acol]
lasttime=lol[-1][ttcol]

if (lasthash != sys.argv[1] or lastapphash != sys.argv[2]):
	link='=HYPERLINK("https://github.com/stanford-ppl/spatial-lang/treep/' + sys.argv[1] + '", "' + sys.argv[1] + '")'
	alink='=HYPERLINK("https://github.com/stanford-ppl/spatial-lang/treep/' + sys.argv[2] + '", "' + sys.argv[2] + '")'
	numsheets = len(sh.worksheets())
	for x in range(0,numsheets):
		worksheet = sh.get_worksheet(x) # Select worksheet by index
		worksheet.update_cell(id,1, link)
		worksheet.update_cell(id,2, alink)
		worksheet.update_cell(id,3, t)
		worksheet.update_cell(id,4, freq + ' MHz')
	sys.stdout.write(str(id))
else:
	# get time difference
	FMT = '%Y-%m-%d %H:%M:%S'
	tdelta = datetime.strptime(t, FMT) - datetime.strptime(lasttime, FMT)
	# Do new test anyway if results are over 24h old
	if (tdelta.seconds > 86400):
		link='=HYPERLINK("https://github.com/stanford-ppl/spatial-lang/treep/' + sys.argv[1] + '", "' + sys.argv[1] + '")'
		alink='=HYPERLINK("https://github.com/stanford-ppl/spatial-lang/treep/' + sys.argv[2] + '", "' + sys.argv[2] + '")'
		numsheets = len(sh.worksheets())
		for x in range(0,numsheets):
			worksheet = sh.get_worksheet(x) # Select worksheet by index
			worksheet.update_cell(id,1, link)
			worksheet.update_cell(id,2, alink)
			worksheet.update_cell(id,3, t)
			worksheet.update_cell(id,4, freq + ' MHz')
		sys.stdout.write(str(id))
	else:
		worksheet = sh.worksheet("STATUS")
		st=(len(worksheet.get_all_values()) % 20) + 1
		worksheet.update_cell(st,1, 'Skipped test at ' + t + ' because hashes (' + sys.argv[1] + ' and ' + sys.argv[2] + ') match and only ' + str(float(tdelta.seconds) / 3600.0) + ' hours elapsed since last test (' + lasttime + ') and 24 hours are required')
		worksheet.update_cell(st+1,1, '')
		sys.stdout.write("-1")


# sh.share('feldman.matthew1@gmail.com', perm_type='user', role='writer')
