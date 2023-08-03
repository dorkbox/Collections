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
@file:Suppress("UNCHECKED_CAST")

package dorkbox.collections

import dorkbox.collections.Collections.allocateIterators
import dorkbox.collections.ObjectSet.Companion.tableSize
import java.lang.IllegalStateException
import java.util.*

/**
 * An unordered map where the keys are objects and values are ints. Null keys are not allowed. No allocation is done except when growing
 * the table size.
 *
 * This class performs fast contains and remove (typically O(1), worst case O(n) but that is rare in practice). Add may be
 * slightly slower, depending on hash collisions. Hashcodes are rehashed to reduce collisions and the need to resize. Load factors
 * greater than 0.91 greatly increase the chances to resize to the next higher POT size.
 *
 * Unordered sets and maps are not designed to provide especially fast iteration. Iteration is faster with OrderedSet and
 * OrderedMap.
 *
 * This implementation uses linear probing with the backward shift algorithm for removal. Hashcodes are rehashed using Fibonacci
 * hashing, instead of the more common power-of-two mask, to better distribute poor hashCodes (see [Malte Skarupke's blog post](https://probablydance.com/2018/06/16/fibonacci-hashing-the-optimization-that-the-world-forgot-or-a-better-alternative-to-integer-modulo/)).
 *
 * Linear probing continues to work even when all hashCodes collide, just more slowly.
 *
 * @author dorkbox, llc
 * @author Nathan Sweet
 * @author Tommy Ettinger
 */
open class ObjectFloatMap<K: Any> : MutableMap<K, Float> {

    companion object {
        const val version = Collections.version

        // This is used to tell the difference between a legit NULL value in a map, and a non-existent value
        internal val dummy = Any()
    }

    protected var mapSize = 0

    var keyTable: Array<K?>
    var valueTable: FloatArray
    var loadFactor: Float
    var threshold: Int

    /**
     * Used by [.place] to bit shift the upper bits of a `long` into a usable range (>= 0 and <=
     * [.mask]). The shift can be negative, which is convenient to match the number of bits in mask: if mask is a 7-bit
     * number, a shift of -7 shifts the upper 7 bits into the lowest 7 positions. This class sets the shift &gt; 32 and &lt; 64,
     * which if used with an int will still move the upper bits of an int to the lower bits due to Java's implicit modulus on
     * shifts.
     *
     * [.mask] can also be used to mask the low bits of a number, which may be faster for some hashcodes, if
     * [.place] is overridden.
     */
    protected var shift: Int

    /**
     * A bitmask used to confine hashcodes to the size of the table. Must be all 1 bits in its low positions, ie a power of two
     * minus 1.
     * If [.place] is overridden, this can be used instead of [.shift] to isolate usable bits of a
     * hash.
     */
    protected var mask: Int

    @Transient
    var entries1: Entries<K>? = null

    @Transient
    var entries2: Entries<K>? = null

    @Transient
    var values1: Values? = null

    @Transient
    var values2: Values? = null

    @Transient
    var keys1: Keys<K>? = null

    @Transient
    var keys2: Keys<K>? = null

    /**
     * Creates a new map with the default capacity of 51 and loadfactor of 0.8
     */
    constructor() : this(51, 0.8f)

    /**
     * Creates a new map with the specified initial capacity and load factor. This map will hold initialCapacity items before
     * growing the backing table.
     *
     * @param initialCapacity The backing array size is initialCapacity / loadFactor, increased to the next power of two.
     * @param loadFactor The loadfactor used to determine backing array growth
     */
    constructor(initialCapacity: Int = 51, loadFactor: Float = 0.8f) {
        require(!(loadFactor <= 0f || loadFactor >= 1f)) { "loadFactor must be > 0 and < 1: $loadFactor" }

        this.loadFactor = loadFactor
        val tableSize = tableSize(initialCapacity, loadFactor)

        threshold = (tableSize * loadFactor).toInt()
        mask = tableSize - 1
        shift = java.lang.Long.numberOfLeadingZeros(mask.toLong())
        keyTable = arrayOfNulls<Any>(tableSize) as Array<K?>
        valueTable = FloatArray(tableSize)
    }

