#!/usr/bin/env python2
'''
Filters CSV keys and leaves only those lines found in the filter file.

@author Ph4r05
'''

import os
import sys
import argparse
import traceback
import xml.etree.ElementTree as ET
from lxml import etree
import ntpath
import traceback
import HTMLParser
import commons

class Main:
    separator = ','

    def filter(self, csv1, ffile):
        if not os.path.exists(csv1):
            print "[!] Error: CSV1 was not found"
            sys.exit(1)
        if not os.path.exists(ffile):
            print "[!] Error: ffile was not found"
            sys.exit(1)

        # Read all key entries to preserve
        keepEntries = set()
        with open(ffile) as f:
            lines = [line.rstrip('\r').rstrip('\n') for line in f]
            for line in lines:
                try:
                    keepEntries.add(line)
                except Exception as inst:
                    traceback.print_exc()
                pass
            pass
        pass

        # Read all existing keys in the old file
        with open(csv1) as f:
            lines = [line.rstrip('\r').rstrip('\n') for line in f]
            for line in lines:
                try:
                    (key, value) = line.split(self.separator, 1)
                    if key in keepEntries:
                        print line
                except Exception as inst:
                    traceback.print_exc()
                pass
            pass
        pass
    pass
pass


# Main executable code
if __name__ == '__main__':
    parser = argparse.ArgumentParser(description='CSV filter.', formatter_class=argparse.ArgumentDefaultsHelpFormatter)
    parser.add_argument('--separator',          help='CSV separator. ', default=',', required=False)
    parser.add_argument('-v','--verbose',   help='Writes output to the standard output', required=False, default=0, type=int)
    parser.add_argument('csv1', help='CSV file to filter')
    parser.add_argument('ffile', help='File with keys to preserve')
    args = parser.parse_args()

    m = Main()
    m.separator = args.separator
    m.filter(args.csv1, args.ffile)


