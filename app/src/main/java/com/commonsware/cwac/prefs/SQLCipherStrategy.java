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

import com.commonsware.cwac.prefs.CWSharedPreferences.LoadPolicy;

import net.sqlcipher.database.SQLiteDatabase;
import net.sqlcipher.database.SQLiteOpenHelper;

import java.io.File;
import java.util.Collection;
import java.util.Map;

public class SQLCipherStrategy extends AbstractSQLStrategy implements CWSharedPreferences.StorageStrategy {
  private SQLiteDatabase db=null;

  public static boolean exists(Context ctxt, String key) {
    File path=ctxt.getDatabasePath(key);
    
    return(path.exists());
  }
  
  public SQLCipherStrategy(Context ctxt, String key, String password,
                           LoadPolicy loadPolicy) {
    super(loadPolicy);
    SQLiteDatabase.loadLibs(ctxt);
    this.key = key;  
    db=new Helper(ctxt, key).getWritableDatabase(password);
  }
  
  public SQLCipherStrategy(Context ctxt, String key, String dbName, String password, 
                           LoadPolicy loadPolicy) {
	super(loadPolicy);
	SQLiteDatabase.loadLibs(ctxt);
	this.key = key;
	db=new Helper(ctxt, dbName).getWritableDatabase(password);
  }  

  public void close() {
    db.close();
    db=null;
  }

  public void persist(Map<String, Object> cache,
                      Collection<String> changedKeys) {
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
	Cursor c = db.rawQuery("SELECT key, value, type FROM prefs", null);
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