    /**
     * Creates a new map identical to the specified map.
     */
    constructor(map: ObjectFloatMap<out K>) : this((map.keyTable.size * map.loadFactor).toInt(), map.loadFactor) {
        System.arraycopy(map.keyTable, 0, keyTable, 0, map.keyTable.size)
        System.arraycopy(map.valueTable, 0, valueTable, 0, map.valueTable.size)
        mapSize = map.mapSize
    }

    override val size: Int
        get() {
            return mapSize
        }

    /**
     * Returns an index >= 0 and <= [.mask] for the specified `item`.
     *
     * The default implementation uses Fibonacci hashing on the item's [Object.hashCode]: the hashcode is multiplied by a
     * long constant (2 to the 64th, divided by the golden ratio) then the uppermost bits are shifted into the lowest positions to
     * obtain an index in the desired range. Multiplication by a long may be slower than int (eg on GWT) but greatly improves
     * rehashing, allowing even very poor hashcodes, such as those that only differ in their upper bits, to be used without high
     * collision rates. Fibonacci hashing has increased collision rates when all or most hashcodes are multiples of larger
     * Fibonacci numbers (see [Malte Skarupke's blog post](https://probablydance.com/2018/06/16/fibonacci-hashing-the-optimization-that-the-world-forgot-or-a-better-alternative-to-integer-modulo/)).
     *
     *
     * This method can be overriden to customizing hashing. This may be useful eg in the unlikely event that most hashcodes are
     * Fibonacci numbers, if keys provide poor or incorrect hashcodes, or to simplify hashing if keys provide high quality
     * hashcodes and don't need Fibonacci hashing: `return item.hashCode() & mask;`
     */
    protected open fun place(item: Any): Int {
        return (item.hashCode() * -0x61c8864680b583ebL ushr shift).toInt()
    }

    /**
     * Returns the index of the key if already present, else -(index + 1) for the next empty index. This can be overridden in this
     * package to compare for equality differently than [Object.equals].
     */
    open fun locateKey(key: Any): Int {
        val keyTable = keyTable
        var i = place(key)
        while (true) {
            val other: K = keyTable[i] ?: return -(i + 1)
            // Empty space is available.
            if (other == key) return i // Same key was found.
            i = (i + 1) and mask
        }
    }

    /**
     * Returns the old value associated with the specified key, or null.
     */
    override fun put(key: K, value: Float): Float? {
        var i = locateKey(key)
        if (i >= 0) { // Existing key was found.
            val oldValue = valueTable[i]
            valueTable[i] = value
            return oldValue
        }
        i = -(i + 1) // Empty space was found.
        keyTable[i] = key
        valueTable[i] = value
        if (++mapSize >= threshold) resize(keyTable.size shl 1)
        return null
    }

    /**
     * Returns the old value associated with the specified key, or the specified default value.
     *
     * @param defaultValue [Float.NaN] can be used for a value unlikely to be in the map.
     */
    fun put(key: K, value: Float, defaultValue: Float): Float {
        var i = locateKey(key)
        if (i >= 0) { // Existing key was found.
            val oldValue = valueTable[i]
            valueTable[i] = value
            return oldValue
        }
        i = -(i + 1) // Empty space was found.
        keyTable[i] = key
        valueTable[i] = value
        if (++mapSize >= threshold) resize(keyTable.size shl 1)
        return defaultValue
    }

    open fun putAll(from: ObjectFloatMap<out K>) {
        ensureCapacity(from.mapSize)

        val keyTable = from.keyTable
        val valueTable = from.valueTable
        var key: K?
        var i = 0
        val n = keyTable.size
        while (i < n) {
            key = keyTable[i]
            if (key != null) {
                put(key, valueTable[i])
            }
            i++
        }
    }

