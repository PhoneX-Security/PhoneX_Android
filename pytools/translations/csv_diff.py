#!/usr/bin/env python2
'''
Reads two CSV files and produces a diff check

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
    #CSV file separator.
    separator = ','
    separator2 = ','
    langcol = -1
    showSame = 0

    def diff(self, csv1, csv2):
        if not os.path.exists(csv1):
            print "[!] Error: CSV1 was not found"
            sys.exit(1)
        if not os.path.exists(csv2):
            print "[!] Error: CSV2 was not found"
            sys.exit(1)

        # Read all existing keys in the old file
        oldKeys = {}
        oldKeysSet = set()
        with open(csv1) as f:
            lines = [line.rstrip('\r').rstrip('\n') for line in f]
            for line in lines:
                try:
                    (key, value) = line.split(self.separator, 1)
                    oldKeys[key] = value
                    oldKeysSet.add(key)
                except Exception as inst:
                    traceback.print_exc()
                pass
            pass
        pass

        # Read new keys in the new file and find differences to the old ones.
        newKeys = {}
        newKeysSet = set()
        keysAdded = set()
        keysRemoved = set()
        keysModified = set()
        keysPreserved = set()
        with open(csv2) as f:
            lines = [line.rstrip('\r').rstrip('\n') for line in f]
            for line in lines:
                try:
                    (key, value) = line.split(self.separator2, 1)

                    newKeys[key] = value
                    newKeysSet.add(key)

                    # Check if this is a new key not present in the old csv file.
                    if not(key in oldKeysSet):
                        keysAdded.add(key)
                    elif self.langcol >= 0:
                        # Key is present, check if the translation has changed -> new translation is needed.
                        oldVal = oldKeys[key].split(self.separator, 1)[0].strip()
                        newVal = newKeys[key].split(self.separator2, 1)[0].strip()
                        if oldVal != newVal:
                            #print "Diff found [%s]!!!\nOLD: %s\nNEW: %s\n\n" % (key, oldVal, newVal)
                            keysModified.add(key)
                        else:
                            keysPreserved.add(key) # Strings for this value are not changed. Still same. Can be used.

                except Exception as inst:
                    traceback.print_exc()
                    print line
                pass
            pass

        # Scan for missing entries.
        for key in oldKeysSet:
            if not (key in newKeysSet):
                keysRemoved.add(key)
            pass
        pass

        # Show only added entries.
        if self.showSame > 0:
            for key in keysPreserved:
                print key
        else:
            keysAddMod = set()
            keysAddMod = keysAddMod.union(keysAdded)
            keysAddMod = keysAddMod.union(keysModified)
            for key in keysAddMod:
                print key
            pass
        pass
    pass
pass


# Main executable code
if __name__ == '__main__':
    parser = argparse.ArgumentParser(description='CSV diff computation.', formatter_class=argparse.ArgumentDefaultsHelpFormatter)
    parser.add_argument('--langcol',            help='Enables language check column. ', default=-1, type=int, required=False)
    parser.add_argument('--separator',          help='CSV separator. ', default=',', required=False)
    parser.add_argument('--separator2',         help='CSV separator. ', default=',', required=False)
    parser.add_argument('--showsame',          help='If set, only the same entries will be displayed. Otherwise the modified or added entries will be displayed ', default=0, type=int, required=False)

    parser.add_argument('-v','--verbose',   help='Writes output to the standard output', required=False, default=0, type=int)
    parser.add_argument('csv1', help='Old CSV file to process')
    parser.add_argument('csv2', help='New CSV file to process')
    args = parser.parse_args()

    m = Main()
    m.separator = args.separator
    m.separator2 = args.separator2
    m.langcol = args.langcol
    m.showSame = args.showsame
    m.diff(args.csv1, args.csv2)


