package net.phonex.util;

import java.util.HashMap;
import java.util.Map;

/**
 * Simple multiset implementation, based on HashMap-s.
 * Allows simple operations:
 * 
 *  contains(T) true if mutliset contains at least one T
 * 	count(T)	number of elements T in multiset
 *  inc(T)		increase number of elements T in multiset by 1
 *  dec(T)		decrease number of elements T in multiset by 1
 *  rm(T)		remove all elements T in multiset
 *  clear()		remove all elements
 * 
 * @author ph4r05
 *
 * TODO: implement extension of this variant = GarbagedMultiSet.
 * Elements of this set will auto-remove themselves after some time.
 *
 * @param <T>
 */
public class MultiSet<T>{
	protected Map<T, Integer> m = null;
	
	/**
	 * Default constructor
	 * 
	 * @param capacity
	 */
	public MultiSet() {
		m = new HashMap<T, Integer>();
	}
	
	/**
	 * Default construction for multiset with pre-defined capacity
	 * 
	 * @param capacity
	 */
	public MultiSet(int capacity) {
		m = new HashMap<T, Integer>(capacity);
	}

	/**
	 * Returns true if at least one t is in multiset
	 * @return
	 */
	public synchronized boolean contains(T t){
		if (m.containsKey(t)==false) return false;
		return (m.get(t) > 0);
	}
	
	/**
	 * Returns number of elements T in map
	 * @return
	 */
	public synchronized int count(T t){
		if (m.containsKey(t)==false) return 0;
		return m.get(t);
	}
	
	/**
	 * Increase number of elements T in multiset by 1
	 * 
	 * @param t
	 * @return	number of elements T in multiset AFTER operation
	 */
	public synchronized int inc(T t){
		int cur = count(t);
		m.put(t, cur+1);
		return (cur+1);
	}
	
	/**
	 * Decrease number of elements T in multiset by 1
	 * 
	 * @param t
	 * @return	number of elements T in multiset AFTER operation
	 */
	public synchronized int dec(T t){
		int cur = count(t);
		int n   = (cur-1) >= 0 ? cur-1 : 0;
		if (n == 0) m.remove(t);
		else 		m.put(t, n);
		return n;
	}
	
	/**
	 * Remove all elements T in multiset
	 * 
	 * @param t
	 * @return	number of elements T in multiset AFTER operation
	 */
	public synchronized Integer rm(T t){
		return m.remove(t);
	}
	
	/**
	 * Remove all elements T in multiset
	 */
	public synchronized void clear(){
		m.clear();
	}
}
