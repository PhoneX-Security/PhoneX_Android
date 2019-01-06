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

import android.content.SharedPreferences;

import net.phonex.util.Log;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.CountDownLatch;

public class CWSharedPreferences implements SharedPreferences, SerializablePreferences {
  private static final String TAG = "CWSharedPrefs";
  private static final HashMap<String, CWSharedPreferences> prefsRoster=new HashMap<String, CWSharedPreferences>();
  private static final Object nothing=new Object();
  private final WeakHashMap<OnSharedPreferenceChangeListener, Object> listeners=new WeakHashMap<OnSharedPreferenceChangeListener, Object>();
  private Map<String, Object> cache=new HashMap<String, Object>();
  private StorageStrategy strategy=null;
  private CountDownLatch latch=new CountDownLatch(1);

  static public CWSharedPreferences getInstance(StorageStrategy strategy) {
    CWSharedPreferences sp=null;

    synchronized(prefsRoster) {
      sp=prefsRoster.get(strategy.getKey());

      if (sp == null) {
        sp=new CWSharedPreferences(strategy);
        prefsRoster.put(strategy.getKey(), sp);

        return(sp);
      }
      else {
        strategy.close();
      }
    }

    return(sp);
  }

  static public SharedPreferences getInstance(String key) {
    CWSharedPreferences sp=null;

    synchronized(prefsRoster) {
      sp=prefsRoster.get(key);
    }

    return(sp);
  }

  CWSharedPreferences(final StorageStrategy strategy) {
    this.strategy=strategy;

    if (strategy.getLoadPolicy() == LoadPolicy.SYNC) {
      reload();
      latch.countDown();
    }
    else {
      new Thread() {
        public void run() {
          reload();
          latch.countDown();
        }
      }.start();
    }
  }

  @Override
  public boolean contains(String key) {
    checkSync();

    return(cache.containsKey(key));
  }

  @Override
  public Editor edit() {
    checkSync();

    return(new Editor(this));
  }

  @Override
  public Map<String, ?> getAll() {
    checkSync();

    synchronized(this) {
      return(new HashMap<String, Object>(cache));
    }
  }

  @Override
  public boolean getBoolean(String key, boolean defValue) {
    checkSync();

    synchronized(this) {
      Boolean result=(Boolean)cache.get(key);

      return(result != null ? result : defValue);
    }
  }

  @Override
  public float getFloat(String key, float defValue) {
    checkSync();

    synchronized(this) {
      Float result=(Float)cache.get(key);

      return(result != null ? result : defValue);
    }
  }

  @Override
  public int getInt(String key, int defValue) {
    checkSync();

    synchronized(this) {
      Integer result=(Integer)cache.get(key);

      return(result != null ? result : defValue);
    }
  }

  @Override
  public long getLong(String key, long defValue) {
    checkSync();

    synchronized(this) {
      Long result=(Long)cache.get(key);

      return(result != null ? result : defValue);
    }
  }

  @Override
  public String getString(String key, String defValue) {
    checkSync();

    synchronized(this) {
      String result=(String)cache.get(key);

      return(result != null ? result : defValue);
    }
  }

  @Override
  public Set<String> getStringSet(String key, Set<String> defValues) {
    checkSync();

    synchronized(this) {
      @SuppressWarnings("unchecked")
      Set<String> result=(Set<String>)cache.get(key);

      return(result != null ? result : defValues);
    }
  }

  @Override
  public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {
    checkSync();

    synchronized(this) {
      listeners.put(listener, nothing);
    }
  }

  @Override
  public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {
    checkSync();

    synchronized(this) {
      listeners.remove(listener);
    }
  }

  void reload() {
    strategy.load(cache);
  }

  private void checkSync() {
    if (strategy.getLoadPolicy() == LoadPolicy.SYNC
        || latch.getCount() == 0) {
      return;
    }

    if (strategy.getLoadPolicy() == LoadPolicy.ASYNC_EXCEPTION) {
      throw new RuntimeException(
                                 "Tried to access preferences before load completed");
    }

    try {
      latch.await();
    }
    catch (InterruptedException e) {
      // presume life is OK and move along
    }
  }

  private void notifyListeners(Collection<String> changes) {
    synchronized(this) {
      for (OnSharedPreferenceChangeListener listener : listeners.keySet()) {
        for (String key : changes) {
          listener.onSharedPreferenceChanged(this, key);
        }
      }
    }
  }

  private void persist(Collection<String> changes) {
    synchronized(this) {
      strategy.persist(cache, changes);
    }
  }

    /**
     * Exports current preferences to the output stream.
     * Snapshoting mechanism.
     *
     * @param os
     */
    @Override
    public synchronized void exportPrefs(OutputStream os) {
        checkSync();
        reload();

        DataOutputStream dos = null;
        try {
            dos = new DataOutputStream(os);
            for(Map.Entry<String, Object> en : cache.entrySet()){
                String[] k = strategy.buildPersistArgs(cache, en.getKey());
                if (k==null){
                    continue;
                }

                // Write key, value and type to the DataOutputStream.
                dos.writeUTF(k[0]);
                dos.writeUTF(k[1]);
                dos.writeUTF(k[2]);
            }

            dos.flush();
            Log.v(TAG, "Export finished");

        } catch(Exception ex){
            Log.e(TAG, "Exception in exporting preferences", ex);
        } finally {
            // Do not close since it would close underlying stream.
            //MiscUtils.closeSilently("exportPrefs", dos);
        }
    }

