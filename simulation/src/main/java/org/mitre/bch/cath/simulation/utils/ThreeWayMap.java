package org.mitre.bch.cath.simulation.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/** A dictionary-like class with a three element composite key
 *
 * @param <I> Class for the first key
 * @param <J> Class for the second key
 * @param <K> Class for the third key
 * @param <V> Class for the value
 */
public class ThreeWayMap<I, J, K, V>  {
    /** Static logger */
    private static final Logger LOGGER = LoggerFactory.getLogger(ThreeWayMap.class);
    //===== Attributes ======//
    private final Map<Key, V> map = new HashMap<>();

    //===== Constructors ======//
    public ThreeWayMap() {  }
    //===== Methods ======//

    /** get value given the 3 element composite key
     *
     * @param i first element of key
     * @param j second element of key
     * @param k third element of key
     * @return value
     */
    public V get(I i, J j, K k){
        Key key = new Key(i,j,k);
        if (!map.containsKey(key)) {
            LOGGER.error("Key not found: ({}, {}, {})", i, j, k);
            return null;
        } else {
            return map.get(key);
        }

    }

    /** set value given the 3 element composite key
     *
     * @param i first element of key
     * @param j second element of key
     * @param k third element of key
     * @param v value
     */
    public void set(I i, J j, K k, V v){
        Key key = new Key(i,j,k);
        map.put(key, v);
    }

    /** check if 3 element composite key exists
     *
     * @param i first element of key
     * @param j second element of key
     * @param k third element of key
     * @return whether the Map contains the key
     */
    public boolean containsKey(I i, J j, K k){
        Key key = new Key(i,j,k);
        return map.containsKey(key);
    }
    //===== SubClasses ======//
    private static class Key <I, J, K> {
        //===== Attributes ======//
        private final I i;
        private final J j;
        private final K k;
        //===== Constructors ======//
        public Key(I i, J j, K k) {
            this.i = i;
            this.j = j;
            this.k = k;
        }
        //===== Methods ======//
        @Override
        public boolean equals(Object o) {
            if (o == this) {
                return true;
            }
            if (o instanceof Key) {
                Key<I,J,K> key = (Key<I,J,K>) o;
                return this.i.equals(key.i) && this.j.equals(key.j) && this.k.equals(key.k);
            }
            return false;
        }

        @Override
        public int hashCode() {
            return (
                    i.toString()
                            +"_"
                            +j.toString()
                            +"_"
                            +k.toString()
            ).hashCode();
        }
    }
}