    override fun putAll(from: Map<out K, Float>) {
        ensureCapacity(from.size)

        from.forEach { (k, v) ->
            put(k, v)
        }
    }

    /**
     * Skips checks for existing keys, doesn't increment size.
     */
    private fun putResize(key: K, value: Float) {
        val keyTable = keyTable
        var i = place(key)
        while (true) {
            if (keyTable[i] == null) {
                keyTable[i] = key
                valueTable[i] = value
                return
            }
            i = (i + 1) and mask
        }
    }

    /**
     * Returns the value for the specified key, or null if the key is not in the map.
     */
    override operator fun get(key: K): Float? {
        val i = locateKey(key)
        return if (i < 0) null else valueTable[i]
    }

    /**
     * Returns the value for the specified key, or the default value if the key is not in the map.
     *
     * @param defaultValue [Float.NaN] can be used for a value unlikely to be in the map.
     */
    operator fun get(key: K, defaultValue: Float?): Float? {
        val i = locateKey(key)
        return if (i < 0) {
            defaultValue
        } else {
            valueTable[i]
        }
    }

    /**
     * Returns the key's current value and increments the stored value. If the key is not in the map, defaultValue + increment is
     * put into the map and defaultValue is returned.
     */
    fun getAndIncrement(key: K, defaultValue: Float, increment: Float): Float {
        var i = locateKey(key)
        if (i >= 0) { // Existing key was found.
            val oldValue = valueTable[i]
            valueTable[i] += increment
            return oldValue
        }
        i = -(i + 1) // Empty space was found.
        keyTable[i] = key
        valueTable[i] = defaultValue + increment
        if (++mapSize >= threshold) resize(keyTable.size shl 1)
        return defaultValue
    }


    /**
     * Returns the value for the removed key, or null if the key is not in the map.
     */
    override fun remove(key: K): Float? {
        var i = locateKey(key)
        if (i < 0) return null

        val keyTable = keyTable
        val valueTable = valueTable

        val oldValue = valueTable[i]
        val mask = mask
        var next = (i + 1) and mask

        var k: K?
        while (keyTable[next].also { k = it } != null) {
            val placement = place(k!!)
            if ((next - placement and mask) > (i - placement and mask)) {
                keyTable[i] = k
                valueTable[i] = valueTable[next]
                i = next
            }
            next = (next + 1) and mask
        }

        keyTable[i] = null
        valueTable[i] = 0F
        mapSize--
        return oldValue
    }

    /**
     * Returns the value for the removed key, or the default value if the key is not in the map.
     *
     * @param defaultValue [Float.NaN] can be used for a value unlikely to be in the map.
     */
    fun remove(key: K, defaultValue: Float): Float {
        var i = locateKey(key)
        if (i < 0) return defaultValue

        val keyTable = keyTable
        val valueTable = valueTable
        val oldValue = valueTable[i]
        val mask = mask
        var next = (i + 1) and mask

        var k: K?
        while (keyTable[next].also { k = it } != null) {
            val placement = place(k!!)
            if ((next - placement and mask) > (i - placement and mask)) {
                keyTable[i] = k
                valueTable[i] = valueTable[next]
                i = next
            }
            next = (next + 1) and mask
        }
        keyTable[i] = null
        valueTable[i] = 0F
        mapSize--
        return oldValue
    }

    /**
     * Returns true if the map has one or more items.
     */
    fun notEmpty(): Boolean {
        return mapSize > 0
    }

    /**
     * Returns true if the map is empty.
     */
    override fun isEmpty(): Boolean {
        return mapSize == 0
    }

    /**
     * Reduces the size of the backing arrays to be the specified capacity / loadFactor, or less. If the capacity is already less,
     * nothing is done. If the map contains more items than the specified capacity, the next highest power of two capacity is used
     * instead.
     */
    open fun shrink(maximumCapacity: Int) {
        require(maximumCapacity >= 0) { "maximumCapacity must be >= 0: $maximumCapacity" }
        val tableSize = tableSize(maximumCapacity, loadFactor)
        if (keyTable.size > tableSize) resize(tableSize)
    }

