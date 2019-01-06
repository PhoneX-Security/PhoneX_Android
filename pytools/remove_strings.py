#!/usr/bin/env python2
'''
Removes specified strings from the language files.

@author Ph4r05
'''

import os
import sys
import re
import argparse
import traceback
import xml.etree.ElementTree as ET
from lxml import etree

def remove_resource_value(element2remove, filepath):
    '''
    Read an xml file and remove an element which is unused, then save the file back to the filesystem
    '''

    doSave=False
    for element in element2remove:
        print('removing "{0}" from resource "{1}"'.format(element, filepath))
        parser = etree.XMLParser(remove_blank_text=False, remove_comments=False, remove_pis=False, strip_cdata=False, resolve_entities=False)
        tree = etree.parse(filepath, parser)
        root = tree.getroot()

        for unused_value in root.findall('.//{0}[@name="{1}"]'.format(element[0], element[1])):
            print "Found!"
            root.remove(unused_value)
            doSave=True

        if not doSave: continue
        with open(filepath, 'wb') as resource:
            tree.write(resource, encoding='utf-8', xml_declaration=True)

# Main executable code
if __name__ == '__main__':
    parser = argparse.ArgumentParser(description='Log statements formating string converter.', formatter_class=argparse.ArgumentDefaultsHelpFormatter)
    parser.add_argument('--app',            help='Path to the Android app. If not specifies it assumes current directory is your Android app directory', default='.', required=False)
    parser.add_argument('--keep-main',      help='Keeps main values/ intact', default=0, required=False, type=int)
    parser.add_argument('--element',        help='XML element to remove', default='string', required=False)
    parser.add_argument('-v','--verbose',   help='Writes output to the standard output', required=False, default=0, type=int)
    parser.add_argument('str2remove',       help='Strings to remove from XML files', metavar='S', nargs='+',)
    args = parser.parse_args()
    rootdir = args.app + "/res"

    if not os.path.exists(rootdir):
        print "[!] Error: resource directory was not found"
        sys.exit(1)

    elements2remove = [[args.element, x] for x in args.str2remove]
    print elements2remove

    for root, subFolders, files in os.walk(rootdir):
        for file in files:
            f = os.path.join(root,file)
            fname, fext = os.path.splitext(f)
            if not fext or not fext.endswith(".xml"): continue
            if not "res/values" in f: continue
            if args.keep_main>0 and "res/values/" in f: continue
            remove_resource_value(elements2remove, f)
