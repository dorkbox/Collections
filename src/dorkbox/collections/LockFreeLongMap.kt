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
 * This class uses the "single-writer-principle" for lock-free publication.
 *
 *
 * Since there are only 2 methods to guarantee that modifications can only be called one-at-a-time (either it is only called by
 * one thread, or only one thread can access it at a time) -- we chose the 2nd option -- and use 'synchronized' to make sure that only
 * one thread can access this modification methods at a time. Getting or checking the presence of values can then happen in a lock-free
 * manner.
 *
 *
 * According to my benchmarks, this is approximately 25% faster than ConcurrentHashMap for (all types of) reads, and a lot slower for
 * contended writes.
 *
 *
 * This data structure is for many-read/few-write scenarios
 *
 *
 * An unordered map. This implementation is a cuckoo hash map using 3 hashes, random walking, and a small stash for problematic
 * keys. Null keys are not allowed. Null values are allowed. No allocation is done except when growing the table size. <br></br>
 *
 *
 * This map performs very fast get, containsKey, and remove (typically O(1), worst case O(log(n))). Put may be a bit slower,
 * depending on hash collisions. Load factors greater than 0.91 greatly increase the chances the map will have to rehash to the
 * next higher POT size.
 *
 *
 * Iteration can be very slow for a map with a large capacity. [.clear] and [.shrink] can be used to reduce
 * the capacity. [OrderedMap] provides much faster iteration.
 */
class LockFreeLongMap<V> : MutableMap<Long, V>, Cloneable, Serializable {

    @Volatile
    private var hashMap: LongMap<V>

    // synchronized is used here to ensure the "single writer principle", and make sure that ONLY one thread at a time can enter this
    // section. Because of this, we can have unlimited reader threads all going at the same time, without contention (which is our
    // use-case 99% of the time)
    /**
     * Constructs an empty <tt>HashMap</tt> with the default initial capacity
     * (16) and the default load factor (0.75).
     */
    constructor() {
        hashMap = LongMap()
    }

    /**
     * Constructs an empty <tt>HashMap</tt> with the specified initial
     * capacity and the default load factor (0.75).
     *
     * @param initialCapacity the initial capacity.
     *
     * @throws StateException if the initial capacity is negative.
     */
    constructor(initialCapacity: Int) {
        hashMap = LongMap(initialCapacity)
    }

    /**
     * Constructs an empty <tt>HashMap</tt> with the specified initial
     * capacity and load factor.
     *
     * @param initialCapacity the initial capacity
     * @param loadFactor the load factor
     *
     * @throws StateException if the initial capacity is negative
     * or the load factor is nonpositive
     */
    constructor(initialCapacity: Int, loadFactor: Float) {
        hashMap = LongMap(initialCapacity, loadFactor)
    }

    override val size: Int
        get() {
            // use the SWP to get a lock-free get of the value
            return mapREF[this].size
        }

    override fun isEmpty(): Boolean {
        // use the SWP to get a lock-free get of the value
        return mapREF[this].size == 0
    }

    @Suppress("UNCHECKED_CAST")
    override fun containsKey(key: Long): Boolean {
        // use the SWP to get a lock-free get of the value
        val value = mapREF[this] as LongMap<V>
        return value.containsKey(key)
    }

    override fun containsValue(value: V): Boolean {
        return containsValue(value, false)
    }

    fun containsValue(value: Any?, identity: Boolean): Boolean {
        // use the SWP to get a lock-free get of the value
        return mapREF[this].containsValue(value, identity)
    }

    @Suppress("UNCHECKED_CAST")
    override operator fun get(key: Long): V? {
        // use the SWP to get a lock-free get of the value
        val value = mapREF[this] as LongMap<V>
        return value.get(key)
    }

    @Synchronized
    override fun put(key: Long, value: V): V? {
        return hashMap.put(key, value)
    }

    @Synchronized
    override fun remove(key: Long): V? {
        return hashMap.remove(key)
    }

    @Synchronized
    override fun putAll(from: Map<out Long, V>) {
        hashMap.putAll(from)
    }

    @Synchronized
    override fun clear() {
        hashMap.clear()
    }

    /**
     * DO NOT MODIFY THE MAP VIA THIS (unless you synchronize around it!) It will result in unknown object visibility!
     *
     * Returns an iterator for the keys in the map. Remove is supported. Note that the same iterator instance is returned each
     * time this method is called. Use the [ObjectMap.Entries] constructor for nested or multithreaded iteration.
     */
    override val keys: LongMap.Keys
        get() {
            return mapREF[this].keys()
        }

    /**
     * DO NOT MODIFY THE MAP VIA THIS (unless you synchronize around it!) It will result in unknown object visibility!
     *
     * Returns an iterator for the values in the map. Remove is supported. Note that the same iterator instance is returned each
     * time this method is called. Use the [ObjectMap.Entries] constructor for nested or multithreaded iteration.
     */
    @Suppress("UNCHECKED_CAST")
    override val values: LongMap.Values<V>
        get() {
            return mapREF[this].values() as LongMap.Values<V>
        }

    /**
     * DO NOT MODIFY THE MAP VIA THIS (unless you synchronize around it!) It will result in unknown object visibility!
     *
     * Returns an iterator for the entries in the map. Remove is supported. Note that the same iterator instance is returned each
     * time this method is called. Use the [ObjectMap.Entries] constructor for nested or multithreaded iteration.
     */
    @Suppress("UNCHECKED_CAST")
    override val entries: MutableSet<MutableMap.MutableEntry<Long, V>>
        get() {
            return mapREF[this].entries() as MutableSet<MutableMap.MutableEntry<Long, V>>
        }

    override fun equals(other: Any?): Boolean {
        return mapREF[this] == other
    }

    fun equalsIdentity(other: Any?): Boolean {
        return mapREF[this].equalsIdentity(other)
    }

    override fun hashCode(): Int {
        return mapREF[this].hashCode()
    }

    override fun toString(): String {
        return mapREF[this].toString()
    }

    /**
     * Clears the map and reduces the size of the backing arrays to be the specified capacity, if they are larger. The reduction
     * is done by allocating new arrays, though for large arrays this can be faster than clearing the existing array.
     */
    @Synchronized
    fun clear(maximumCapacity: Int) {
        mapREF[this].clear(maximumCapacity)
    }

    /**
     * Reduces the size of the backing arrays to be the specified capacity or less. If the capacity is already less, nothing is
     * done.
     * If the map contains more items than the specified capacity, the next highest power of two capacity is used instead.
     */
    @Synchronized
    fun shrink(maximumCapacity: Int) {
        mapREF[this].shrink(maximumCapacity)
    }


    /**
     * Return a non-thread-safe copy of the backing map
     */
    fun toMap(): LongMap<V> {
        return LongMap(mapREF[this] as LongMap<V>)
    }

    companion object {
        const val version = Collections.version

        // Recommended for best performance while adhering to the "single writer principle". Must be static-final
        private val mapREF = AtomicReferenceFieldUpdater.newUpdater(
            LockFreeLongMap::class.java, LongMap::class.java, "hashMap"
        )
    }
}
