#!/usr/bin/env python2
'''
Imports source revision to the database.
Supports only android source files.

PyMySQL needs to be installed.
$ pip install PyMySQL
$ pip install SQLAlchemy

@author Ph4r05
'''

import commons
from commons import TranslateHelper
import pymysql
import pymysql.cursors

import os
import sys
import argparse
import traceback

from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker
from commons import Base, SourceFile, SourcePhrases, Translation

class TRBase:
    connection = None
    engine = None
    session = None

    app = None
    rev = None
    projectId = 1

    dbEntries = None
    dbKeys = None

    def connect(self):
        # load DB data from JSON configuration file.
        dbData = TranslateHelper.loadDbData()

        # Connect to the database
        self.connection = pymysql.connect(host=dbData['server'],
                                     user=dbData['user'],
                                     passwd=dbData['passwd'],
                                     db=dbData['db'],
                                     charset='utf8mb4',
                                     cursorclass=pymysql.cursors.DictCursor)

        self.engine = create_engine(TranslateHelper.getConnectionString())
        # Bind the engine to the metadata of the Base class so that the
        # declaratives can be accessed through a DBSession instance
        Base.metadata.bind = self.engine
        DBSession = sessionmaker(bind=self.engine)
        self.session = DBSession()
    pass

    def loadAllSourcePhrasesForRevision(self, srcLang='en'):
        return self.session.query(SourcePhrases).filter(SourcePhrases.revision == self.rev,
                                                        SourcePhrases.projectId == self.projectId,
                                                        SourcePhrases.srcLang == srcLang).all()

    def loadAllTranslationsForRevision(self, lang='en'):
        return self.session.query(Translation).filter(Translation.revision == self.rev,
                                                      Translation.projectId == self.projectId,
                                                      Translation.dstLang == lang).all()

    def deleteSourceFile(self, filename):
        res = self.session.query(SourceFile).filter(SourceFile.revision == self.rev,
                                                        SourceFile.projectId == self.projectId,
                                                        SourceFile.filename == filename).delete()
        self.session.commit()
        return res

    def insertSourceFile(self, filename, content, lang='en'):
        sf = SourceFile()
        sf.filename = filename
        sf.filedata = content
        sf.filetype = 'android_xml'
        sf.projectId = self.projectId
        sf.revision = self.rev
        sf.srcLang = lang
        self.session.add(sf)
        self.session.commit()
        return sf

    def updateSourceFile(self, filename, content, lang='en'):
        sfile = self.session.query(SourceFile).filter(SourceFile.revision == self.rev,
                                                        SourceFile.projectId == self.projectId,
                                                        SourceFile.filename == filename,
                                                        SourceFile.srcLang == lang).first()
        if sfile == None:
            return self.insertSourceFile(filename, content, lang)
        else:
            sfile.filedata = content
            self.session.add(sfile)
            return sfile

    def updateSourcePhrase(self, phrase, sfile, lang='en'):
        phrases = TranslateHelper.stringEntryToSourcePhrase(phrase)
        for sp in phrases:
            # Exists in the database?
            if sp.stringKey in self.dbKeys:
                oldSp = self.dbKeys[sp.stringKey]
                oldSp.content = sp.content
                oldSp.translatable = sp.translatable
                self.session.add(oldSp)
            else:
                # Just insert record to the database.
                sp.projectId = self.projectId
                sp.revision = self.rev
                sp.sourceFile = sfile.id
                sp.srcLang = lang
                self.session.add(sp)