#!/usr/bin/env python2
'''
Displays information about current source revision.

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

from sqlalchemy import func
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker
from commons import Base, SourceFile, SourcePhrases, Translation

class Main(TRBase):
    lang = None

    def work(self):
        self.connect()

        # Source phrase list:
        phraseList = self.session.query(SourcePhrases, func.count(SourcePhrases.id)) \
            .filter(SourcePhrases.projectId == self.projectId) \
            .group_by(SourcePhrases.srcLang, SourcePhrases.revision) \
            .order_by(SourcePhrases.srcLang, SourcePhrases.revision).all()

        print "* Source phrase revision list: "
        for (srcPhr, cnt) in phraseList:
            print " - SrcLang: %s, revision: %s, date created: %s, date modified: %s, phrase count: %s" % \
                  (srcPhr.srcLang, srcPhr.revision, srcPhr.dateCreated, srcPhr.dateModified, cnt)
        print ""
        pass

        # Source file list:
        fileList = self.session.query(SourceFile, func.count(SourceFile.id)) \
            .filter(SourceFile.projectId == self.projectId) \
            .group_by(SourceFile.srcLang, SourceFile.revision) \
            .order_by(SourceFile.srcLang, SourceFile.revision).all()
        print "* Source file revision list: "
        for (srcFl, cnt) in fileList:
            print " - SrcLang: %s, revision: %s, date created: %s, date modified: %s, file count: %s" % \
                  (srcFl.srcLang, srcFl.revision, srcFl.dateCreated, srcFl.dateModified, cnt)
        print ""
        pass

        # Translation report:
        transList = self.session.query(Translation, func.count(Translation.id)) \
            .filter(Translation.projectId == self.projectId) \
            .group_by(Translation.dstLang, Translation.revision) \
            .order_by(Translation.dstLang, Translation.revision).all()
        print "* Translation report:"
        for (trEn, cnt) in transList:
            print " - DstLang: %s, revision: %s, date created: %s, date modified: %s, phrases translated: %s" % \
                  (trEn.dstLang, trEn.revision, trEn.dateCreated, trEn.dateModified, cnt)
        print ""
        pass





# Main executable code
if __name__ == '__main__':
    parser = argparse.ArgumentParser(description='Displays information about current source revision.', formatter_class=argparse.ArgumentDefaultsHelpFormatter)
    parser.add_argument('--app',            help='Path to the Android app. If not specifies it assumes current directory is your Android app directory', default='.', required=False)
    args = parser.parse_args()

    m = Main()
    m.app = args.app
    m.work()
