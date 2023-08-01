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
/*******************************************************************************
 * Copyright 2011 LibGDX.
 * Mario Zechner <badlogicgames></badlogicgames>@gmail.com>
 * Nathan Sweet <nathan.sweet></nathan.sweet>@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dorkbox.collections

import dorkbox.collections.Collections.allocateIterators
import java.util.Comparator


/**
 * An [ObjectMap] that also stores keys in an [ArrayList] using the insertion order. Null keys are not allowed. No
 * allocation is done except when growing the table size.
 *
 *
 * Iteration over the [.entries], [.keys], and [.values] is ordered and faster than an unordered map. Keys
 * can also be accessed and the order changed using [.orderedKeys]. There is some additional overhead for put and remove.
 * When used for faster iteration versus ObjectMap and the order does not actually matter, copying during remove can be greatly
 * reduced by setting [Array.ordered] to false for [OrderedMap.orderedKeys].
 *
 *
 * This class performs fast contains (typically O(1), worst case O(n) but that is rare in practice). Remove is somewhat slower due
 * to [.orderedKeys]. Add may be slightly slower, depending on hash collisions. Hashcodes are rehashed to reduce
 * collisions and the need to resize. Load factors greater than 0.91 greatly increase the chances to resize to the next higher POT
 * size.
 *
 *
 * Unordered sets and maps are not designed to provide especially fast iteration. Iteration is faster with OrderedSet and
 * OrderedMap.
 *
 *
 * This implementation uses linear probing with the backward shift algorithm for removal. Hashcodes are rehashed using Fibonacci
 * hashing, instead of the more common power-of-two mask, to better distribute poor hashCodes (see [Malte Skarupke's blog post](https://probablydance.com/2018/06/16/fibonacci-hashing-the-optimization-that-the-world-forgot-or-a-better-alternative-to-integer-modulo/)). Linear probing continues to work even when all hashCodes collide, just more slowly.
 *
 * @author Nathan Sweet
 * @author Tommy Ettinger
 */
class OrderedMap<K, V> : ObjectMap<K, V?> where K : Any, K : Comparable<K> {
    companion object {
        const val version = Collections.version
    }

    private val keys_: MutableList<K>

    /**
     * Creates a new map with an initial capacity of 51 and a load factor of 0.8.
     */
    constructor(): this(51, 0.8F)

    /**
     * Creates a new map with a load factor of 0.8.
     *
     * @param initialCapacity The backing array size is initialCapacity / loadFactor, increased to the next power of two.
     */
    constructor(initialCapacity: Int) : super(initialCapacity) {
        keys_ = ArrayList(initialCapacity)
    }

    /**
     *  Creates a new map with the specified initial capacity and load factor. This map will hold initialCapacity items before
     * growing the backing table.
     *
     * @param initialCapacity The backing array size is initialCapacity / loadFactor, increased to the next power of two.
     * @param loadFactor The loadfactor used to determine backing array growth
     */
    constructor(initialCapacity: Int, loadFactor: Float) : super(initialCapacity, loadFactor) {
        keys_ = ArrayList(initialCapacity)
    }

    /**
     * Creates a new map containing the items in the specified map.
     */
    constructor(map: OrderedMap<out K, out V>) : super(map) {
        keys_ = ArrayList(map.keys_)
    }

    /**
     * Sorts this array. The array elements must implement [Comparable]. This method is not thread safe.
     */
    fun sort() {
        keys_.sort()
    }

    /**
     * Sorts the array. This method is not thread safe.
     */
    fun sort(comparator: Comparator<in K>) {
        keys_.sortWith(comparator)
    }

    override fun put(key: K, value: V?): V? {
        var i = locateKey(key)
        if (i >= 0) { // Existing key was found.
            val oldValue = valueTable[i]
            valueTable[i] = value
            return oldValue
        }

        i = -(i + 1) // Empty space was found.
        keyTable[i] = key
        valueTable[i] = value
        keys_.add(key)
        if (++mapSize >= threshold) resize(keyTable.size shl 1)
        return null
    }

    fun putAll(map: OrderedMap<K, out V?>) {
        ensureCapacity(map.size)
        val keys = map.keys_
        var i = 0
        val n = map.keys_.size
        while (i < n) {
            val key = keys[i]
            put(key, map.get(key))
            i++
        }
    }

