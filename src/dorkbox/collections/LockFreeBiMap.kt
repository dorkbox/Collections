/*
 * Copyright 2023 dorkbox, llc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dorkbox.collections

import java.io.Serializable
import java.util.concurrent.atomic.*

/**
 * A bimap (or "bidirectional map") is a map that preserves the uniqueness of its values as well as that of its keys. This constraint
 * enables bimaps to support an "inverse view", which is another bimap containing the same entries as this bimap but with reversed keys and values.
 *
 * This class uses the "single-writer-principle" for lock-free publication.
 *
 * Since there are only 2 methods to guarantee that modifications can only be called one-at-a-time (either it is only called by
 * one thread, or only one thread can access it at a time) -- we chose the 2nd option -- and use 'synchronized' to make sure that only
 * one thread can access this modification methods at a time. Getting or checking the presence of values can then happen in a lock-free
 * manner.
 *
 * This uses regular hashmaps as the backing data structures
 */
class LockFreeBiMap<K: Any, V: Any> : MutableMap<K, V>, Cloneable, Serializable {
    @Volatile
    private var forwardHashMap: MutableMap<K, V>

    @Volatile
    private var reverseHashMap: MutableMap<V, K>

    private val inverse: LockFreeBiMap<V, K>

    // synchronized is used here to ensure the "single writer principle", and make sure that ONLY one thread at a time can enter this
    // section. Because of this, we can have unlimited reader threads all going at the same time, without contention (which is our
    // use-case 99% of the time)s
    constructor() {
        forwardHashMap = HashMap()
        reverseHashMap = HashMap()
        inverse = LockFreeBiMap(reverseHashMap, forwardHashMap, this)
    }

    private constructor(forwardHashMap: MutableMap<K, V>, reverseHashMap: MutableMap<V, K>, inverse: LockFreeBiMap<V, K>) {
        this.forwardHashMap = forwardHashMap
        this.reverseHashMap = reverseHashMap
        this.inverse = inverse
    }


    override val size: Int
        get() = forwardHashMap.size

    /**
     * Removes all the mappings from this bimap.
     * The bimap will be empty after this call returns.
     */
    @Synchronized
    override fun clear() {
        forwardHashMap.clear()
        reverseHashMap.clear()
    }

    override fun containsValue(value: V): Boolean {
        // use the SWP to get a lock-free get of the value
        return forwardREF[this].containsValue(value)
    }

    override fun containsKey(key: K): Boolean {
        // use the SWP to get a lock-free get of the value
        return forwardREF[this].containsKey(key)
    }

    /**
     * @return the inverse view of this bimap, which maps each of this bimap's values to its associated key.
     */
    fun inverse(): LockFreeBiMap<V, K> {
        return inverse
    }

    /**
     * Replaces all the mappings from the specified map to this bimap.
     * These mappings will replace any mappings that this map had for
     * any of the keys currently in the specified map.
     *
     * @param hashMap mappings to be stored in this map
     *
     * @throws NullPointerException if the specified map is null
     *
     * @throws IllegalArgumentException if a given value in the map is already bound to a different key in this bimap. The bimap will remain
     * unmodified in this event. To avoid this exception, call [.replaceAllForce] replaceAllForce(map) instead.
     */
    @Synchronized
    @Throws(IllegalArgumentException::class)
    fun replaceAll(hashMap: Map<K, V>?) {
        if (hashMap == null) {
            throw NullPointerException("hashMap")
        }
        val biMap = LockFreeBiMap<K, V>()
        try {
            biMap.putAll(hashMap)
        }
        catch (e: IllegalArgumentException) {
            // do nothing if there is an exception
            throw e
        }

        // only if there are no problems with the creation of the new bimap.
        forwardHashMap.clear()
        reverseHashMap.clear()
        forwardHashMap.putAll(biMap.forwardHashMap)
        reverseHashMap.putAll(biMap.reverseHashMap)
    }

