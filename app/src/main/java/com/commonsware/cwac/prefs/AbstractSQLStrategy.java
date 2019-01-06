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

import android.database.Cursor;

import com.commonsware.cwac.prefs.CWSharedPreferences.LoadPolicy;
import com.commonsware.cwac.prefs.CWSharedPreferences.StorageStrategy;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

abstract public class AbstractSQLStrategy implements StorageStrategy {
  protected static final int TYPE_NULL=0;
  protected static final int TYPE_BOOLEAN=1;
  protected static final int TYPE_INT=2;
  protected static final int TYPE_LONG=3;
  protected static final int TYPE_FLOAT=4;
  protected static final int TYPE_STRING=5;
  protected static final int TYPE_STRINGSET=6;
  protected String key=null;
  private LoadPolicy loadPolicy=null;
  
  AbstractSQLStrategy(LoadPolicy loadPolicy) {
    this.loadPolicy=loadPolicy;
  }
  
  public String getKey() {
    return(key);
  }
  
  public LoadPolicy getLoadPolicy() {
    return(loadPolicy);
  }

  @SuppressWarnings("unchecked")
  public String[] buildPersistArgs(Map<String, Object> cache, String key) {
    String[] result=null;

    if (cache.containsKey(key)) {
      String value=null;
      Object raw=cache.get(key);
      int type=0;

      if (raw == null) {
        type=TYPE_NULL;
      }
      else if (raw instanceof Set) {
        StringBuilder buf=new StringBuilder();

        for (String s : (Set<String>)raw) {
          buf.append(s);
          buf.append('\0');
        }

        value=buf.toString();
        type=TYPE_STRINGSET;
      }
      else {
        value=raw.toString();

        if (raw instanceof Boolean) {
          value=((Boolean)raw ? "1" : "0");
          type=TYPE_BOOLEAN;
        }
        else if (raw instanceof Integer) {
          type=TYPE_INT;
        }
        else if (raw instanceof Long) {
          type=TYPE_LONG;
        }
        else if (raw instanceof Float) {
          type=TYPE_FLOAT;
        }
        else {
          type=TYPE_STRING;
        }
      }

      String[] args= { key, value, String.valueOf(type) };

      result=args;
    }

    return(result);
  }

  public Object buildObject(String stringValue, int type){
      Object value=null;

      switch (type) {
          case TYPE_NULL:
              break;

          case TYPE_BOOLEAN:
              int i=Integer.parseInt(stringValue);
              value=(i == 1);
              break;

          case TYPE_INT:
              value=Integer.parseInt(stringValue);
              break;

          case TYPE_LONG:
              value=Long.parseLong(stringValue);
              break;

          case TYPE_FLOAT:
              value=Float.parseFloat(stringValue);
              break;

          case TYPE_STRING:
              value=stringValue;
              break;

          case TYPE_STRINGSET:
              String nulled=stringValue;
              String[] splits=nulled.split("\\x00");
              Set<String> output=new HashSet<String>();

              for (String s : splits) {
                  output.add(s);
              }

              value=output;

              break;
      }

      return value;
  }

  public void load(Cursor c, Map<String, Object> cache) {
    while (c.moveToNext()) {
      String key=c.getString(0);
      int type=c.getInt(2);
      Object value=null;

      switch (type) {
        case TYPE_NULL:
          break;

        case TYPE_BOOLEAN:
          int i=c.getInt(1);
          
          value=(i == 1);
          break;

        case TYPE_INT:
          value=c.getInt(1);
          break;

        case TYPE_LONG:
          value=c.getLong(1);
          break;

        case TYPE_FLOAT:
          value=c.getFloat(1);
          break;

        case TYPE_STRING:
          value=c.getString(1);
          break;

        case TYPE_STRINGSET:
          String nulled=c.getString(1);
          String[] splits=nulled.split("\\x00");
          Set<String> output=new HashSet<String>();

          for (String s : splits) {
            output.add(s);
          }

          value=output;

          break;
      }

      cache.put(key, value);
    }
  }

    /**
     * Should export data as SQL inserts, but this is time consuming
     * and that portability is not needed at the moment.
     * @param os
     */
    @Override
    public void exportData(OutputStream os) {
        throw new UnsupportedOperationException("Not implemented");
    }

    /**
     * Should import SQL inserts, not needed at the moment.
     * @param is
     * @return
     * @throws ImportException
     */
    @Override
    public int importData(InputStream is) throws ImportException {
        throw new UnsupportedOperationException("Not implemented");
    }
}