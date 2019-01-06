#!/usr/bin/env python2
'''
Reads CVS and imports new string values or updates existing ones.
Script supports only secondary languages.

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

class StringEntry:
    '''
    Basic element that represents <string/> entity in strings file.
    Can also represent <plurals/>
    '''
    key = None
    value = None
    translatable = True
    order = 0
    inFileOrder = 0
    file = None

    isPlural = False
    plurals = {}
    def __repr__(self):
        if self.isPlural:
            return '<string name="{}" translatable="{}">{}</string>'.format(self.key, 'true' if self.translatable else 'false', self.value)
        else:
            return '<plurals name="{}">'.format(self.key) + (' '.join(self.plurals)) + '</plurals>'

class PluralEntry:
    '''
    Single plural entity, embedded in StringEntry.plurals.
    '''
    quantity = None
    value = None
    def __repr__(self):
        return '<item name="{}">{}</string>'.format(self.quantity, self.value)

class Main:
    # Database of default language entries.
    # filename -> list of entries.
    defaultLang = {}
    # string key -> string entry
    defaultLangKeys = {}

    # Database for the translated language - of old entries.
    # filename -> list of entries.
    newLang = {}
    # string key -> string entry
    newLangKeys = {}
    # files that needed to be post-parsed.
    postParseFiles = set([])

    # Parsed CSV with key:value dictionary.
    parsedLang = {}

    #CSV file separator.
    separator = ','

    # Language for CSV translation.
    lang = None

    # Path to the application directory
    app = None

    # Cdata post-processing constants
    cdataStart = ';;;;;CDATA_CDATA_CDATA_START;;;;;'
    cdataEnd = ';;;;;CDATA_CDATA_CDATA_END;;;;;'

    # If true, all strings/plurals with missing translation will be removed from the current language file,
    doRemoveMissing = False
    # If missing translation entries are not removed and this is set to non-NONE object, name of the string is prefixed
    # with this entry so it is easy to see number of entries needed to be translated.
    notranslatedPrefix = '___TO_TRANSLATE___;'

    def parseCsv(self, filepath):
        '''
        Reads file at given path and parses it as CSV.
        :param filepath:
        :return:
        '''
        if not os.path.exists(filepath):
            print "[!] Error: CSV was not found"
            sys.exit(1)

        with open(filepath) as f:
            lines = [line.rstrip('\r').rstrip('\n') for line in f]
            for line in lines:
                try:
                    (key, value) = line.split(self.separator, 2)
                    self.parsedLang[key] = value
                except Exception as inst:
                    print inst
                pass
            pass
        pass

    def getLangDir(self, language = None):
        '''
        Returns language directory
        :return:
        '''
        targetdir = "/res/values/"
        if language != None and language != "en":
            targetdir = "/res/values-{0}/".format(language)
        return targetdir

    def genXmlFiled(self, language = None):
        '''
        Generates list of XML files to process for given language.
        Acts as a generator.

        :param language: language code
        :return:
        '''
        langDir = self.getLangDir(language)
        absLangDir = self.app + langDir
        if not os.path.exists(absLangDir):
            print "[!] Error: language directory was not found {}".format(absLangDir)
            sys.exit(1)

        for root, subFolders, files in os.walk(absLangDir):
            for file in files:
                f = os.path.join(root,file)
                fname, fext = os.path.splitext(f)
                if not fext or not fext.endswith(".xml"): continue

                yield f
            pass
        pass

    def getText(self, elem):
        '''
        Extracts string representation of the element body.
        String constants may contain another tags inside, consider them as text.
        :param elem:
        :return:
        '''
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

    def removeSubElements(self, elem):
        '''
        Removes all sub elements from the given element. Used to remove old sub-tags from copied string value.
        :param elem:
        :return:
        '''
        try:
            for e in list(elem):
                if e == None: continue
                elem.remove(e)
        except Exception as e:
            raise e

    def processString(self, stringVal, curOrder, inFileOrder, file):
        txt = None
        try:
            txt = self.getText(stringVal)
        except Exception as e:
            return None

        sEntry = StringEntry()
        sEntry.key = stringVal.attrib['name']
        sEntry.value = txt
        sEntry.order = curOrder
        sEntry.inFileOrder = inFileOrder
        sEntry.file = file
        if 'translatable' in stringVal.attrib and stringVal.attrib['translatable']:
            sEntry.translatable = stringVal.attrib['translatable'] == 'true'
        return sEntry

    def getStringEntries(self, file, startOrder = 0):
        '''
        Reads XML file and extracts all string entries.
        :param file:
        :return:
        '''
        parser = etree.XMLParser(remove_blank_text=False, remove_comments=False, remove_pis=False, strip_cdata=False, resolve_entities=False, encoding='utf-8')
        tree = etree.parse(file, parser)
        root = tree.getroot()
        toReturn = {}

        curOrder = startOrder
        inFileOrder = 0

        # Classical string processing.
        for stringVal in root.findall('.//string'):
            if stringVal == None: continue

            sEntry = self.processString(stringVal, curOrder, inFileOrder, file)
            if sEntry == None: continue
            toReturn[sEntry.key] = sEntry

            curOrder += 1
            inFileOrder += 1

        # Plurals need to be processed.
        for elem in root.findall('.//plurals'):
            sEntry = StringEntry()
            sEntry.key = elem.attrib['name']
            sEntry.value = None
            sEntry.order = curOrder
            sEntry.inFileOrder = inFileOrder
            sEntry.file = file
            sEntry.isPlural = True
            if 'translatable' in elem.attrib and elem.attrib['translatable']:
                sEntry.translatable = elem.attrib['translatable'] == 'true'
            for item in list(elem):
                pe = PluralEntry()
                pe.quantity = item.attrib['quantity']
                pe.value = self.getText(item)
                sEntry.plurals[pe.quantity] = pe

            toReturn[sEntry.key] = sEntry
            curOrder += 1
            inFileOrder += 1
        pass
        return toReturn

    def processCurrentLang(self):
        '''
        Processes current state of the translation for given language.
        :return:
        '''
        for xmlfile in self.genXmlFiled(self.lang):
            print "cur:", xmlfile
            curKeys = self.getStringEntries(xmlfile)

            for x in curKeys: self.newLangKeys[x] = curKeys[x]
            self.newLang[xmlfile] = [curKeys[x] for x in curKeys]
        pass

    def processDefaultLang(self):
        '''
        Processes default language as a template.
        :return:
        '''
        for xmlfile in self.genXmlFiled(None):
            print "def:", xmlfile
            curKeys = self.getStringEntries(xmlfile)

            for x in curKeys: self.defaultLangKeys[x] = curKeys[x]
            self.defaultLang[xmlfile] = [curKeys[x] for x in curKeys]
        pass

    def processLang(self):
        '''
        Copies all default language files to the current language, replaces existing translations, removes others.
        :return:
        '''

        # Copy only files that contains translatable strings.
        fnameToCopy = set()
        for x in self.defaultLangKeys:
            se = self.defaultLangKeys[x]
            if se.translatable==False: continue
            fname = ntpath.basename(se.file)
            fnameToCopy.add(fname)
        pass

        for fname in fnameToCopy:
            fnameSrc = self.app + '/' + self.getLangDir(None) + "/" + fname
            fnameDst = self.app + '/' + self.getLangDir(self.lang) + "/" + fname
            print 'copy {} -> {}'.format(fnameSrc, fnameDst)

            parser = etree.XMLParser(remove_blank_text=False, remove_comments=False, remove_pis=False, strip_cdata=False, resolve_entities=False, encoding='utf-8')
            tree = etree.parse(fnameSrc, parser)
            root = tree.getroot()

            ctrNew = 0
            ctrOld = 0
            ctrLost = 0

            # Process all string values.
            for stringVal in root.findall('.//{0}'.format("string")):
                if stringVal.text == None or stringVal.text == "": continue

                # We do not want non-translatable entities in language files for non-default language.
                if 'translatable' in stringVal.attrib and stringVal.attrib['translatable']=='false':
                    root.remove(stringVal)
                    continue

                # Try to find a correct translation for this key, either in CSV file or in string XML file in previous version.
                key = stringVal.attrib['name']
                newText = None
                try:
                    # Do we have translation loaded from csv?
                    newText = None
                    if key in self.parsedLang:
                        newText = self.parsedLang[key]
                        ctrNew += 1

                    # Try to look in previous version of a language file
                    elif key in self.newLangKeys:
                        newText = self.newLangKeys[key].value
                        ctrOld += 1

                    if newText != None:
                        if '<' in newText or '>' in newText:
                            # Has to modify newText since we dont have CDATA support in this dumm parser.
                            newText = self.cdataStart + newText + self.cdataEnd
                            self.postParseFiles.add(fnameDst)

                        # Parser is too picky, has to decode.
                        stringVal.text = newText.decode('utf8')
                        self.removeSubElements(stringVal)
                        continue
                    else:
                        print "!  Warning, key not found: ", key

                    # We have no translation for this key, remove it.
                    if self.doRemoveMissing:
                        root.remove(stringVal)
                    elif self.notranslatedPrefix != None:
                        stringVal.attrib['name'] = self.notranslatedPrefix + stringVal.attrib['name']
                    ctrLost += 1
                except Exception as ex:
                    print "Exception for key: '{}' new '{}', exception {} ".format(key, newText, ex)
                    print str(newText)
                    print traceback.format_exc()
                    sys.exit(1)
            pass

            # Process all plurals
            for elem in root.findall('.//plurals'):
                if elem.text == None or elem.text == "": continue
                key = elem.attrib['name']

                for item in list(elem):
                    quantity = item.attrib['quantity']
                    csvKey = 'PLURALS_' + quantity + '_' + key

                    newText = None
                    try:
                        # Do we have translation loaded from csv?
                        newText = None
                        if csvKey in self.parsedLang:
                            newText = self.parsedLang[csvKey]

                        elif key in self.newLangKeys:
                            entry = self.newLangKeys[key]
                            if entry != None and quantity in entry.plurals:
                                newText = entry.plurals[quantity]

                        if newText != None:
                            if '<' in newText or '>' in newText:
                                # Has to modify newText since we dont have CDATA support in this dumm parser.
                                newText = self.cdataStart + newText + self.cdataEnd
                                self.postParseFiles.add(fnameDst)

                            # Parser is too picky, has to decode.
                            item.text = newText.decode('utf8')
                            self.removeSubElements(item)
                            continue
                        else:
                            print "!  Warning, key not found: ", key, csvKey

                        # We have no translation for this key, remove it.
                        if self.doRemoveMissing:
                            root.remove(elem)
                        elif self.notranslatedPrefix != None:
                            elem.attrib['name'] = self.notranslatedPrefix + elem.attrib['name']
                        ctrLost += 1
                        break

                    except Exception as ex:
                        print "Exception for key: '{}' new '{}', exception {} ".format(key, newText, ex)
                        print str(newText)
                        print traceback.format_exc()
                        sys.exit(1)
                pass
            pass

            # Store new file
            with open(fnameDst, 'wb') as resource:
                tree.write(resource, encoding='utf-8', xml_declaration=True)
                print "New file written, new entries: {}, old entries: {}, no translation entries: {}".format(ctrNew, ctrOld, ctrLost)

    def unescape(self, s):
        '''
        Simple HTML entity unescaping.
        :param s:
        :return:
        '''
        s = s.replace("&lt;", "<")
        s = s.replace("&gt;", ">")
        # this has to be last:
        s = s.replace("&amp;", "&")
        return s

    def postParse(self):
        '''
        Replaces CDATA correctly and recovers HTML entities inside CDATA.
        :return:
        '''
        h = HTMLParser.HTMLParser()

        for fname in self.postParseFiles:
            print "Post-parsing file", fname
            with open(fname, 'rb') as resource:
                content = resource.readlines()
                newContent = []
                for line in content:
                    if not (self.cdataStart in line):
                        newContent.append(line)
                        continue
                    if not (self.cdataEnd in line):
                        raise Exception('Cdata end not found on the same line: ' + line)

                    (prefix, tail) = line.split(self.cdataStart)
                    (body, suffix) = tail.split(self.cdataEnd)
                    line = '{}<![CDATA[{}]]>{}'.format(prefix, self.unescape(body), suffix)
                    newContent.append(line)

                with open(fname, 'wb') as r:
                    r.write("".join(newContent))

            pass
        pass
    pass
pass


# Main executable code
if __name__ == '__main__':
    parser = argparse.ArgumentParser(description='Log statements formating string converter.', formatter_class=argparse.ArgumentDefaultsHelpFormatter)
    parser.add_argument('--app',            help='Path to the Android app. If not specifies it assumes current directory is your Android app directory', default='.', required=False)
    parser.add_argument('--lang',           help='Specific language to use. ', default='', required=True)
    parser.add_argument('--csv',            help='CSV file to process. ', default='', required=True)
    parser.add_argument('-v','--verbose',   help='Writes output to the standard output', required=False, default=0, type=int)
    args = parser.parse_args()

    m = Main()
    m.lang = args.lang
    m.app  = args.app
    m.parseCsv(args.csv)
    m.processDefaultLang()
    m.processCurrentLang()
    m.processLang()
    m.postParse()