    /**
     * Replaces all the mappings from the specified map to this bimap.
     * These mappings will replace any mappings that this map had for
     * any of the keys currently in the specified map. This is an alternate
     * form of [.replaceAll] replaceAll(K, V) that will silently
     * ignore duplicates
     *
     * @param hashMap mappings to be stored in this map
     *
     * @throws NullPointerException if the specified map is null
     */
    @Synchronized
    fun replaceAllForce(hashMap: Map<K, V>?) {
        if (hashMap == null) {
            throw NullPointerException("hashMap")
        }

        // only if there are no problems with the creation of the new bimap.
        forwardHashMap.clear()
        reverseHashMap.clear()
        putAllForce(hashMap)
    }

    /**
     * Associates the specified value with the specified key in this bimap.
     * If the bimap previously contained a mapping for the key, the old
     * value is replaced. If the given value is already bound to a different
     * key in this bimap, the bimap will remain unmodified. To avoid throwing
     * an exception, call [.putForce] putForce(K, V) instead.
     *
     * @param key key with which the specified value is to be associated
     * @param value value to be associated with the specified key
     *
     * @return the previous value associated with <tt>key</tt>, or
     * <tt>null</tt> if there was no mapping for <tt>key</tt>.
     * (A <tt>null</tt> return can also indicate that the map
     * previously associated <tt>null</tt> with <tt>key</tt>.)
     *
     * @throws IllegalArgumentException if the given value is already bound to a different key in this bimap. The bimap will remain
     * unmodified in this event. To avoid this exception, call [.putForce]  putForce(K, V) instead.
     */
    @Synchronized
    @Throws(IllegalArgumentException::class)
    override fun put(key: K, value: V): V? {
        val prevForwardValue = forwardHashMap.put(key, value)
        if (prevForwardValue != null) {
            reverseHashMap.remove(prevForwardValue)
        }
        val prevReverseValue = reverseHashMap.put(value, key)
        if (prevReverseValue != null) {
            // put the old value back
            if (prevForwardValue != null) {
                forwardHashMap[key] = prevForwardValue
            }
            else {
                forwardHashMap.remove(key)
            }
            reverseHashMap[value] = prevReverseValue
            throw IllegalArgumentException("Value already exists. Keys and values must both be unique!")
        }
        return prevForwardValue
    }

    /**
     * Associates the specified value with the specified key in this bimap.
     * If the bimap previously contained a mapping for the key, the old
     * value is replaced. This is an alternate form of [.put] put(K, V)
     * that will silently ignore duplicates
     *
     * @param key key with which the specified value is to be associated
     * @param value value to be associated with the specified key
     *
     * @return the previous value associated with <tt>key</tt>, or
     * <tt>null</tt> if there was no mapping for <tt>key</tt>.
     * (A <tt>null</tt> return can also indicate that the map
     * previously associated <tt>null</tt> with <tt>key</tt>.)
     */
    @Synchronized
    fun putForce(key: K, value: V): V? {
        val prevForwardValue = forwardHashMap.put(key, value)
        if (prevForwardValue != null) {
            reverseHashMap.remove(prevForwardValue)
        }
        val prevReverseValue = reverseHashMap.put(value, key)
        if (prevReverseValue != null) {
            forwardHashMap.remove(prevReverseValue)
        }
        return prevForwardValue
    }

    /**
     * Copies all the mappings from the specified map to this map.
     * These mappings will replace any mappings that this map had for
     * any of the keys currently in the specified map.
     *
     * @param from mappings to be stored in this map
     *
     * @throws NullPointerException if the specified map is null
     *
     * @throws IllegalArgumentException if the given value is already bound to a different key in this bimap. The bimap will remain
     * unmodified in this event. To avoid this exception, call [.putAllForce] putAllForce(K, V) instead.
     */
    @Synchronized
    @Throws(IllegalArgumentException::class)
    override fun putAll(from: Map<out K, V>) {
        val biMap = LockFreeBiMap<K, V>()
        for ((key, value) in from) {
            biMap.put(key, value)

            // we have to verify that the keys/values between the bimaps are unique
            require(!forwardHashMap.containsKey(key)) { "Key already exists. Keys and values must both be unique!" }
            require(!reverseHashMap.containsKey(value)) { "Value already exists. Keys and values must both be unique!" }
        }


        // only if there are no problems with the creation of the new bimap AND the uniqueness constrain is guaranteed
        forwardHashMap.putAll(biMap.forwardHashMap)
        reverseHashMap.putAll(biMap.reverseHashMap)
    }

