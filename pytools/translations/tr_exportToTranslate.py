#!/usr/bin/env python2
'''
Exports string to be translated by external translator for given langage and revision (strings with missing translations will be exported).

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

import os
import sys
import argparse
import traceback

from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker
from sqlalchemy.sql import text
from commons import Base, SourceFile, SourcePhrases, Translation

class Main(TRBase):
    csv = None
    lang = None
    separator = '|'
    parsedLang = {}

    def work(self):
        self.connect()

        self.dbEntries = self.loadAllSourcePhrasesForRevision()
        self.dbKeys = dict((x.stringKey, x) for x in self.dbEntries)

        translations = self.loadAllTranslationsForRevision()
        translationKeys = dict((x.stringKey, x) for x in translations)

        nonTranslated = {}
        nonTranslatedFinal = {}

        # Select all non-translated entries.
        for key in self.dbKeys:
            elem = self.dbKeys[key]

            if elem.translatable and key not in translationKeys:
                nonTranslated[key] = elem

        # Go through non-translated entries and find latest known translation.
        sqlNewestTranslations = text('SELECT tt.* FROM translation tt INNER JOIN \
                    (SELECT stringKey, MAX(revision) AS maxrev \
                    FROM translation \
                    WHERE projectId=%s AND dstLang = :lang \
                    GROUP BY projectId, stringKey) groupedtt \
                ON tt.stringKey = groupedtt.stringKey \
                AND tt.revision = groupedtt.maxrev \
                WHERE tt.projectId=%s AND tt.dstLang = :lang' % (self.projectId, self.projectId))

        #res = self.session.execute(sqlNewestTranslations, {'lang' : self.lang})
        res = self.session.query(Translation).from_statement(sqlNewestTranslations).params(lang=self.lang).all()
        doNotTranslate = set()

        # For each newest translation entry we check that the key is present in the current revision
        # and if yes, the match for the source language phrase is checked. If there is a match, the phrase
        # does not need to be translated since we have it from previous translation and it didn't change.
        idSet = set()
        for rec in res:
            if not (rec.stringKey in self.dbKeys): continue
            idSet.add(str(rec.srcPhrase))
        joinedIds = ",".join(idSet)
        sqlGetSources = text('SELECT * FROM sourcePhrases WHERE id IN (' + joinedIds + ')')
        resSrc = self.session.query(SourcePhrases).from_statement(sqlGetSources).all() if len(joinedIds) > 0 else []

        # Build search index on source entries.
        srcTranDict = dict((x.stringKey, x) for x in resSrc)

        # Check source phrase match.
        for rec in res:
            if not (rec.stringKey in self.dbKeys) or not (rec.stringKey in srcTranDict): continue
            srcNewest = self.dbKeys[rec.stringKey]

            src = srcTranDict[rec.stringKey]
            if src == None: continue

            # Compare this source phrase with loaded phrase, if is the same, do not translate.
            if src.content == srcNewest.content:
                doNotTranslate.add(rec.stringKey)

        # Build final key set to translate to this language.
        for x in nonTranslated:
            if x in doNotTranslate: continue
            nonTranslatedFinal[x] = nonTranslated[x]


        # Find corresponding czech translation for this so translation agency can translate it better.
        # It is just a helper so does not have to be perfect.
        csLang = self.session.query(Translation).from_statement(sqlNewestTranslations).params(lang='cs').all()
        csLangKeys = dict((x.stringKey, x) for x in csLang)

        # Generate CSV.
        csvLines = []
        for x in nonTranslatedFinal:
            entry = nonTranslatedFinal[x]
            csEquiv = csLangKeys[x].translation if x in csLangKeys else ""
            csvLines.append("%s|%s|%s" % (entry.stringKey, entry.content, csEquiv))

        csvLines = sorted(csvLines)

        print "LANG_KEY|LANG_EN|LANG_CS"
        for x in csvLines:
            print x.encode('utf-8')

# Main executable code
if __name__ == '__main__':
    parser = argparse.ArgumentParser(description='Exports string to be translated by external translator for given langage and revision (strings with missing translations will be exported).', formatter_class=argparse.ArgumentDefaultsHelpFormatter)
    parser.add_argument('--app',            help='Path to the Android app. If not specifies it assumes current directory is your Android app directory', default='.', required=False)
    parser.add_argument('--rev',            help='Revision. ', default=-1, type=int, required=True)
    parser.add_argument('--csv',            help='CSV file to produce. ', default='', required=True)
    parser.add_argument('--lang',           help='Specific language to use. ', default='', required=True)
    parser.add_argument('--separator',      help='CSV separator. ', default='|', required=False)
    args = parser.parse_args()

    m = Main()
    m.app = args.app
    m.rev = args.rev
    m.csv = args.csv
    m.lang = args.lang
    m.separator = args.separator

    m.work()