    override fun remove(key: K): V? {
        keys_.remove(key)
        return super.remove(key)
    }

    fun removeIndex(index: Int): V? {
        val itemAtIndex = keys_.removeAt(index)
        return super.remove(itemAtIndex)
    }

    /**
     * Changes the key `before` to `after` without changing its position in the order or its value. Returns true if
     * `after` has been added to the OrderedMap and `before` has been removed; returns false if `after` is
     * already present or `before` is not present. If you are iterating over an OrderedMap and have an index, you should
     * prefer [.alterIndex], which doesn't need to search for an index like this does and so can be faster.
     *
     * @param before a key that must be present for this to succeed
     * @param after a key that must not be in this map for this to succeed
     *
     * @return true if `before` was removed and `after` was added, false otherwise
     */
    fun alter(before: K, after: K): Boolean {
        if (containsKey(after)) return false
        val index = keys_.indexOf(before)
        if (index == -1) return false
        super.put(after, super.remove(before))
        keys_[index] = after
        return true
    }

    /**
     * Changes the key at the given `index` in the order to `after`, without changing the ordering of other entries or
     * any values. If `after` is already present, this returns false; it will also return false if `index` is invalid
     * for the size of this map. Otherwise, it returns true. Unlike [.alter], this operates in constant time.
     *
     * @param index the index in the order of the key to change; must be non-negative and less than [.size]
     * @param after the key that will replace the contents at `index`; this key must not be present for this to succeed
     *
     * @return true if `after` successfully replaced the key at `index`, false otherwise
     */
    fun alterIndex(index: Int, after: K): Boolean {
        if (index < 0 || index >= size || containsKey(after)) return false
        super.put(after, super.remove(keys_[index]))
        keys_[index] = after
        return true
    }

    override fun clear(maximumCapacity: Int) {
        keys_.clear()
        super.clear(maximumCapacity)
    }

    override fun clear() {
        keys_.clear()
        super.clear()
    }

    fun orderedKeys(): List<K> {
        return keys_
    }

    override fun iterator(): Entries<K, V?> {
        return entries()
    }

    /**
     * Returns an iterator for the entries in the map. Remove is supported.
     *
     * If [Collections.allocateIterators] is false, the same iterator instance is returned each time this method is called.
     * Use the [OrderedMapEntries] constructor for nested or multithreaded iteration.
     */
    @Suppress("UNCHECKED_CAST")
    override fun entries(): Entries<K, V?> {
        if (allocateIterators) return OrderedMapEntries(this as OrderedMap<K, V?>)
        if (entries1 == null) {
            entries1 = OrderedMapEntries(this as OrderedMap<K, V?>)
            entries2 = OrderedMapEntries(this as OrderedMap<K, V?>)
        }
        if (!entries1!!.valid) {
            entries1!!.reset()
            entries1!!.valid = true
            entries2!!.valid = false
            return entries1 as Entries<K, V?>
        }
        entries2!!.reset()
        entries2!!.valid = true
        entries1!!.valid = false
        return entries2 as Entries<K, V?>
    }

    /**
     * Returns an iterator for the values in the map. Remove is supported.
     *
     * If [Collections.allocateIterators] is false, the same iterator instance is returned each time this method is called.
     *
     * Use the [OrderedMapValues] constructor for nested or multithreaded iteration.
     */
    override fun values(): Values<V?> {
        if (allocateIterators) return OrderedMapValues(this as OrderedMap<K, V?>)
        if (values1 == null) {
            values1 = OrderedMapValues(this as OrderedMap<K, V?>)
            values2 = OrderedMapValues(this as OrderedMap<K, V?>)
        }
        if (!values1!!.valid) {
            values1!!.reset()
            values1!!.valid = true
            values2!!.valid = false
            return values1 as Values<V?>
        }
        values2!!.reset()
        values2!!.valid = true
        values1!!.valid = false
        return values2 as Values<V?>
    }

