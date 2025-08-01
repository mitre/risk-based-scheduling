
package org.mitre.bch.cath.simulation.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/** A dictionary-like class with a three element composite key
 *
 * @param <I> Class for the first key
 * @param <J> Class for the second key
 * @param <V> Class for the value
 */
public class TwoWayMap<I, J, V>  {
    /** Static logger */
    private static final Logger LOGGER = LoggerFactory.getLogger(TwoWayMap.class);
    //===== Attributes ======//
    private final Map<Key, V> map = new HashMap<>();

    //===== Constructors ======//
    public TwoWayMap() {  }
    //===== Methods ======//

    /** get value given the 3 element composite key
     *
     * @param i first element of key
     * @param j second element of key
     * @return value
     */
    public V get(I i, J j){
        Key key = new Key(i,j);
        if (!map.containsKey(key)) {
            LOGGER.error("Key not found: ({}, {}, {})", i, j);
            return null;
        } else {
            return map.get(key);
        }

    }

    /** set value given the 3 element compositie key
     *
     * @param i first element of key
     * @param j second element of key
     * @param v value
     */
    public void set(I i, J j, V v){
        Key key = new Key(i,j);
        map.put(key, v);
    }

    /** check if 3 element composite key exists
     *
     * @param i first element of key
     * @param j second element of key
     * @return whether the Map contains the key
     */
    public boolean containsKey(I i, J j){
        Key key = new Key(i,j);
        return map.containsKey(key);
    }
    //===== SubClasses ======//
    private static class Key <I, J> {
        //===== Attributes ======//
        private final I i;
        private final J j;

        //===== Constructors ======//
        public Key(I i, J j) {
            this.i = i;
            this.j = j;
        }
        //===== Methods ======//
        @Override
        public boolean equals(Object o) {
            if (o == this) {
                return true;
            }
            if (o instanceof Key) {
                Key<I,J> key = (Key<I,J>) o;
                return this.i.equals(key.i) && this.j.equals(key.j);
            }
            return false;
        }

        @Override
        public int hashCode() {
            return (
                    i.toString()
                            +"_"
                            +j.toString()
            ).hashCode();
        }
    }
}