    /**
     * Clears the map and reduces the size of the backing arrays to be the specified capacity / loadFactor, if they are larger.
     */
    open fun clear(maximumCapacity: Int) {
        val tableSize = tableSize(maximumCapacity, loadFactor)
        if (keyTable.size <= tableSize) {
            clear()
            return
        }
        mapSize = 0
        resize(tableSize)
    }

    override fun clear() {
        if (mapSize == 0) return
        mapSize = 0
        Arrays.fill(keyTable, null)
        Arrays.fill(valueTable, 0F)
    }

    /**
     * Returns true if the specified value is in the map. Note this traverses the entire map and compares every value, which may
     * be an expensive operation.
     */
    override fun containsValue(value: Float): Boolean {
        val valueTable = valueTable
        if (value == 0F) {
            val keyTable = keyTable
            for (i in valueTable.indices.reversed()) if (keyTable[i] != null && valueTable[i] == 0F) return true
        }
        else {
            for (i in valueTable.indices.reversed()) if (valueTable[i] == value) return true
        }

        return false
    }

    override fun containsKey(key: K): Boolean {
        return locateKey(key) >= 0
    }

    /**
     * Returns the key for the specified value, or null if it is not in the map. Note this traverses the entire map and compares
     * every value, which may be an expensive operation.
     *
     * @param identity If true, uses == to compare the specified value with values in the map. If false, uses
     * [.equals].
     */
    fun findKey(value: Any?, identity: Boolean): K? {
        val valueTable = valueTable
        if (value == null) {
            val keyTable = keyTable
            for (i in valueTable.indices.reversed()) if (keyTable[i] != null && valueTable[i] == 0F) return keyTable[i]
        }
        else if (identity) {
            for (i in valueTable.indices.reversed()) if (valueTable[i] == value) return keyTable[i]
        }
        else {
            for (i in valueTable.indices.reversed()) if (value == valueTable[i]) return keyTable[i]
        }
        return null
    }

    /**
     * Increases the size of the backing array to accommodate the specified number of additional items / loadFactor. Useful before
     * adding many items to avoid multiple backing array resizes.
     */
    fun ensureCapacity(additionalCapacity: Int) {
        val tableSize = tableSize(mapSize + additionalCapacity, loadFactor)
        if (keyTable.size < tableSize) resize(tableSize)
    }

    fun resize(newSize: Int) {
        val oldCapacity = keyTable.size
        threshold = (newSize * loadFactor).toInt()
        mask = newSize - 1
        shift = java.lang.Long.numberOfLeadingZeros(mask.toLong())

        val oldKeyTable = keyTable
        val oldValueTable = valueTable
        keyTable = arrayOfNulls<Any>(newSize) as Array<K?>
        valueTable = FloatArray(newSize)

        if (mapSize > 0) {
            for (i in 0 until oldCapacity) {
                val key = oldKeyTable[i]
                if (key != null) putResize(key, oldValueTable[i])
            }
        }
    }

    override fun hashCode(): Int {
        var h = mapSize
        val keyTable = keyTable
        val valueTable = valueTable
        var i = 0
        val n = keyTable.size
        while (i < n) {
            val key: K? = keyTable[i]
            if (key != null) {
                h += key.hashCode()
                val value = valueTable[i]
                if (value != 0F) h += value.hashCode()
            }
            i++
        }
        return h
    }

    override fun equals(other: Any?): Boolean {
        if (other === this) return true
        if (other !is ObjectFloatMap<*>) return false
        other as ObjectFloatMap<K>

        if (other.size != mapSize) return false
        val keyTable = keyTable
        val valueTable = valueTable

        var i = 0
        val n = keyTable.size
        while (i < n) {
            val key = keyTable[i]
            if (key != null) {
                val otherValue = other.get(key, 0F)
                if (otherValue == 0F && !other.containsKey(key)) return false
                if (otherValue != valueTable[i]) return false
            }
            i++
        }
        return true
    }


