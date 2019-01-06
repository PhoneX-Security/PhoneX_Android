#!/usr/bin/env python2
'''
Builds CSV file from string constants for translation.

@author Ph4r05
'''

import os
import sys
import re
import argparse
import traceback
import xml.etree.ElementTree as ET
from lxml import etree

# Language strings dictionary.
langsDb = {'en': {}, 'cs': {}}

# Term order in the original sequence.
termOrdering = []

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

def processFile(filepath, lang):
    '''
    Processes given language file, extracts translateable strings and stores to the internal database.
    :param filepath:
    :param lang:
    :return:
    '''

    parser = etree.XMLParser(remove_blank_text=False, remove_comments=False, remove_pis=False, strip_cdata=False, resolve_entities=False)
    tree = etree.parse(filepath, parser)
    root = tree.getroot()
    charCnt = 0

    for stringVal in root.findall('.//string'):
        if stringVal.text == None or stringVal.text == "": continue
        #txt = stringVal.text.encode('utf-8').strip()
        #txt = stringVal.text
        txt = getText(stringVal)
        if txt == "" or txt == None: continue

        if 'translatable' in stringVal.attrib and stringVal.attrib['translatable']=='false':
            continue

        stringKey = stringVal.attrib['name']
        langsDb[lang][stringKey] = txt

        if lang == 'en':
            termOrdering.append(stringKey)

        charCnt += len(txt)

    # look for plurals.
    for elem in root.findall('.//plurals'):
        if elem.text == None or elem.text == "": continue
        key = elem.attrib['name']

        for item in list(elem):
            quantity = item.attrib['quantity']
            csvKey = 'PLURALS_' + quantity + '_' + key
            txt = getText(item)

            langsDb[lang][csvKey] = txt
            if lang == 'en':
                termOrdering.append(csvKey)

    return charCnt

def processLanguage(args, lang):
    '''
    Processes all strings for the given language, populates internal database.

    :param args:
    :param lang:
    :return:
    '''
    rootdir = args.app + "/res"
    targetdir = "/res/values/"
    if lang != "en":
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
            chars += processFile(f, lang)
        pass
    pass

# Main executable code
if __name__ == '__main__':
    parser = argparse.ArgumentParser(description='Log statements formating string converter.', formatter_class=argparse.ArgumentDefaultsHelpFormatter)
    parser.add_argument('--app',            help='Path to the Android app. If not specifies it assumes current directory is your Android app directory', default='.', required=False)
    parser.add_argument('-v','--verbose',   help='Writes output to the standard output', required=False, default=0, type=int)
    args = parser.parse_args()

    separator = '|'

    # Languages to process
    langs = ['en', 'cs']
    for lang in langs:
        processLanguage(args, lang)

    #print langsDb
    # Dump output CSV file.
    print ("KEY{}LANG_EN{}LANG_CS{}LANG_CH".format(separator, separator, separator))
    for key in termOrdering:
        if not (key in langsDb['cs']): langsDb['cs'][key] = ''
        if separator in langsDb['cs'][key] or separator in langsDb['en'][key]:
            print "WARNING! separator found in strings: ", langsDb['cs'][key], "or ", langsDb['en'][key]
            sys.exit(2)

        print ("{}{}{}{}{}{}".format(key, separator, langsDb['en'][key], separator, langsDb['cs'][key], separator))
    pass
