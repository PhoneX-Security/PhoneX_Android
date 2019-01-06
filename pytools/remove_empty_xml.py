#!/usr/bin/env python2
'''
Removes empty XML files with empty root element

@author Ph4r05
'''

import os
import sys
import re
import argparse
import traceback
import xml.etree.ElementTree as ET
from lxml import etree

# Main executable code
if __name__ == '__main__':
    parser = argparse.ArgumentParser(description='Log statements formating string converter.', formatter_class=argparse.ArgumentDefaultsHelpFormatter)
    parser.add_argument('--app',            help='Path to the Android app. If not specifies it assumes current directory is your Android app directory', default='.', required=False)
    parser.add_argument('--keep-main',      help='Keeps main values/ intact', default=0, required=False, type=int)
    parser.add_argument('--dry-run',        help='Does not actually remove anything', default=0, required=False, type=int)
    parser.add_argument('-v','--verbose',   help='Writes output to the standard output', required=False, default=0, type=int)
    args = parser.parse_args()
    rootdir = args.app + "/res"

    if not os.path.exists(rootdir):
        print "[!] Error: resource directory was not found"
        sys.exit(1)

    for root, subFolders, files in os.walk(rootdir):
        for file in files:
            f = os.path.join(root,file)
            fname, fext = os.path.splitext(f)
            if not fext or not fext.endswith(".xml"): continue
            if not "res/values" in f: continue
            if args.keep_main>0 and "res/values/" in f: continue

            # Determine if there is only empty root element, without children and attributes.
            #print('testing resource "{0}"'.format(f))
            parser = etree.XMLParser(remove_blank_text=False, remove_comments=False, remove_pis=False, strip_cdata=False, resolve_entities=False)
            tree = etree.parse(f, parser)
            treeroot = tree.getroot()
            rootKeys = treeroot.keys()

            children = treeroot.getchildren()
            childrenNonComment = filter(lambda y: not isinstance(y, type(etree.Comment(""))), children)

            if len(rootKeys)==0 and len(childrenNonComment)==0:
             print("Removing %s" % f)
             if args.dry_run==0:
                os.remove(f)