    /**
     * Reads snapshot from the input stream.
     *
     * @param is
     * @throws ImportException
     */
    @Override
    public synchronized int importPrefs(InputStream is) throws ImportException {
        checkSync();
        reload();

        DataInputStream dis = null;
        List<String> changes = new LinkedList<String>();
        Map<String, Object> newCache = new HashMap<String, Object>();
        try {
            dis = new DataInputStream(is);
            try {
                while (true) {
                    final String key = dis.readUTF();
                    final String val = dis.readUTF();
                    final String typeSt = dis.readUTF();
                    final int type = Integer.parseInt(typeSt);

                    newCache.put(key, strategy.buildObject(val, type));
                    changes.add(key);
                }
            } catch(EOFException eofEx){
                // Legitimate exception at the end of the stream.
            }

            // If new cache is empty, drop it.
            if (newCache.isEmpty()){
                Log.v(TAG, "Cache empty, not saving.");
                return -2;
            }

            // Swap caches, check sync.
            cache = newCache;
            strategy.persist(cache, changes);
            Log.vf(TAG, "Import finished, size=%d", cache.size());

            return 1;

        } catch(Exception ex){
            Log.e(TAG, "Exception in exporting preferences", ex);
            throw new ImportException("Cannot import");
        } finally {
            // Do not close since it would close underlying stream.
            //MiscUtils.closeSilently("exportPrefs", dos);
        }
    }

    static public class Editor implements SharedPreferences.Editor {
    private final Map<String, Object> changes=new HashMap<String, Object>();
    private final Object removeMarker=new Object();
    private boolean shouldClear=false;
    private CWSharedPreferences prefs=null;

    Editor(CWSharedPreferences prefs) {
      this.prefs=prefs;
    }

    @Override
    public void apply() {
      final Collection<String> changes=updateCache();

      new Thread() {
        public void run() {
          prefs.persist(changes);
        }
      }.start();

      prefs.notifyListeners(changes);
    }

    @Override
    public android.content.SharedPreferences.Editor clear() {
      synchronized(this) {
        shouldClear=true;
      }

      return(this);
    }

    @Override
    public boolean commit() {
      Collection<String> changes=updateCache();

      prefs.persist(changes);
      prefs.notifyListeners(changes);

      return(true);
    }

    @Override
    public android.content.SharedPreferences.Editor putBoolean(String key,
                                                               boolean value) {
      synchronized(this) {
        changes.put(key, value);
      }

      return(this);
    }

    @Override
    public android.content.SharedPreferences.Editor putFloat(String key,
                                                             float value) {
      synchronized(this) {
        changes.put(key, value);
      }

      return(this);
    }

    @Override
    public android.content.SharedPreferences.Editor putInt(String key,
                                                           int value) {
      synchronized(this) {
        changes.put(key, value);
      }

      return(this);
    }

    @Override
    public android.content.SharedPreferences.Editor putLong(String key,
                                                            long value) {
      synchronized(this) {
        changes.put(key, value);
      }

      return(this);
    }

    @Override
    public android.content.SharedPreferences.Editor putString(String key,
                                                              String value) {
      synchronized(this) {
        changes.put(key, value);
      }

      return(this);
    }

    @Override
    public android.content.SharedPreferences.Editor putStringSet(String key,
                                                                 Set<String> value) {
      synchronized(this) {
        changes.put(key, value);
      }

      return(this);
    }

    @Override
    public android.content.SharedPreferences.Editor remove(String key) {
      synchronized(this) {
        changes.put(key, removeMarker);
      }

      return(this);
    }

    private Collection<String> updateCache() {
      Set<String> result=new HashSet<String>();

      if (shouldClear) {
        prefs.cache.clear();
        result.addAll(prefs.cache.keySet());
      }

      for (Map.Entry<String, Object> e : changes.entrySet()) {
        String key=e.getKey();
        Object value=e.getValue();

        if (value == removeMarker) {
          prefs.cache.remove(key);
          result.add(key);
        }
        else if (!prefs.cache.containsKey(key)
            || (value == null && prefs.cache.get(key)!=null)
            || (value != null && value.equals(prefs.cache.get(key))==false)) {
          prefs.cache.put(key, value);
          result.add(key);
        }
      }

      return(result);
    }
  }

  public interface StorageStrategy {
    void close();

    void persist(Map<String, Object> cache,
                 Collection<String> changedKeys);

    void load(Map<String, Object> cache);

    void exportData(OutputStream os);
    int  importData(InputStream is) throws ImportException;
    String[] buildPersistArgs(Map<String, Object> cache, String key);
    Object buildObject(String stringValue, int type);

    String getKey();

    LoadPolicy getLoadPolicy();
  }

  public enum LoadPolicy {
    SYNC, ASYNC_BLOCK, ASYNC_EXCEPTION
  }

}