    fun toString(separator: String): String {
        return toString(separator, false)
    }

    override fun toString(): String {
        return toString(", ", true)
    }

    protected open fun toString(separator: String, braces: Boolean): String {
        if (mapSize == 0) return if (braces) "{}" else ""
        val buffer = StringBuilder(32)
        if (braces) buffer.append('{')

        val keyTable = keyTable
        val valueTable = valueTable

        var i = keyTable.size
        while (i-- > 0) {
            val key: K = keyTable[i] ?: continue
            buffer.append(key)
            buffer.append('=')
            buffer.append(valueTable[i])
            break
        }

        while (i-- > 0) {
            val key: K = keyTable[i] ?: continue
            buffer.append(separator)
            buffer.append(key)
            buffer.append('=')
            buffer.append(valueTable[i])
        }

        if (braces) buffer.append('}')
        return buffer.toString()
    }

    override val entries: MutableSet<MutableMap.MutableEntry<K, Float>>
        get() = entries() as MutableSet<MutableMap.MutableEntry<K, Float>>


    /**
     * Returns an iterator for the entries in the map. Remove is supported.
     *
     *
     * If [Collections.allocateIterators] is false, the same iterator instance is returned each time this method is called.
     *
     * Use the [Entries] constructor for nested or multithreaded iteration.
     */
    open fun entries(): Entries<K> {
        if (allocateIterators) return Entries(this)
        if (entries1 == null) {
            entries1 = Entries(this)
            entries2 = Entries(this)
        }
        if (!entries1!!.valid) {
            entries1!!.reset()
            entries1!!.valid = true
            entries2!!.valid = false
            return entries1 as Entries<K>
        }
        entries2!!.reset()
        entries2!!.valid = true
        entries1!!.valid = false
        return entries2 as Entries<K>
    }

    override val values: MutableCollection<Float>
        get() = values()

    /**
     * Returns an iterator for the values in the map. Remove is supported.
     *
     * If [Collections.allocateIterators] is false, the same iterator instance is returned each time this method is called.
     *
     * Use the [Values] constructor for nested or multithreaded iteration.
     */
    open fun values(): Values {
        if (allocateIterators) return Values(this)
        if (values1 == null) {
            values1 = Values(this)
            values2 = Values(this)
        }
        if (!values1!!.valid) {
            values1!!.reset()
            values1!!.valid = true
            values2!!.valid = false
            return values1 as Values
        }
        values2!!.reset()
        values2!!.valid = true
        values1!!.valid = false
        return values2 as Values
    }

    override val keys: MutableSet<K>
        get() = keys()

