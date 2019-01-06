#!/usr/bin/env python2
'''
Imports translated phrases in CSV format to the database.

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

        translations = self.loadAllTranslationsForRevision(self.lang)
        translationKeys = dict((x.stringKey, x) for x in translations)

        #  Parse CSV and store new translations to the database to the current revision.
        self.parsedLang = TranslateHelper.parseCsv(self.csv, self.separator)

        # Update translate entries in the database for current revision.
        for key in self.parsedLang:
            val = self.parsedLang[key]
            origKey = key
            quantity = 'none'

            isPlural = key.startswith('PLURAL_')
            if isPlural:
                (pl, quantity, origKey) = key.split('_', 2)

            # Find in the db entity
            if key not in self.dbKeys:
                print "Unknown key: %s" % (key)
                continue

            dbEntity = self.dbKeys[key]
            if key in translationKeys:
                print "%s already in translation DB for revision" % key
                oldTr = translationKeys[key]
                oldTr.translation = val
                self.session.add(oldTr)

            else:
                tr = Translation()
                tr.approveLvl = 0
                tr.dstLang = self.lang
                tr.origKey = origKey
                tr.pluralType = quantity
                tr.translation = val
                tr.stringKey = key
                tr.projectId = self.projectId
                tr.revision = self.rev
                tr.srcPhrase = self.dbKeys[key].id if key in self.dbKeys else 0
                self.session.add(tr)
        self.session.commit()

# Main executable code
if __name__ == '__main__':
    parser = argparse.ArgumentParser(description='Imports translated phrases in CSV format to the database.', formatter_class=argparse.ArgumentDefaultsHelpFormatter)
    parser.add_argument('--app',            help='Path to the Android app. If not specifies it assumes current directory is your Android app directory', default='.', required=False)
    parser.add_argument('--rev',            help='Revision. ', default=-1, type=int, required=True)
    parser.add_argument('--csv',            help='CSV file to process. ', default='', required=True)
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
