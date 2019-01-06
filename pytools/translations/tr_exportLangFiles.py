#!/usr/bin/env python2
'''
Export translated strings from DB to the Android language files.

PyMySQL needs to be installed.
$ pip install PyMySQL
$ pip install SQLAlchemy

@author Ph4r05
'''

import commons
from commons import TranslateHelper
import pymysql
import pymysql.cursors
from tr_base import TRBase
import HTMLParser
import ntpath
import copy
import xml.etree.ElementTree as ET
from lxml import etree
import xml.dom.minidom
# from xml.dom.ext.reader import Sax2
# from xml.dom.ext import PrettyPrint
# from StringIO import StringIO

import os
import sys
import argparse
import traceback

from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker
from sqlalchemy.sql import text
from commons import Base, SourceFile, SourcePhrases, Translation

class Main(TRBase):
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

    # Export directory
    expDir = None

    # Cdata post-processing constants
    cdataStart = ';;;;;CDATA_CDATA_CDATA_START;;;;;'
    cdataEnd = ';;;;;CDATA_CDATA_CDATA_END;;;;;'

    # If true, all strings/plurals with missing translation will be removed from the current language file,
    doRemoveMissing = False

    # If true, missing entities are included in XML file, but commented out so file is valid.
    doCommentMissing = False

    # If missing translation entries are not removed and this is set to non-NONE object, name of the string is prefixed
    # with this entry so it is easy to see number of entries needed to be translated.
    notranslatedPrefix = '___TO_TRANSLATE___;'

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
                    line = '{}<![CDATA[{}]]>{}'.format(prefix, TranslateHelper.unescape(body), suffix)
                    newContent.append(line)

                with open(fname, 'wb') as r:
                    r.write("".join(newContent))

            pass
        pass
    pass

    def processCurrentLang(self):
        '''
        Processes current state of the translation for given language.
        :return:
        '''
        for xmlfile in TranslateHelper.genLanguageFiles(self.app, self.lang):
            print "cur:", xmlfile
            curKeys = TranslateHelper.getStringEntries(xmlfile, 0)

            for x in curKeys: self.newLangKeys[x] = curKeys[x]
            self.newLang[xmlfile] = [curKeys[x] for x in curKeys]
        pass

    def processDefaultLang(self):
        '''
        Processes default language as a template.
        :return:
        '''
        for xmlfile in TranslateHelper.genLanguageFiles(self.app, None):
            print "def:", xmlfile
            curKeys = TranslateHelper.getStringEntries(xmlfile, 0)

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

        destDir = self.expDir if not (self.expDir is None) else TranslateHelper.getValuesDir(self.app, self.lang)
        for fname in fnameToCopy:
            fnameSrc = TranslateHelper.getValuesDir(self.app, None) + "/" + fname
            fnameDst = destDir + "/" + fname
            print 'copy {} -> {}'.format(fnameSrc, fnameDst)

            parser = etree.XMLParser(remove_blank_text=False, remove_comments=False, remove_pis=False, strip_cdata=False, resolve_entities=False, encoding='utf-8')
            tree = etree.parse(fnameSrc, parser)
            root = tree.getroot()

            ctrNew = 0
            ctrOld = 0
            ctrLost = 0

            # Process all string values.
            for stringVal in root.findall('.//{0}'.format("string")):
                if stringVal.text is None or stringVal.text == "": continue

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

                    if not (newText is None):
                        if '<' in newText or '>' in newText:
                            # Has to modify newText since we dont have CDATA support in this dumm parser.
                            newText = self.cdataStart + newText + self.cdataEnd
                            self.postParseFiles.add(fnameDst)

                        # Parser is too picky, has to decode.
                        stringVal.text = newText.decode('utf8')
                        TranslateHelper.removeSubElements(stringVal)
                        continue
                    else:
                        print "!  Warning, key not found: ", key

                    # We have no translation for this key, remove it.
                    if self.doRemoveMissing:
                        root.remove(stringVal)
                    elif self.doCommentMissing:
                        idx = root.index(stringVal)

                        root.remove(stringVal)
                        elemText = "TODO:TRANSLATE"+ET.tostring(stringVal, encoding="UTF-8", method="html").strip()+"\n"
                        commentEntry = etree.Comment(text=elemText)

                        root.insert(idx, commentEntry)
                    elif not (self.notranslatedPrefix is None):
                        stringVal.attrib['name'] = self.notranslatedPrefix + stringVal.attrib['name']
                    ctrLost += 1
                except Exception as ex:
                    print traceback.format_exc()
                    print str(newText)
                    print "Exception for key: '%s' new '%s', exception %s " % (key, newText, ex)
                    sys.exit(1)
            pass

            # Process all plurals
            for elem in root.findall('.//plurals'):
                if elem.text is None or elem.text == "": continue
                key = elem.attrib['name']

                # quantity -> item
                quantityMap = dict((item.attrib['quantity'], item) for item in list(elem))

                # We want to check all possible quantities.
                for quantity in TranslateHelper.getQuantities():
                    if not quantity in quantityMap:
                        quantityMap[quantity] = None
                    item = quantityMap[quantity]
                    csvKey = 'PLURAL_' + quantity + '_' + key

                    newText = None
                    try:
                        # Do we have translation loaded from csv?
                        newText = None
                        if csvKey in self.parsedLang:
                            newText = self.parsedLang[csvKey]

                        elif key in self.newLangKeys:
                            entry = self.newLangKeys[key]
                            if not (entry is None) and quantity in entry.plurals:
                                newText = entry.plurals[quantity]

                        newTextObj = newText
                        if isinstance(newText, commons.PluralEntry):
                            newText = newTextObj.value

                        if not (newText is None):
                            if '<' in newText or '>' in newText:
                                # Has to modify newText since we dont have CDATA support in this dumm parser.
                                newText = self.cdataStart + newText + self.cdataEnd
                                self.postParseFiles.add(fnameDst)

                            # Parser is too picky, has to decode.
                            newText = newText.decode('utf8')

                            if item is None:
                                xmltag = "<item quantity=\"%s\">%s</item>\n" % (quantity, newText)
                                newElement = etree.fromstring(xmltag)
                                print xmltag, newElement
                                elem.append(newElement)
                            else:
                                print item
                                item.text = newText
                                TranslateHelper.removeSubElements(item)
                            continue
                        else:
                            print "!  Warning, key not found: ", key, csvKey

                        # We have no translation for this key, remove it.
                        # if self.doRemoveMissing:
                        #     elem.remove(item)
                        # elif not (self.notranslatedPrefix is None):
                        #     elem.attrib['name'] = self.notranslatedPrefix + elem.attrib['name']
                        # ctrLost += 1
                        # break

                    except Exception as ex:
                        print "Exception for key: '{}' new '{}', exception {} ".format(key, str(newText), ex)
                        print str(newText)
                        print traceback.format_exc()
                        sys.exit(1)
                pass
            pass

            # Store new file
            with open(fnameDst, 'wb') as resource:
                # prettyXml = xml.dom.minidom.parseString(etree.tostring(tree, pretty_print=False, encoding="utf8")).toprettyxml(encoding = "utf8")
                # print prettyXml
                # print etree.tostring(tree, pretty_print=True, encoding="utf8")
                # resource.write(prettyXml)

                tree.write(resource, encoding='utf-8', xml_declaration=True, pretty_print=True)
                print "New file written, new entries: {}, old entries: {}, no translation entries: {}".format(ctrNew, ctrOld, ctrLost)

    def work(self):
        self.connect()

        self.dbEntries = self.loadAllSourcePhrasesForRevision()
        self.dbKeys = dict((x.stringKey, x) for x in self.dbEntries)
        srcOrigKeys = dict((x.origKey, x) for x in self.dbEntries)
        translated = {}

        # Go through translated entries and find latest known translation.
        sqlNewestTranslations = text('SELECT tt.* FROM translation tt INNER JOIN \
                    (SELECT stringKey, MAX(revision) AS maxrev \
                    FROM translation \
                    WHERE projectId=%s AND dstLang = :lang AND revision <= :rev \
                    GROUP BY projectId, stringKey) groupedtt \
                ON tt.stringKey = groupedtt.stringKey \
                AND tt.revision = groupedtt.maxrev \
                WHERE tt.projectId=%s AND tt.dstLang = :lang AND revision <= :rev' % (self.projectId, self.projectId))

        res = self.session.query(Translation).from_statement(sqlNewestTranslations).params(lang=self.lang, rev=self.rev).all()
        useOldTranslation = set()

        # Got through translated entries, ignore those not present in current revision.
        # Use also older translations if the source matches the current source.
        idSet = set()
        for tr in res:
            # Not in current revision? Ignore then.
            if not (tr.origKey in srcOrigKeys): continue
            idSet.add(str(tr.srcPhrase))
        joinedIds = ",".join(idSet)
        sqlGetSources = text('SELECT * FROM sourcePhrases WHERE id IN (' + joinedIds + ')')
        resSrc = self.session.query(SourcePhrases).from_statement(sqlGetSources).all() if len(joinedIds) > 0 else []

        # Build search index on source entries.
        srcTranDict = dict((x.stringKey, x) for x in resSrc)

        for tr in res:
            # Not in current revision? Ignore then.
            if not (tr.origKey in srcOrigKeys):
                continue

            # Take if revision matches exactly (=new translation).
            if tr.revision == self.rev:
                translated[tr.stringKey] = tr
                continue

            # If there is no matching source phrase, check if it is plural included in this revision since there may be added
            # a new plural quantity with no source in EN version. If yes, accept (possible problem with older versions).
            if tr.isPlural() and tr.origKey in srcOrigKeys:
                translated[tr.stringKey] = tr
                continue

            if tr.stringKey not in srcTranDict:
                if not tr.isPlural() or not tr.origKey in srcOrigKeys:
                    continue
                else:
                    translated[tr.stringKey] = tr
                    continue

            # Translation source is known here.
            srcNewest = self.dbKeys[tr.stringKey]
            src = srcTranDict[tr.stringKey]
            if src is None: continue

            # Compare this source phrase with loaded phrase, if is the same, do not translate.
            if src.content == srcNewest.content:
                translated[tr.stringKey] = tr
                continue
            pass
        pass

        self.parsedLang = dict((translated[x].stringKey, translated[x].translation.encode('utf8')) for x in translated)
        self.processDefaultLang()
        self.processCurrentLang()
        self.processLang()
        self.postParse()


# Main executable code
if __name__ == '__main__':
    parser = argparse.ArgumentParser(description='Export translated strings from DB to the Android language files.', formatter_class=argparse.ArgumentDefaultsHelpFormatter)
    parser.add_argument('--app',            help='Path to the Android app. If not specifies it assumes current directory is your Android app directory', default='.', required=False)
    parser.add_argument('--expdir',         help='Export directory. If null, app XMLs will be overwritten. ', default=None, required=False)
    parser.add_argument('--rev',            help='Revision. ', default=-1, type=int, required=True)
    parser.add_argument('--ingoremissing',  help='Ignore missing translation - do not add missing entries to XML files. ', default=0, type=int, required=False)
    parser.add_argument('--commentmissing', help='Missing files are commented out in the XML files. ', default=0, type=int, required=False)
    parser.add_argument('--lang',           help='Specific language to export from DB. ', default='', required=True)
    args = parser.parse_args()

    m = Main()
    m.app = args.app
    m.rev = args.rev
    m.lang = args.lang
    m.expDir = args.expdir
    m.doRemoveMissing = args.ingoremissing > 0
    m.doCommentMissing = args.commentmissing > 0

    m.work()