    /**
     * Returns an iterator for the keys in the map. Remove is supported.
     *
     * If [Collections.allocateIterators] is false, the same iterator instance is returned each time this method is called.
     *
     * Use the [Keys] constructor for nested or multithreaded iteration.
     */
    open fun keys(): Keys<K> {
        if (allocateIterators) return Keys(this)
        if (keys1 == null) {
            keys1 = Keys(this)
            keys2 = Keys(this)
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

    class Entry<K: Any>(val map: ObjectFloatMap<K>) : MutableMap.MutableEntry<K, Float> {
        override lateinit var key: K
        override var value: Float = 0F

        override fun setValue(newValue: Float): Float {
            val oldValue = value
            map[key] = newValue
            value = newValue
            return oldValue
        }

        override fun toString(): String {
            return "$key=$value"
        }
    }

    abstract class MapIterator<K: Any, V, I>(val map: ObjectFloatMap<K>) : Iterable<I>, MutableIterator<I> {
        var hasNext = false
        var nextIndex = 0
        var currentIndex = 0
        var valid = true

        init {
            @Suppress("LeakingThis")
            reset()
        }

        open fun reset() {
            currentIndex = -1
            nextIndex = -1
            findNextIndex()
        }

        fun findNextIndex() {
            val keyTable = map.keyTable
            val n = keyTable.size
            while (++nextIndex < n) {
                if (keyTable[nextIndex] != null) {
                    hasNext = true
                    return
                }
            }
            hasNext = false
        }

        override fun remove() {
            var i = currentIndex
            check(i >= 0) { "next must be called before remove." }

            val keyTable = map.keyTable
            val valueTable = map.valueTable

            val mask = map.mask
            var next = (i + 1) and mask

            var key: K?
            while (keyTable[next].also { key = it } != null) {
                val placement = map.place(key!!)
                if ((next - placement and mask) > (i - placement and mask)) {
                    keyTable[i] = key
                    valueTable[i] = valueTable[next]
                    i = next
                }
                next = (next + 1) and mask
            }
            keyTable[i] = null
            valueTable[i] = 0F
            map.mapSize--
            if (i != currentIndex) --nextIndex
            currentIndex = -1
        }
    }

    open class Entries<K: Any>(map: ObjectFloatMap<K>) : MutableSet<Entry<K>>, MapIterator<K, Int, Entry<K>>(map) {
        var entry = Entry<K>(map)

        /** Note the same entry instance is returned each time this method is called.  */
        override fun next(): Entry<K> {
            if (!hasNext) throw NoSuchElementException()
            if (!valid) throw RuntimeException("#iterator() cannot be used nested.")
            val keyTable = map.keyTable
            entry.key = keyTable[nextIndex]!!
            entry.value = map.valueTable[nextIndex]
            currentIndex = nextIndex
            findNextIndex()
            return entry
        }

        override fun hasNext(): Boolean {
            if (!valid) throw RuntimeException("#iterator() cannot be used nested.")
            return hasNext
        }

        override fun add(element: Entry<K>): Boolean {
            map.put(element.key, element.value)
            return true
        }

        override fun addAll(elements: Collection<Entry<K>>): Boolean {
            var added = false
            elements.forEach {
                map.put(it.key, it.value)
                added = true
            }

            return added
        }

        override val size: Int
            get() = map.mapSize

        override fun clear() {
            map.clear()
            reset()
        }

        override fun isEmpty(): Boolean {
            return map.isEmpty()
        }

        override fun containsAll(elements: Collection<Entry<K>>): Boolean {
            elements.forEach {(k,v) ->
                if (map.get(k) != v) {
                    return false
                }
            }
            return true
        }

        override fun contains(element: Entry<K>): Boolean {
           return (map.get(element.key) == element.value)
        }

        override fun iterator(): Entries<K> {
            return this
        }

        override fun retainAll(elements: Collection<Entry<K>>): Boolean {
            var removed = false
            map.keyTable.forEach { key ->
                if (key != null) {
                    val hasElement = elements.firstOrNull { it.key == key } != null
                    if (!hasElement) {
                        removed = map.remove(key) != null || removed
                    }
                }
            }
            reset()
            return removed
        }

        override fun removeAll(elements: Collection<Entry<K>>): Boolean {
            var removed = false
            elements.forEach { (k,_) ->
                removed = map.remove(k) != null || removed
            }
            reset()
            return removed
        }

        override fun remove(element: Entry<K>): Boolean {
            val removed = map.remove(entry.key) != null
            reset()
            return removed
        }
    }

    open class Values(map: ObjectFloatMap<*>) : MutableCollection<Float>, MapIterator<Any, Float, Float>(map as ObjectFloatMap<Any>) {
        override fun hasNext(): Boolean {
            if (!valid) throw RuntimeException("#iterator() cannot be used nested.")
            return hasNext
        }

        override fun next(): Float {
            if (!hasNext) throw NoSuchElementException()
            if (!valid) throw RuntimeException("#iterator() cannot be used nested.")
            val value = map.valueTable[nextIndex]
            currentIndex = nextIndex
            findNextIndex()
            return value
        }

        override val size: Int
            get() = map.mapSize

        override fun clear() {
            map.clear()
            reset()
        }

        override fun addAll(elements: Collection<Float>): Boolean {
            throw IllegalStateException("Cannot add values to a map without keys")
        }

        override fun add(element: Float): Boolean {
            throw IllegalStateException("Cannot add values to a map without keys")
        }

        override fun isEmpty(): Boolean {
            return map.isEmpty()
        }

        override fun containsAll(elements: Collection<Float>): Boolean {
            elements.forEach {
                if (!map.containsValue(it)) {
                    return false
                }
            }
            return true
        }

        override fun contains(element: Float): Boolean {
            return map.containsValue(element)
        }

        override fun iterator(): Values {
            return this
        }

        override fun retainAll(elements: Collection<Float>): Boolean {
            var removed = false
            map.keyTable.forEach { key ->
                if (key != null) {
                    val value = map[key]
                    if (!elements.contains(value)) {
                        map.remove(key)
                        removed = true
                    }
                }
            }
            reset()
            return removed
        }

        override fun removeAll(elements: Collection<Float>): Boolean {
            var removed = false
            elements.forEach {
                val key = map.findKey(it, false)
                if (key != null) {
                    removed = map.remove(key) != null || removed
                }
            }
            reset()
            return removed
        }

        override fun remove(element: Float): Boolean {
            var removed = false
            val key = map.findKey(element, false)
            if (key != null) {
                removed = map.remove(key) != null
            }
            reset()
            return removed
        }

        /** Returns a new array containing the remaining values.  */
        open fun toArray(): FloatArray {
            val array = FloatArray(map.size)
            var index = 0
            while (hasNext()) {
                array[index++] = next()
            }
            return array
        }

        /** Adds the remaining values to the specified array.  */
        fun toArray(array: FloatArray): FloatArray {
            var index = 0
            while (hasNext) {
                array[index++] = next()
            }
            return array
        }
    }

    open class Keys<K: Any>(map: ObjectFloatMap<K>) : MutableSet<K>, MapIterator<K, Any?, K>(map) {
        override fun hasNext(): Boolean {
            if (!valid) throw RuntimeException("#iterator() cannot be used nested.")
            return hasNext
        }

        override fun next(): K {
            if (!hasNext) throw NoSuchElementException()
            if (!valid) throw RuntimeException("#iterator() cannot be used nested.")
            val key = map.keyTable[nextIndex]
            currentIndex = nextIndex
            findNextIndex()
            return key!!
        }

        override fun add(element: K): Boolean {
            throw IllegalStateException("Cannot add keys to a map without values")
        }

        override fun addAll(elements: Collection<K>): Boolean {
            throw IllegalStateException("Cannot add keys to a map without values")
        }

        override val size: Int
            get() = map.mapSize

        override fun clear() {
            map.clear()
            reset()
        }

        override fun isEmpty(): Boolean {
            return map.isEmpty()
        }

        override fun containsAll(elements: Collection<K>): Boolean {
            elements.forEach {
                if (!map.containsKey(it)) {
                    return false
                }
            }
            return true
        }

        override fun contains(element: K): Boolean {
            return map.containsKey(element)
        }

        override fun iterator(): Keys<K> {
            return this
        }

        override fun retainAll(elements: Collection<K>): Boolean {
            var removed = false
            map.keyTable.forEach {
                if (it != null && !elements.contains(it)) {
                    map.remove(it)
                    removed = true
                }
            }
            reset()
            return removed
        }

        override fun removeAll(elements: Collection<K>): Boolean {
            var removed = false
            elements.forEach {
                if (map.remove(it) == null) {
                    removed = true
                }
            }
            reset()
            return removed
        }

        override fun remove(element: K): Boolean {
            val removed = map.remove(element) == null
            reset()
            return removed
        }

        /** Returns a new array containing the remaining keys.  */
        @Suppress("USELESS_CAST")
        open fun toArray(): Array<K> {
            return Array(map.mapSize) { next() as Any } as Array<K>
        }

        /** Adds the remaining keys to the array.  */
        fun <T: K> toArray(array: Array<T>): Array<T> {
            var index = 0
            while (hasNext) {
                array[index++] = next() as T
            }
            return array
        }
    }
}
