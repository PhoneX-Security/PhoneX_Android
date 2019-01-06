#!/usr/bin/env python2
'''
Imports Android XML language as a translation to the database. Used mainly to import CS language.

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

    tranKeys = []

    def tranToTranslations(self, entry):
        ret = []
        if entry.isPlural:
            for itName in entry.plurals:
                it = entry.plurals[itName]
                sp = Translation()
                sp.origKey = entry.key
                sp.stringKey = it.getCsvKey()
                sp.translation = it.value
                sp.pluralType = it.quantity
                sp.srcPhrase = self.dbKeys[sp.stringKey].id if sp.stringKey in self.dbKeys else 0
                ret.append(sp)
        else:
            sp = Translation()
            sp.origKey = entry.key
            sp.stringKey = entry.key
            sp.translation = entry.value
            sp.pluralType = 'none'
            sp.srcPhrase = self.dbKeys[sp.stringKey].id if sp.stringKey in self.dbKeys else 0
            ret.append(sp)

        return ret

    def work(self):
        self.connect()

        self.dbEntries = self.loadAllSourcePhrasesForRevision()
        self.dbKeys = dict((x.stringKey, x) for x in self.dbEntries)

        translations = self.loadAllTranslationsForRevision(self.lang)
        self.tranKeys = dict((x.stringKey, x) for x in translations)

        # Read XML for given language and import it as a translation.
        stringCtr = 0
        for xml in TranslateHelper.genLanguageFiles(self.app, self.lang):
            entries = TranslateHelper.getStringEntries(xml, stringCtr)
            stringCtr += len(entries)

            if len(entries) == 0:
                continue

            # Update Db string entries.
            for entry in entries:
                phrases = self.tranToTranslations(entries[entry])
                for tr in phrases:
                    # Exists in the database?
                    if tr.stringKey in self.tranKeys:
                        oldTr = self.tranKeys[tr.stringKey]
                        oldTr.translation = tr.translation
                        self.session.add(oldTr)
                    else:
                        # Just insert record to the database.
                        tr.projectId = self.projectId
                        tr.revision = self.rev
                        tr.sourceFile = self.dbKeys[tr.stringKey].id if tr.stringKey in self.dbKeys else 0
                        tr.dstLang = self.lang
                        tr.approveLvl = 0
                        self.session.add(tr)
            self.session.commit()

# Main executable code
if __name__ == '__main__':
    parser = argparse.ArgumentParser(description='Imports Android XML language as a translation to the database. Used mainly to import CS language.', formatter_class=argparse.ArgumentDefaultsHelpFormatter)
    parser.add_argument('--app',            help='Path to the Android app. If not specifies it assumes current directory is your Android app directory', default='.', required=False)
    parser.add_argument('--rev',            help='Revision. ', default=-1, type=int, required=True)
    parser.add_argument('--lang',           help='Specific language to import from app. ', default='', required=True)
    args = parser.parse_args()

    m = Main()
    m.app = args.app
    m.rev = args.rev
    m.lang = args.lang

    m.work()