    /**
     * Copies all the mappings from the specified map to this map.
     * These mappings will replace any mappings that this map had for
     * any of the keys currently in the specified map. This is an alternate
     * form of [.putAll] putAll(K, V) that will silently
     * ignore duplicates
     *
     * @param hashMap mappings to be stored in this map
     *
     * @throws NullPointerException if the specified map is null
     */
    @Synchronized
    fun putAllForce(hashMap: Map<K, V>) {
        for ((key, value) in hashMap) {
            putForce(key, value)
        }
    }

    /**
     * Removes the mapping for the specified key from this map if present.
     *
     * @param key key whose mapping is to be removed from the map
     *
     * @return the previous value associated with <tt>key</tt>, or
     * <tt>null</tt> if there was no mapping for <tt>key</tt>.
     * (A <tt>null</tt> return can also indicate that the map
     * previously associated <tt>null</tt> with <tt>key</tt>.)
     */
    @Synchronized
    override fun remove(key: K): V? {
        val value = forwardHashMap.remove(key)
        reverseHashMap.remove(value)
        return value
    }

    /**
     * Returns the value to which the specified key is mapped,
     * or `null` if this map contains no mapping for the key.
     *
     *
     * More formally, if this map contains a mapping from a key
     * `k` to a value `v` such that `(key==null ? k==null :
     * key.equals(k))`, then this method returns `v`; otherwise
     * it returns `null`.  (There can be at most one such mapping.)
     *
     *
     * A return value of `null` does not *necessarily*
     * indicate that the map contains no mapping for the key; it's also
     * possible that the map explicitly maps the key to `null`.
     *
     * The [containsKey][containsKey] operation may be used to
     * distinguish these two cases.
     *
     * @see .put
     */
    override operator fun get(key: K): V? {
        // use the SWP to get a lock-free get of the value
        @Suppress("UNCHECKED_CAST")
        return forwardREF[this][key] as V?
    }

    /**
     * Returns the reverse key to which the specified key is mapped,
     * or `null` if this map contains no mapping for the key.
     *
     *
     * More formally, if this map contains a mapping from a key
     * `k` to a value `v` such that `(key==null ? k==null :
     * key.equals(k))`, then this method returns `v`; otherwise
     * it returns `null`.  (There can be at most one such mapping.)
     *
     *
     *
     * A return value of `null` does not *necessarily*
     * indicate that the map contains no mapping for the key; it's also
     * possible that the map explicitly maps the key to `null`.
     * The [containsKey][containsKey] operation may be used to
     * distinguish these two cases.
     *
     * @see .put
     */
    fun getReverse(key: V): K? {
        // use the SWP to get a lock-free get of the value
        @Suppress("UNCHECKED_CAST")
        return reverseREF[this][key] as K?
    }


    /**
     * Returns a {@link Set} view of the keys contained in this map.
     * The set is backed by the map, so changes to the map are
     * reflected in the set, and vice-versa.  If the map is modified
     * while an iteration over the set is in progress (except through
     * the iterator's own <tt>remove</tt> operation), the results of
     * the iteration are undefined.  The set supports element removal,
     * which removes the corresponding mapping from the map, via the
     * <tt>Iterator.remove</tt>, <tt>Set.remove</tt>,
     * <tt>removeAll</tt>, <tt>retainAll</tt>, and <tt>clear</tt>
     * operations.  It does not support the <tt>add</tt> or <tt>addAll</tt>
     * operations.
     *
     * @return a set view of the keys contained in this map
     */
    override val entries: MutableSet<MutableMap.MutableEntry<K, V>>
        // use the SWP to get a lock-free get of the value
        @Suppress("UNCHECKED_CAST")
        get() = forwardREF[this].keys as MutableSet<MutableMap.MutableEntry<K, V>>



