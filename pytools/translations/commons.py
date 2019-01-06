#!/usr/bin/env python2
'''
Dependencies:
$ pip install PyMySQL
$ pip install SQLAlchemy

@author Ph4r05
'''

import sys
import os
import xml.etree.ElementTree as ET
from lxml import etree
import json
from pprint import pprint
import traceback
from sqlalchemy import Column, DateTime, String, Integer, ForeignKey, func, Enum, Text, TIMESTAMP, text
from sqlalchemy.orm import relationship, backref
from sqlalchemy.ext.declarative import declarative_base

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
        translatableStr = "" if self.translatable else " translatable=\"false\""
        if not self.isPlural:
            return '<string name="{}"{}>{}</string>'.format(self.key, translatableStr, self.value)
        else:
            return '<plurals name="{}"{}>'.format(self.key, translatableStr) + (' '.join(self.plurals)) + '</plurals>'

    def getCsvKey(self):
        return self.key

class PluralEntry:
    '''
    Single plural entity, embedded in StringEntry.plurals.
    '''
    quantity = None
    value = None
    parent = None

    def __repr__(self):
        return '<item name="{}">{}</string>'.format(self.quantity, self.value)

    def getCsvKey(self):
        if self.parent == None: return None
        return 'PLURAL_%s_%s' % (self.quantity, self.parent.getCsvKey())

Base = declarative_base()
class SourceFile(Base):
    '''
    DB Entity for source file record.
    '''
    __tablename__ = 'sourceFile'
    id = Column(Integer, primary_key=True)
    projectId = Column(Integer)
    filename = Column(String(255))
    revision = Column(Integer)
    filetype = Column(Enum('android_xml', 'ios_property', 'csv'))
    srcLang = Column(String(16), default='en')
    dateCreated = Column(TIMESTAMP)
    dateModified = Column(TIMESTAMP, server_onupdate=text('CURRENT_TIMESTAMP'))
    filedata = Column(Text)

class SourcePhrases(Base):
    '''
    DB Entity for source phrases.
    '''
    __tablename__ = 'sourcePhrases'
    id = Column(Integer, primary_key=True)
    projectId = Column(Integer)
    revision = Column(Integer)
    sourceFile = Column(Integer, ForeignKey('sourceFile.id'))
    stringKey = Column(String(255))
    origKey = Column(String(255), nullable=True)
    pluralType = Column(Enum('none', 'zero', 'one', 'two', 'few', 'many', 'other'))
    srcLang = Column(String(6), default='en')
    translatable = Column(Integer, default=1)
    dateCreated = Column(TIMESTAMP)
    dateModified = Column(TIMESTAMP, server_onupdate=text('CURRENT_TIMESTAMP'))
    content = Column(Text)

class Translation(Base):
    '''
    DB Entity for translation.
    '''
    __tablename__ = 'translation'
    id = Column(Integer, primary_key=True)
    projectId = Column(Integer)
    revision = Column(Integer)
    srcPhrase = Column(Integer)
    stringKey = Column(String(255))
    origKey = Column(String(255), nullable=True)
    pluralType = Column(Enum('none', 'zero', 'one', 'two', 'few', 'many', 'other'))
    dstLang = Column(String(6), default='en')
    dateCreated = Column(TIMESTAMP)
    dateModified = Column(TIMESTAMP, server_onupdate=text('CURRENT_TIMESTAMP'))
    approveLvl = Column(Integer)
    translation = Column(Text)

    def __repr__(self):
       return '<string name="%s">%s</string>' % (self.stringKey, self.translation)

    def isPlural(self):
        return self.pluralType != 'none'


