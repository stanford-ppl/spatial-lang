#!/usr/bin/python
"""Find synthesis utilization."""

# Import standard modules
import re

# Import project modules
import argparse
import os
import re
import subprocess

def parse_args(docstring):
    """Parse command line arguments and create help function (-h).

    Args:
        docstring (str): Docstring of the calling module.

    Returns:
        Namespace: Script arguments.
            d: Directory of the make file.
    """
    # Parse arguments
    parser = argparse.ArgumentParser(description=docstring,
                                     formatter_class=
                                     argparse.RawDescriptionHelpFormatter)

    parser.add_argument('directory',
                        help="directory to run synthesis in")
    parser.add_argument('--nomake', dest='synth', action='store_false', default=True)
    parser.add_argument('--board', dest='board_name', action='store', default='zynq')
    args = parser.parse_args()

    # Expand file path to absolute path
    args.directory = os.path.abspath(args.directory)

    return args

def main():
    # Parse command line arguments
    args = parse_args(__doc__)

    # Check that directory exists
    if not os.path.isdir(args.directory):
        print "The specified directory does not exist."
        return

    # Run synthesis
    if os.path.isdir(args.directory):
        os.chdir(args.directory)
    else:
        print 'Generated folder not found'
        return
        
    if args.synth:
        subprocess.call("make zynq | tee make.log", shell=True)

    # Scrape and return data
    if os.path.isdir('verilog-zynq'):
        os.chdir("./verilog-zynq")
    else:
        print 'Synthesis folder not found'
        return

    if os.path.isfile('par_utilization.rpt'):
        with open('par_utilization.rpt', 'r') as f:
            lines = f.readlines()
            for line in lines:
                try:
                    print(line.split('|')[1].strip() + ',' + line.split('|')[2].strip())
                except:
                    print('')
                    # Do nothing
            # for i in range(33, 45):
            #     print(lines[i].split('|')[1].strip() + ',' + lines[i].split('|')[2].strip())
            # for i in range(73, 98):
            #     print(lines[i].split('|')[1].strip() + ',' + lines[i].split('|')[2].strip())
            # for i in range(108, 113):
            #     print(lines[i].split('|')[1].strip() + ',' + lines[i].split('|')[2].strip())
            # for i in range(123, 124):
            #     print(lines[i].split('|')[1].strip() + ',' + lines[i].split('|')[2].strip())
            # for i in range(196, 217):
            #     print(lines[i].split('|')[1].strip() + ',' + lines[i].split('|')[2].strip())


    if os.path.isfile('par_ram_utilization.rpt'):
        with open('par_ram_utilization.rpt', 'r') as f:
            lines = f.readlines()
            for i in range(24, 37):
                if '|' in lines[i]:
                    print(lines[i].split('|')[1].strip() + ',' + lines[i].split('|')[2].strip())
    if os.path.isfile('par_timing_summary.rpt'):
        with open('par_timing_summary.rpt', 'r') as f:
            lines = f.readlines()
            titles = lines[126]
            data = lines[128]
            pattern = re.compile(r'\s\s+')
            titles = re.split(pattern, titles)
            data = re.split(pattern, data)
            for i, title in enumerate(titles):
                if title is not '':
                    print(title + ',' + data[i])

if __name__ == '__main__':
    main()