    /**
     * Returns a {@link Set} view of the keys contained in this map.
     * The set is backed by the map, so changes to the map are
     * reflected in the set, and vice-versa.  If the map is modified
     * while an iteration over the set is in progress (except through
     * the iterators own <tt>remove</tt> operation), the results of
     * the iteration are undefined.  The set supports element removal,
     * which removes the corresponding mapping from the map, via the
     * <tt>Iterator.remove</tt>, <tt>Set.remove</tt>,
     * <tt>removeAll</tt>, <tt>retainAll</tt>, and <tt>clear</tt>
     * operations.  It does not support the <tt>add</tt> or <tt>addAll</tt>
     * operations.
     *
     * @return a set view of the keys contained in this map
     */
    override val keys: MutableSet<K>
        // use the SWP to get a lock-free get of the value
        @Suppress("UNCHECKED_CAST")
        get() = forwardREF[this].keys as MutableSet<K>

    /**
     * Returns a [Collection] view of the values contained in this map.
     * The collection is backed by the map, so changes to the map are
     * reflected in the collection, and vice-versa.  If the map is
     * modified while an iteration over the collection is in progress
     * (except through the iterators own <tt>remove</tt> operation),
     * the results of the iteration are undefined.  The collection
     * supports element removal, which removes the corresponding
     * mapping from the map, via the <tt>Iterator.remove</tt>,
     * <tt>Collection.remove</tt>, <tt>removeAll</tt>,
     * <tt>retainAll</tt> and <tt>clear</tt> operations.  It does not
     * support the <tt>add</tt> or <tt>addAll</tt> operations.
     *
     * @return a view of the values contained in this map
     */
    override val values: MutableCollection<V>
        // use the SWP to get a lock-free get of the value
        @Suppress("UNCHECKED_CAST")
        get() = forwardREF[this].values as MutableCollection<V>

    /**
     * Returns <tt>true</tt> if this bimap contains no key-value mappings.
     *
     * @return <tt>true</tt> if this bimap contains no key-value mappings
     */
    override fun isEmpty(): Boolean {
        // use the SWP to get a lock-free get of the value
        return forwardREF[this].isEmpty()
    }

    /**
     * Returns a [Collection] view of the values contained in this map.
     * The collection is backed by the map, so changes to the map are
     * reflected in the collection, and vice-versa.  If the map is
     * modified while an iteration over the collection is in progress
     * (except through the iterators own <tt>remove</tt> operation),
     * the results of the iteration are undefined.  The collection
     * supports element removal, which removes the corresponding
     * mapping from the map, via the <tt>Iterator.remove</tt>,
     * <tt>Collection.remove</tt>, <tt>removeAll</tt>,
     * <tt>retainAll</tt> and <tt>clear</tt> operations.  It does not
     * support the <tt>add</tt> or <tt>addAll</tt> operations.
     *
     * @return a view of the values contained in this map
     */
    val reverseValues: MutableCollection<K>
        // use the SWP to get a lock-free get of the value
        @Suppress("UNCHECKED_CAST")
        get() = reverseREF[this].values as MutableCollection<K>

    companion object {
        const val version = Collections.version

        // Recommended for best performance while adhering to the "single writer principle". Must be static-final
        private val forwardREF = AtomicReferenceFieldUpdater.newUpdater(
            LockFreeBiMap::class.java, HashMap::class.java, "forwardHashMap"
        )
        private val reverseREF = AtomicReferenceFieldUpdater.newUpdater(
            LockFreeBiMap::class.java, HashMap::class.java, "reverseHashMap"
        )
    }
}