class TranslateHelper(object):

    @staticmethod
    def getQuantities():
        return ['none', 'zero', 'one', 'two', 'few', 'many', 'other']

    @staticmethod
    def getText(elem):
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

    @staticmethod
    def loadDbData():
        '''
        Loads DB configuration data (address, username, password, database) from JSON file, returns a dictionary.
        :return:
        '''
        filename = "db.json"
        if not os.path.exists(filename):
            print "[!] Error: DB config was not found"
            sys.exit(1)
        with open(filename) as data_file:
            data = json.load(data_file)
            return data

    @staticmethod
    def getConnectionString():
        dbData = TranslateHelper.loadDbData()
        return 'mysql+pymysql://%s:%s@%s/%s?charset=utf8' % (dbData['user'], dbData['passwd'], dbData['server'], dbData['db'])
        pass

    @staticmethod
    def getValuesDir(appDir, lang):
        '''
        Returns values directory with XML resources for given language.
        en = default.
        :param appDir:
        :param lang:
        :return:
        '''
        targetdir = appDir + "/res/values/"
        if lang != "en" and lang != None:
            targetdir = appDir + "/res/values-{0}/".format(lang)
        return targetdir

    @staticmethod
    def genLanguageFiles(appDir, lang):
        '''
        Generates (yields) XML files from the directory corresponding to given language.
        :param appDir:
        :param lang:
        :return:
        '''
        targetdir = TranslateHelper.getValuesDir(appDir, lang)
        if not os.path.exists(targetdir):
            print "[!] Error: resource directory was not found: %s" % targetdir
            sys.exit(1)
        chars = 0

        for root, subFolders, files in os.walk(targetdir):
            for file in files:
                f = os.path.join(root,file)
                fname, fext = os.path.splitext(f)
                if not fext or not fext.endswith(".xml"): continue
                yield f
            pass
        pass

    @staticmethod
    def processString(stringVal, curOrder, inFileOrder, file):
        txt = None
        try:
            txt = TranslateHelper.getText(stringVal)
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

    @staticmethod
    def getStringEntries(file, startOrder = 0):
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

            sEntry = TranslateHelper.processString(stringVal, curOrder, inFileOrder, file)
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
            sEntry.plurals = {}
            if 'translatable' in elem.attrib and elem.attrib['translatable']:
                sEntry.translatable = elem.attrib['translatable'] == 'true'
            for item in list(elem):
                pe = PluralEntry()
                pe.quantity = item.attrib['quantity']
                pe.value = TranslateHelper.getText(item)
                pe.parent = sEntry
                sEntry.plurals[pe.quantity] = pe

            toReturn[sEntry.key] = sEntry
            curOrder += 1
            inFileOrder += 1
        return toReturn
    pass

    @staticmethod
    def stringEntryToSourcePhrase(entry):
        ret = []
        if entry.isPlural:
            for itName in entry.plurals:
                it = entry.plurals[itName]
                sp = SourcePhrases()
                sp.origKey = entry.key
                sp.stringKey = it.getCsvKey()
                sp.content = it.value
                sp.translatable = entry.translatable
                sp.pluralType = it.quantity
                ret.append(sp)
        else:
            sp = SourcePhrases()
            sp.origKey = entry.key
            sp.stringKey = entry.key
            sp.content = entry.value
            sp.pluralType = 'none'
            sp.translatable = entry.translatable
            ret.append(sp)
        return ret

    @staticmethod
    def unescape(s):
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

    @staticmethod
    def parseCsv(filepath, separator, maxCol=2):
        '''
        Reads file at given path and parses it as CSV.
        :param filepath:
        :return:
        '''
        parsedLang = {}
        if not os.path.exists(filepath):
            print "[!] Error: CSV was not found"
            sys.exit(1)

        with open(filepath) as f:
            lines = [line.rstrip('\r').rstrip('\n') for line in f]
            for line in lines:
                try:
                    (key, value) = line.split(separator, maxCol)
                    parsedLang[key] = value
                except Exception as inst:
                    traceback.print_exc()
                pass
            pass
        return parsedLang

    @staticmethod
    def removeSubElements(elem):
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

pass


