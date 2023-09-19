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
 */
class LockFreeIntStringMap : MutableMap<Int, String?>, Cloneable, Serializable {

    @Volatile
    private var hashMap: IntMap<String?>


    // synchronized is used here to ensure the "single writer principle", and make sure that ONLY one thread at a time can enter this
    // section. Because of this, we can have unlimited reader threads all going at the same time, without contention (which is our
    // use-case 99% of the time)

    /**
     * Constructs an empty <tt>HashMap</tt> with the default initial capacity
     * (16) and the default load factor (0.75).
     */
    constructor() {
        hashMap = IntMap()
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
        hashMap = IntMap(initialCapacity)
    }

    /**
     * Constructs a new <tt>HashMap</tt> with the same mappings as the
     * specified <tt>Map</tt>.  The <tt>HashMap</tt> is created with
     * default load factor (0.75) and an initial capacity sufficient to
     * hold the mappings in the specified <tt>Map</tt>.
     *
     * @param map the map whose mappings are to be placed in this map
     *
     * @throws NullPointerException if the specified map is null
     */
    constructor(map: Map<Int, String?>) {
        hashMap = IntMap(map.size)
        map.forEach { (index, key) ->
            hashMap.put(index, key)
        }
    }

    constructor(map: LockFreeIntStringMap) {
        hashMap = IntMap(map.hashMap)
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
        hashMap = IntMap(initialCapacity, loadFactor)
    }

    val map: IntMap<String>
    get() {
        @Suppress("UNCHECKED_CAST")
        return mapREF[this] as IntMap<String>
    }


    override val size: Int
        get() {
            // use the SWP to get a lock-free get of the value
            return mapREF[this].size
        }

    override val keys: MutableSet<Int>
        get() {
            return map.keys()
        }

    override val values: MutableCollection<String?>
        get() {
            @Suppress("UNCHECKED_CAST")
            return map.values() as MutableCollection<String?>
        }

    override val entries: MutableSet<MutableMap.MutableEntry<Int, String?>>
        get() {
            @Suppress("UNCHECKED_CAST")
            return map.entries() as MutableSet<MutableMap.MutableEntry<Int, String?>>
        }

    override fun isEmpty(): Boolean {
        // use the SWP to get a lock-free get of the value
        return mapREF[this].isEmpty()
    }

    override fun containsKey(key: Int): Boolean {
        // use the SWP to get a lock-free get of the value
        return mapREF[this].containsKey(key)
    }

    override fun containsValue(value: String?): Boolean {
        // use the SWP to get a lock-free get of the value
        return mapREF[this].containsValue(value)
    }

    override operator fun get(key: Int): String? {
        return mapREF[this][key] as String?
    }

    @Synchronized
    override fun put(key: Int, value: String?): String? {
        return hashMap.put(key, value)
    }

    @Synchronized
    override fun putAll(from: Map<out Int, String?>) {
        hashMap.putAll(from)
    }

    @Synchronized
    override fun remove(key: Int): String? {
        return hashMap.remove(key)
    }

    @Synchronized
    fun removeAllValues(value: String) {
        val iterator = hashMap.entries().iterator()
        while (iterator.hasNext()) {
            val value1 = iterator.next()
            if (value1.value == value) {
                iterator.remove()
            }
        }
    }

    @Synchronized
    fun replaceAll(hashMap: IntMap<String?>) {
        this.hashMap.clear()
        this.hashMap.putAll(hashMap)
    }

    @Synchronized
    override fun clear() {
        hashMap.clear()
    }

    override fun equals(other: Any?): Boolean {
        return (mapREF[this] == other)
    }

    override fun hashCode(): Int {
        return mapREF[this].hashCode()
    }

    override fun toString(): String {
        return mapREF[this].toString()
    }


    /**
     * Return a non-thread-safe copy of the backing map
     */
    fun toMap(): IntMap<String?> {
        return IntMap(mapREF[this] as IntMap<String?>)
    }

    // this must be at the end of the file!
    companion object {
        const val version = Collections.version

        // Recommended for best performance while adhering to the "single writer principle". Must be static-final
        private val mapREF = AtomicReferenceFieldUpdater.newUpdater(
            LockFreeIntStringMap::class.java, IntMap::class.java, "hashMap"
        )
    }
}