    /**
     * Returns an iterator for the keys in the map. Remove is supported.
     *
     * If [Collections.allocateIterators] is false, the same iterator instance is returned each time this method is called.
     *
     * Use the [OrderedMapKeys] constructor for nested or multithreaded iteration.
     */
    override fun keys(): Keys<K> {
        if (allocateIterators) return OrderedMapKeys(this)
        if (keys1 == null) {
            keys1 = OrderedMapKeys(this)
            keys2 = OrderedMapKeys(this)
        }
        if (!keys1!!.valid) {
            keys1!!.reset()
            keys1!!.valid = true
            keys2!!.valid = false
            return keys1 as Keys<K>
        }
        keys2!!.reset()
        keys2!!.valid = true
        keys1!!.valid = false
        return keys2 as Keys<K>
    }

    override fun toString(separator: String, braces: Boolean): String {
        if (size == 0) return if (braces) "{}" else ""

        val buffer = StringBuilder(32)
        if (braces) buffer.append('{')
        val keys = keys_

        var i = 0
        val n = keys.size
        while (i < n) {
            val key = keys[i]
            if (i > 0) buffer.append(separator)
            buffer.append(if (key === this) "(this)" else key)
            buffer.append('=')
            val value = get(key)
            buffer.append(if (value === this) "(this)" else value)
            i++
        }

        if (braces) buffer.append('}')
        return buffer.toString()
    }

    class OrderedMapEntries<K, V>(map: OrderedMap<K, V?>) : Entries<K, V?>(map) where K : Any, K : Comparable<K> {
        private val keys: MutableList<K>

        init {
            keys = map.keys_
        }

        override fun reset() {
            currentIndex = -1
            nextIndex = 0
            hasNext = map.size > 0
        }

        override fun next(): Entry<K, V?> {
            if (!hasNext) throw NoSuchElementException()
            if (!valid) throw RuntimeException("#iterator() cannot be used nested.")
            currentIndex = nextIndex
            entry.key = keys[nextIndex]
            entry.value = map.get(entry.key)
            nextIndex++
            hasNext = nextIndex < map.size
            return entry
        }

        override fun remove() {
            check(currentIndex >= 0) { "next must be called before remove." }
            map.remove(entry.key)
            nextIndex--
            currentIndex = -1
        }
    }

    class OrderedMapKeys<K>(map: OrderedMap<K, *>) : Keys<K>(map) where K : Any, K : Comparable<K> {
        private val keys: MutableList<K>

        init {
            keys = map.keys_
        }

        override fun reset() {
            currentIndex = -1
            nextIndex = 0
            hasNext = map.size > 0
        }

        override fun next(): K {
            if (!hasNext) throw NoSuchElementException()
            if (!valid) throw RuntimeException("#iterator() cannot be used nested.")
            val key = keys[nextIndex]
            currentIndex = nextIndex
            nextIndex++
            hasNext = nextIndex < map.size
            return key
        }

        override fun remove() {
            check(currentIndex >= 0) { "next must be called before remove." }
            (map as OrderedMap<*, *>).removeIndex(currentIndex)
            nextIndex = currentIndex
            currentIndex = -1
        }

        override fun toArray(): Array<K> {
            @Suppress("UNCHECKED_CAST")
            return Array(keys.size - nextIndex) { next() as Any } as Array<K>
        }
    }

    class OrderedMapValues<V>(map: OrderedMap<*, V?>) : Values<V?>(map) {
        private val keys: MutableList<*>

        init {
            keys = map.keys_
        }

        override fun reset() {
            currentIndex = -1
            nextIndex = 0
            hasNext = map.size > 0
        }

        override fun next(): V? {
            if (!hasNext) throw NoSuchElementException()
            if (!valid) throw RuntimeException("#iterator() cannot be used nested.")
            val value = map.get(keys.get(nextIndex)!!)
            currentIndex = nextIndex
            nextIndex++
            hasNext = nextIndex < map.size
            return value
        }

        override fun remove() {
            check(currentIndex >= 0) { "next must be called before remove." }
            (map as OrderedMap<*, *>).removeIndex(currentIndex)
            nextIndex = currentIndex
            currentIndex = -1
        }

        override fun toArray(): Array<V?> {
            @Suppress("UNCHECKED_CAST")
            return Array(keys.size - nextIndex) { next() as Any } as Array<V?>
        }
    }
}
