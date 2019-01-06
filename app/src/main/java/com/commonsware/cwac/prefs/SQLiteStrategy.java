/***
  Copyright (c) 2012 CommonsWare, LLC
  
  Licensed under the Apache License, Version 2.0 (the "License"); you may
  not use this file except in compliance with the License. You may obtain
  a copy of the License at
    http://www.apache.org/licenses/LICENSE-2.0
  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
 */

package com.commonsware.cwac.prefs;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.commonsware.cwac.prefs.CWSharedPreferences.LoadPolicy;

import java.util.Collection;
import java.util.Map;

public class SQLiteStrategy extends AbstractSQLStrategy implements
    CWSharedPreferences.StorageStrategy {
  private Helper helper=null;

  public SQLiteStrategy(Context ctxt, String key, LoadPolicy loadPolicy) {
    super(loadPolicy);
    this.key=key;
    helper=new Helper(ctxt, key);
  }
  
  public SQLiteStrategy(Context ctxt, String key, String dbName, LoadPolicy loadPolicy) {
	super(loadPolicy);
	this.key=key;
	helper=new Helper(ctxt, dbName);
  }

  public void close() {
    helper.close();
  }

  public void persist(Map<String, Object> cache,
                      Collection<String> changedKeys) {
    SQLiteDatabase db=helper.getWritableDatabase();

    db.beginTransaction();

    try {
      for (String key : changedKeys) {
        String args[]=buildPersistArgs(cache, key);

        if (args != null) {
          db.execSQL("INSERT OR REPLACE INTO prefs (key, value, type) VALUES (?, ?, ?)",
                     args);
        }
        else {
          String[] deleteArgs= { key };

          db.delete("prefs", "key=?", deleteArgs);
        }
      }

      db.setTransactionSuccessful();
    }
    finally {
      db.endTransaction();
    }
  }

  public void load(Map<String, Object> cache) {
	  Cursor c = helper.getReadableDatabase().rawQuery("SELECT key, value, type FROM prefs", null);
	  load(c, cache);
	  if (c!=null){
	  	try { c.close(); } catch(Exception e){ }
	  }
  }

  private static class Helper extends SQLiteOpenHelper {
    private static final int SCHEMA_VERSION=1;

    public Helper(Context ctxt, String name) {
      super(ctxt, name, null, SCHEMA_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
      db.execSQL("CREATE TABLE prefs (key TEXT NOT NULL PRIMARY KEY, value TEXT, type INTEGER NOT NULL)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion,
                          int newVersion) {
      throw new RuntimeException("You cannot be serious!");
    }
  }
}
