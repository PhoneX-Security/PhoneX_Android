#!/usr/bin/env python2

import os
import sys
import re
import argparse
import traceback
import xml.etree.ElementTree as ET
from lxml import etree
import glob
import fnmatch

__author__="ph4r05"
__date__ ="$Sep 16, 2014 1:06:17 PM$"

if __name__ == "__main__":
    print "Hello World";
    parser = argparse.ArgumentParser(description='Relicence.', formatter_class=argparse.ArgumentDefaultsHelpFormatter)
    parser.add_argument('--app',            help='Path to the Android app. If not specifies it assumes current directory is your Android app directory', default='.', required=False)
    parser.add_argument('-v','--verbose',   help='Writes output to the standard output', required=False, default=0, type=int)
    args = parser.parse_args()

    srcDir = args.app + "/src"
    resDir = args.app + "/res"
    if not os.path.exists(args.app):
        print "[!] Error: resource directory was not found"
        sys.exit(1)
    
    pattern = re.compile(r'^(\/\*+.+?[*]+\/)', re.MULTILINE | re.DOTALL )
    htmlPattern = re.compile(r'.*(<!--.+?GNU.+?-->)', re.MULTILINE | re.DOTALL )
    
    # Traverse file structure looking for java source files.
    for root, subFolders, files in os.walk(srcDir):
        for file in fnmatch.filter(files, '*.java'):
            f = os.path.join(root,file)
            with open(f, 'r') as fin:
                content = fin.read().lstrip()
                mobj = pattern.match(content)
                if mobj and "GNU" in mobj.group(1):
                    print "match!  %s; [%s] [%s]" % (f, 1, 1) #mobj.group(1))
                    newContent = content.replace(mobj.group(1), "").lstrip()
                    with open(f, 'w') as rw:
                        rw.write(newContent)
    
    # Traverse resource directory
    for root, subFolders, files in os.walk(resDir):
        for file in fnmatch.filter(files, '*.xml'):
            f = os.path.join(root,file)
            with open(f, 'r') as fin:
                
                content = fin.read().lstrip()
                mobj = htmlPattern.match(content)
                if mobj:
                    print "match!  %s; [%s] [%s]" % (f, 1, 1) #mobj.group(1))
                    newContent = content.replace(mobj.group(1), "").lstrip()
                    with open(f, 'w') as rw:
                        rw.write(newContent)
                    