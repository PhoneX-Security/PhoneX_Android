#!/usr/bin/env python2
'''
Counts number of characters in default string file.

@author Ph4r05
'''

import os
import sys
import re
import argparse
import traceback
import xml.etree.ElementTree as ET
from lxml import etree

def getText(elem):
    if elem == None: return None
    txt = None

    try:
        txt = elem.text.encode('utf-8')
        for e in list(elem):
            if e == None: continue

            newTxt = etree.tostring(e, encoding='utf-8')
            if newTxt == None: continue

            txt += newTxt
    except Exception as e:
        raise e

    return txt

def count_characters(filepath):
    parser = etree.XMLParser(remove_blank_text=False, remove_comments=False, remove_pis=False, strip_cdata=False, resolve_entities=False)
    tree = etree.parse(filepath, parser)
    root = tree.getroot()
    charCnt = 0;
    
    #print ""
    #print "Analyzing file:", filepath
    for  stringVal in root.findall('.//{0}'.format("string")):
    	if stringVal.text == None or stringVal.text == "": continue
    	txt = getText(stringVal)
    	if txt == "" or txt == None: continue
    	
    	if 'translatable' in stringVal.attrib and stringVal.attrib['translatable']=='false':
			continue
    	
    	print "#", stringVal.attrib['name'],'=',txt
    	charCnt += len(txt)
    return charCnt

# Main executable code
if __name__ == '__main__':
    parser = argparse.ArgumentParser(description='Log statements formating string converter.', formatter_class=argparse.ArgumentDefaultsHelpFormatter)
    parser.add_argument('--app',            help='Path to the Android app. If not specifies it assumes current directory is your Android app directory', default='.', required=False)
    parser.add_argument('--lang',           help='Specific language to use. If not specifies default language is used', default='', required=False)
    parser.add_argument('-v','--verbose',   help='Writes output to the standard output', required=False, default=0, type=int)
    args = parser.parse_args()
    lang = args.lang
    rootdir = args.app + "/res"
    targetdir = "/res/values/"
    if lang != "": 
    	targetdir = "/res/values-{0}/".format(lang)

    if not os.path.exists(rootdir):
        print "[!] Error: resource directory was not found"
        sys.exit(1)
        
    chars = 0

    for root, subFolders, files in os.walk(rootdir):
        for file in files:
            f = os.path.join(root,file)
            fname, fext = os.path.splitext(f)
            if not fext or not fext.endswith(".xml"): continue
            if not targetdir in f: continue
            chars += count_characters(f)

    print "Number of characters: ", chars
