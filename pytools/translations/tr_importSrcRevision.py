#!/usr/bin/env python2
'''
Creates a new (or updates an existing) source phrase revision. Uploads current source language to the database.

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
    lang = None

    def work(self):
        self.connect()

        self.dbEntries = self.loadAllSourcePhrasesForRevision(self.lang)
        self.dbKeys = dict((x.stringKey, x) for x in self.dbEntries)

        stringCtr = 0
        for xml in TranslateHelper.genLanguageFiles(self.app, self.lang):
            entries = TranslateHelper.getStringEntries(xml, stringCtr)
            stringCtr += len(entries)

            if len(entries) == 0:
                continue

            baseName = os.path.basename(xml)
            fileData = None
            with open (xml, "r") as myfile:
                fileData = myfile.read().replace('\n', '')

            sfile = self.updateSourceFile(baseName, fileData, self.lang)

            # Update Db string entries.
            for entry in entries:
                self.updateSourcePhrase(entries[entry], sfile, self.lang)
            self.session.commit()

# Main executable code
if __name__ == '__main__':
    parser = argparse.ArgumentParser(description='Creates a new (or updates an existing) source phrase revision. Uploads current source language to the database.', formatter_class=argparse.ArgumentDefaultsHelpFormatter)
    parser.add_argument('--app',            help='Path to the Android app. If not specifies it assumes current directory is your Android app directory', default='.', required=False)
    parser.add_argument('--rev',            help='Revision. ', default=-1, type=int, required=True)
    parser.add_argument('--lang',           help='Specific language to import. ', default='en', required=False)
    args = parser.parse_args()

    m = Main()
    m.app = args.app
    m.rev = args.rev
    m.lang = args.lang

    m.work()
