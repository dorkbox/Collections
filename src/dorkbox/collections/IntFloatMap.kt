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
import dorkbox.collections.ObjectSet.Companion.tableSize
import java.util.*

/**
 * An unordered map where the keys are unboxed ints and values are floats. No allocation is done except when growing the table
 * size.
 *
 * This class performs fast contains and remove (typically O(1), worst case O(n) but that is rare in practice). Add may be
 * slightly slower, depending on hash collisions. Hashcodes are rehashed to reduce collisions and the need to resize. Load factors
 * greater than 0.91 greatly increase the chances to resize to the next higher POT size.
 *
 *
 * Unordered sets and maps are not designed to provide especially fast iteration. Iteration is faster with OrderedSet and
 * OrderedMap.
 *
 *
 * This implementation uses linear probing with the backward shift algorithm for removal. Hashcodes are rehashed using Fibonacci
 * hashing, instead of the more common power-of-two mask, to better distribute poor hashCodes (see [Malte Skarupke's blog post](https://probablydance.com/2018/06/16/fibonacci-hashing-the-optimization-that-the-world-forgot-or-a-better-alternative-to-integer-modulo/)). Linear probing continues to work even when all hashCodes collide, just more slowly.
 * @author Nathan Sweet
 * @author Tommy Ettinger
 */
class IntFloatMap : MutableMap<Int, Float>, MutableIterable<IntFloatMap.Entry> {
    companion object {
        const val version = Collections.version
    }

    private var size_ = 0
    var keyTable: IntArray
    var valueTable: FloatArray
    var zeroValue = 0F
    var hasZeroValue = false
    private val loadFactor: Float
    private var threshold: Int

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
     * minus 1. If [.place] is overriden, this can be used instead of [.shift] to isolate usable bits of a
     * hash.
     */
    protected var mask: Int

    @Transient
    private var entries1: Entries? = null

    @Transient
    private var entries2: Entries? = null

    @Transient
    private var values1: Values? = null

    @Transient
    private var values2: Values? = null

    @Transient
    private var keys1: Keys? = null

    @Transient
    private var keys2: Keys? = null

    /** Creates a new map with an initial capacity of 51 and a load factor of 0.8.  */
    constructor() : this(51, 0.8f)

    /**
     *  Creates a new map with the specified initial capacity and load factor. This map will hold initialCapacity items before
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

        keyTable = IntArray(tableSize)
        valueTable = FloatArray(tableSize)
    }

    /**
     * Creates a new map identical to the specified map.
     */
    constructor(map: IntFloatMap) : this((map.keyTable.size * map.loadFactor).toInt(), map.loadFactor) {
        System.arraycopy(map.keyTable, 0, keyTable, 0, map.keyTable.size)
        System.arraycopy(map.valueTable, 0, valueTable, 0, map.valueTable.size)
        size_ = map.size_
        zeroValue = map.zeroValue
        hasZeroValue = map.hasZeroValue
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
     * hashcodes and don't need Fibonacci hashing: `return item.hashCode() & mask;`  */
    protected fun place(item: Int): Int {
        return (item * -0x61c8864680b583ebL ushr shift).toInt()
    }

    /**
     * Returns the index of the key if already present, else -(index + 1) for the next empty index. This can be overridden in this
     * pacakge to compare for equality differently than [Object.equals].
     */
    private fun locateKey(key: Int): Int {
        val keyTable = keyTable
        var i = place(key)
        while (true) {
            val other = keyTable[i]
            if (other == 0) return -(i + 1) // Empty space is available.
            if (other == key) return i // Same key was found.
            i = i + 1 and mask
        }
    }


    override fun put(key: Int, value: Float): Float? {
        if (key == 0) {
            val oldValue = zeroValue
            zeroValue = value
            if (!hasZeroValue) {
                hasZeroValue = true
                size_++
            }
            return oldValue
        }
        var i = locateKey(key)
        if (i >= 0) { // Existing key was found.
            val oldValue = valueTable[i]
            valueTable[i] = value
            return oldValue
        }
        i = -(i + 1) // Empty space was found.
        keyTable[i] = key
        valueTable[i] = value
        if (++size_ >= threshold) resize(keyTable.size shl 1)
        return null
    }

    fun putAll(map: IntFloatMap) {
        ensureCapacity(map.size_)
        if (map.hasZeroValue) {
            put(0, map.zeroValue)
        }

        val keyTable = map.keyTable
        val valueTable = map.valueTable
        var i = 0
        val n = keyTable.size
        while (i < n) {
            val key = keyTable[i]
            if (key != 0) put(key, valueTable[i])
            i++
        }
    }

    /**
     * Skips checks for existing keys, doesn't increment size, doesn't need to handle key 0.
     */
    private fun putResize(key: Int, value: Float) {
        val keyTable = keyTable
        var i = place(key)
        while (true) {
            if (keyTable[i] == 0) {
                keyTable[i] = key
                valueTable[i] = value
                return
            }
            i = i + 1 and mask
        }
    }

    override operator fun get(key: Int): Float? {
        if (key == 0) return if (hasZeroValue) zeroValue else null
        val i = locateKey(key)
        return if (i >= 0) valueTable[i] else null
    }

    operator fun get(key: Int, defaultValue: Float?): Float? {
        if (key == 0) return if (hasZeroValue) zeroValue else defaultValue
        val i = locateKey(key)
        return if (i >= 0) valueTable[i] else defaultValue
    }

    /**
     * Returns the value for the removed key, or null if the key is not in the map.
     */
    override fun remove(key: Int): Float? {
        if (key == 0) {
            if (!hasZeroValue) return null
            hasZeroValue = false
            val oldValue = zeroValue
            zeroValue = 0F
            size_--
            return oldValue
        }

        var i = locateKey(key)
        if (i < 0) return null
        val keyTable = keyTable
        val valueTable = valueTable
        val oldValue = valueTable[i]
        val mask = mask
        var next = (i + 1) and mask

        var k: Int
        while (keyTable[next].also { k = it } != 0) {
            val placement = place(k)
            if ((next - placement and mask) > (i - placement and mask)) {
                keyTable[i] = k
                valueTable[i] = valueTable[next]
                i = next
            }
            next = (next + 1) and mask
        }
        keyTable[i] = 0
        valueTable[i] = 0F
        size_--
        return oldValue
    }

    /**
     * Returns true if the map has one or more items.
     */
    fun notEmpty(): Boolean {
        return size_ > 0
    }

    /**
     *  Returns true if the map is empty.
     */
    override fun isEmpty(): Boolean {
        return size_ == 0
    }

    override fun putAll(from: Map<out Int, Float>) {
        ensureCapacity(from.size)
        from.entries.forEach { (k,v) ->
            put(k, v)
        }
    }

    /**
     * Reduces the size of the backing arrays to be the specified capacity / loadFactor, or less. If the capacity is already less,
     * nothing is done. If the map contains more items than the specified capacity, the next highest power of two capacity is used
     * instead.
     */
    fun shrink(maximumCapacity: Int) {
        require(maximumCapacity >= 0) { "maximumCapacity must be >= 0: $maximumCapacity" }
        val tableSize = tableSize(maximumCapacity, loadFactor)
        if (keyTable.size > tableSize) resize(tableSize)
    }

    /**
     * Clears the map and reduces the size of the backing arrays to be the specified capacity / loadFactor, if they are larger.
     * */
    fun clear(maximumCapacity: Int) {
        val tableSize = tableSize(maximumCapacity, loadFactor)
        if (keyTable.size <= tableSize) {
            clear()
            return
        }
        size_ = 0
        hasZeroValue = false
        zeroValue = 0F
        resize(tableSize)
    }

    @Suppress("UNCHECKED_CAST")
    override val entries: MutableSet<MutableMap.MutableEntry<Int, Float>>
        get() = entries() as MutableSet<MutableMap.MutableEntry<Int, Float>>
    override val keys: MutableSet<Int>
        get() = keys()
    override val size: Int
        get() = size_
    override val values: MutableCollection<Float>
        get() = values()

    override fun clear() {
        if (size_ == 0) return
        size_ = 0
        Arrays.fill(keyTable, 0)
        Arrays.fill(valueTable, 0F)
        zeroValue = 0F
        hasZeroValue = false
    }

    /**
     * Returns true if the specified value is in the map. Note this traverses the entire map and compares every value, which may
     * be an expensive operation.
     */
    override fun containsValue(value: Float): Boolean {
        val valueTable = valueTable
        if (value == 0F) {
            if (hasZeroValue && zeroValue == 0F) return true
            val keyTable = keyTable
            for (i in valueTable.indices.reversed()) if (keyTable[i] != 0 && valueTable[i] == 0F) return true
        }
        else {
            if (value == zeroValue) return true
            for (i in valueTable.indices.reversed()) if (valueTable[i] == value) return true
        }
        return false
    }

    override fun containsKey(key: Int): Boolean {
        return if (key == 0) hasZeroValue else locateKey(key) >= 0
    }

    /**
     * Returns the key for the specified value, or <tt>notFound</tt> if it is not in the map. Note this traverses the entire map
     * and compares every value, which may be an expensive operation.
     */
    fun findKey(value: Any?): Int? {
        val valueTable = valueTable
        if (value == null) {
            if (hasZeroValue && zeroValue == 0F) return 0
            val keyTable = keyTable
            for (i in valueTable.indices.reversed()) if (keyTable[i] != 0 && valueTable[i] == 0F) return keyTable[i]
        }
        else {
            if (value == zeroValue) return 0
            for (i in valueTable.indices.reversed()) if (valueTable[i] == value) return keyTable[i]
        }


        return null
    }

    /**
     * Returns the key for the specified value, or <tt>notFound</tt> if it is not in the map. Note this traverses the entire map
     * and compares every value, which may be an expensive operation.
     */
    fun findKey(value: Any?, notFound: Int): Int {
        return findKey(value) ?: notFound
    }

    /**
     * Increases the size of the backing array to accommodate the specified number of additional items / loadFactor. Useful before
     * adding many items to avoid multiple backing array resizes.
     */
    fun ensureCapacity(additionalCapacity: Int) {
        val tableSize = tableSize(size_ + additionalCapacity, loadFactor)
        if (keyTable.size < tableSize) resize(tableSize)
    }

    private fun resize(newSize: Int) {
        val oldCapacity = keyTable.size
        threshold = (newSize * loadFactor).toInt()
        mask = newSize - 1
        shift = java.lang.Long.numberOfLeadingZeros(mask.toLong())
        val oldKeyTable = keyTable
        val oldValueTable = valueTable
        keyTable = IntArray(newSize)
        valueTable = FloatArray(newSize)
        if (size_ > 0) {
            for (i in 0 until oldCapacity) {
                val key = oldKeyTable[i]
                if (key != 0) putResize(key, oldValueTable[i])
            }
        }
    }

    override fun hashCode(): Int {
        var h = size_
        if (hasZeroValue && zeroValue != 0F) h += zeroValue.hashCode()
        val keyTable = keyTable
        val valueTable = valueTable
        var i = 0
        val n = keyTable.size
        while (i < n) {
            val key = keyTable[i]
            if (key != 0) {
                h += key * 31
                val value = valueTable[i]
                if (value != 0F) {
                    h += value.hashCode()
                }
            }
            i++
        }
        return h
    }

    override fun equals(other: Any?): Boolean {
        if (other === this) return true
        if (other !is IntFloatMap) return false
        if (other.size_ != size_) return false
        if (other.hasZeroValue != hasZeroValue) return false
        if (hasZeroValue) {
            if (other.zeroValue == 0F) {
                if (zeroValue != 0F) return false
            }
            else {
                if (other.zeroValue != zeroValue) return false
            }
        }
        val keyTable = keyTable
        val valueTable = valueTable
        var i = 0
        val n = keyTable.size
        while (i < n) {
            val key = keyTable[i]
            if (key != 0) {
                val value = valueTable[i]
                if (value == 0F) {
                    if (other[key] != null) return false
                }
                else {
                    if (value != other[key]) return false
                }
            }
            i++
        }
        return true
    }


    override fun toString(): String {
        if (size_ == 0) return "[]"
        val buffer = StringBuilder(32)
        buffer.append('[')
        val keyTable = keyTable
        val valueTable = valueTable
        var i = keyTable.size
        if (hasZeroValue) {
            buffer.append("0=")
            buffer.append(zeroValue)
        }
        else {
            while (i-- > 0) {
                val key = keyTable[i]
                if (key == 0) continue
                buffer.append(key)
                buffer.append('=')
                buffer.append(valueTable[i])
                break
            }
        }
        while (i-- > 0) {
            val key = keyTable[i]
            if (key == 0) continue
            buffer.append(", ")
            buffer.append(key)
            buffer.append('=')
            buffer.append(valueTable[i])
        }
        buffer.append(']')
        return buffer.toString()
    }

    override fun iterator(): MutableIterator<Entry> {
        return entries()
    }

    /**
     * Returns an iterator for the entries in the map. Remove is supported.
     *
     * If [Collections.allocateIterators] is false, the same iterator instance is returned each time this method is called.
     *
     * Use the [Entries] constructor for nested or multithreaded iteration.
     */
    fun entries(): Entries {
        if (allocateIterators) return Entries(this)
        if (entries1 == null) {
            entries1 = Entries(this)
            entries2 = Entries(this)
        }
        if (!entries1!!.valid) {
            entries1!!.reset()
            entries1!!.valid = true
            entries2!!.valid = false
            return entries1!!
        }
        entries2!!.reset()
        entries2!!.valid = true
        entries1!!.valid = false
        return entries2!!
    }

    /**
     * Returns an iterator for the values in the map. Remove is supported.
     *
     * If [Collections.allocateIterators] is false, the same iterator instance is returned each time this method is called.
     * Use the [Entries] constructor for nested or multithreaded iteration.
     */
    fun values(): Values {
        if (allocateIterators) return Values(this)
        if (values1 == null) {
            values1 = Values(this)
            values2 = Values(this)
        }
        if (!values1!!.valid) {
            values1!!.reset()
            values1!!.valid = true
            values2!!.valid = false
            return values1!!
        }
        values2!!.reset()
        values2!!.valid = true
        values1!!.valid = false
        return values2!!
    }

    /**
     * Returns an iterator for the keys in the map. Remove is supported.
     *
     * If [Collections.allocateIterators] is false, the same iterator instance is returned each time this method is called.
     * Use the [Entries] constructor for nested or multithreaded iteration.
     */
    fun keys(): Keys {
        if (allocateIterators) return Keys(this)
        if (keys1 == null) {
            keys1 = Keys(this)
            keys2 = Keys(this)
        }
        if (!keys1!!.valid) {
            keys1!!.reset()
            keys1!!.valid = true
            keys2!!.valid = false
            return keys1!!
        }
        keys2!!.reset()
        keys2!!.valid = true
        keys1!!.valid = false
        return keys2!!
    }

    class Entry: MutableMap.MutableEntry<Int, Float> {
        override var key = 0

        override var value = 0F
        override fun setValue(newValue: Float): Float {
            val oldValue = value
            value = newValue
            return oldValue
        }

        override fun toString(): String {
            return "$key=$value"
        }
    }

    abstract class MapIterator<V, I>(map: IntFloatMap): Iterable<I>, MutableIterator<I>  {
        var hasNext = false
        val map: IntFloatMap
        var nextIndex = 0
        var currentIndex = 0
        var valid = true

        init {
            this.map = map
            reset()
        }

        fun reset() {
            currentIndex = INDEX_ILLEGAL
            nextIndex = INDEX_ZERO
            if (map.hasZeroValue) hasNext = true else findNextIndex()
        }

        fun findNextIndex() {
            val keyTable = map.keyTable
            val n = keyTable.size
            while (++nextIndex < n) {
                if (keyTable[nextIndex] != 0) {
                    hasNext = true
                    return
                }
            }
            hasNext = false
        }

        override fun remove() {
            var i = currentIndex
            if (i == INDEX_ZERO && map.hasZeroValue) {
                map.hasZeroValue = false
                map.zeroValue = 0F
            } else if (i < 0) {
                throw IllegalStateException("next must be called before remove.");
            } else {
                val keyTable = map.keyTable
                val valueTable = map.valueTable

                val mask = map.mask
                var next = (i + 1) and mask

                var key: Int
                while (keyTable[next].also { key = it } != 0) {
                    val placement = map.place(key)
                    if ((next - placement and mask) > (i - placement and mask)) {
                        keyTable[i] = key
                        valueTable[i] = valueTable[next]
                        i = next
                    }
                    next = (next + 1) and mask
                }
                keyTable[i] = 0
                valueTable[i] = 0F
                if (i != currentIndex) --nextIndex
                currentIndex = INDEX_ILLEGAL
                map.size_--
            }
        }

        companion object {
            private const val INDEX_ILLEGAL = -2
            const val INDEX_ZERO = -1
        }
    }

    class Entries(map: IntFloatMap) : MutableSet<Entry>, MapIterator<Int, Entry>(map) {
        private val entry = Entry()

        /** Note the same entry instance is returned each time this method is called.  */
        override fun next(): Entry {
            if (!hasNext) throw NoSuchElementException()
            if (!valid) throw RuntimeException("#iterator() cannot be used nested.")
            val keyTable = map.keyTable
            if (nextIndex == INDEX_ZERO) {
                entry.key = 0
                entry.value = map.zeroValue
            }
            else {
                entry.key = keyTable[nextIndex]
                entry.value = map.valueTable[nextIndex]
            }
            currentIndex = nextIndex
            findNextIndex()
            return entry
        }

        override fun hasNext(): Boolean {
            if (!valid) throw RuntimeException("#iterator() cannot be used nested.")
            return hasNext
        }

        override fun add(element: Entry): Boolean {
            map.put(element.key, element.value)
            return true
        }

        override fun addAll(elements: Collection<Entry>): Boolean {
            var added = false
            elements.forEach {
                map.put(it.key, it.value)
                added = true
            }

            return added
        }

        override val size: Int
            get() = map.size_

        override fun clear() {
            map.clear()
            reset()
        }

        override fun isEmpty(): Boolean {
            return map.isEmpty()
        }

        override fun containsAll(elements: Collection<Entry>): Boolean {
            elements.forEach {(k,v) ->
                if (map.get(k) != v) {
                    return false
                }
            }
            return true
        }

        override fun contains(element: Entry): Boolean {
            return (map.get(element.key) == element.value)
        }

        override fun iterator(): MutableIterator<Entry> {
            return this
        }

        override fun retainAll(elements: Collection<Entry>): Boolean {
            var removed = false

            // check zero value first
            if (map.hasZeroValue) {
                val hasElement = elements.firstOrNull { it.key == 0 } != null
                if (hasElement) {
                    removed = map.remove(0) != null
                }
            }

            // now check remaining entries

            map.keyTable.forEach { key ->
                if (key == 0) return@forEach
                val hasElement = elements.firstOrNull { it.key == key } != null
                if (!hasElement) {
                    removed = map.remove(key) != null || removed
                }
            }
            reset()
            return removed
        }

        override fun removeAll(elements: Collection<Entry>): Boolean {
            var removed = false
            elements.forEach { (k,_) ->
                removed = map.remove(k) != null || removed
            }
            reset()
            return removed
        }

        override fun remove(element: Entry): Boolean {
            val removed = map.remove(entry.key) != null
            reset()
            return removed
        }
    }

    class Values(map: IntFloatMap) : MutableCollection<Float>, MapIterator<Int, Float>(map) {
        override fun hasNext(): Boolean {
            if (!valid) throw RuntimeException("#iterator() cannot be used nested.")
            return hasNext
        }

        override fun next(): Float {
            if (!hasNext) throw NoSuchElementException()
            if (!valid) throw RuntimeException("#iterator() cannot be used nested.")
            val value = if (nextIndex == INDEX_ZERO) {
                map.zeroValue
            } else {
                map.valueTable[nextIndex]
            }
            currentIndex = nextIndex
            findNextIndex()
            return value
        }

        override val size: Int
            get() = map.size_

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

        override fun iterator(): MutableIterator<Float> {
            return this
        }

        override fun retainAll(elements: Collection<Float>): Boolean {
            var removed = false
            map.keyTable.forEach { key ->
                if (key != 0) {
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
                val key = map.findKey(it)
                if (key != null) {
                    removed = map.remove(key) != null || removed
                }
            }
            reset()
            return removed
        }

        override fun remove(element: Float): Boolean {
            var removed = false
            val key = map.findKey(element)
            if (key != null) {
                removed = map.remove(key) != null
            }
            reset()
            return removed
        }

        /**
         * Returns a new array containing the remaining values.
         */
        fun toArray(): FloatArray {
            val array = FloatArray(map.size)
            var index = 0
            while (hasNext()) {
                array[index++] = next()
            }
            return array
        }
    }

    class Keys(map: IntFloatMap) : MutableSet<Int>, MapIterator<Int, Int>(map) {
        override fun hasNext(): Boolean {
            if (!valid) throw RuntimeException("#iterator() cannot be used nested.")
            return hasNext
        }
        override operator fun next(): Int {
            if (!hasNext) throw NoSuchElementException()
            if (!valid) throw RuntimeException("#iterator() cannot be used nested.")
            val key = if (nextIndex == INDEX_ZERO) 0 else map.keyTable[nextIndex]
            currentIndex = nextIndex
            findNextIndex()
            return key
        }

        override val size: Int
            get() = map.size_

        override fun clear() {
            map.clear()
        }

        override fun addAll(elements: Collection<Int>): Boolean {
            var alreadyAdded = false
            elements.forEach {
                alreadyAdded = alreadyAdded || map.put(it, 0F) == null
            }

            return alreadyAdded
        }

        override fun add(element: Int): Boolean {
            return map.put(element, 0F) == null
        }

        override fun isEmpty(): Boolean {
            return map.size_ == 0
        }

        override fun containsAll(elements: Collection<Int>): Boolean {
            elements.forEach {
                if (!map.containsKey(it)) {
                    return false
                }
            }
            return true
        }

        override fun contains(element: Int): Boolean {
            return map.containsKey(element)
        }

        override fun iterator(): MutableIterator<Int> {
            return this
        }

        override fun retainAll(elements: Collection<Int>): Boolean {
            var removed = false
            map.keyTable.forEach {
                if (!elements.contains(it)) {
                    if (map.remove(it) == null) {
                        removed = true
                    }
                }
            }
            reset()
            return removed
        }

        override fun removeAll(elements: Collection<Int>): Boolean {
            var removed = false
            elements.forEach {
                if (map.remove(it) == null) {
                    removed = true
                }
            }
            reset()
            return removed
        }

        override fun remove(element: Int): Boolean {
            return map.remove(element) == null
        }

        /**
         * Returns a new array containing the remaining keys.
         */
        fun toArray(): IntArray {
            val array = IntArray(map.size)
            var index = 0
            while (hasNext()) {
                array[index++] = next()
            }
            return array
        }

        /**
         * Adds the remaining values to the specified array.
         */
        fun toArray(array: IntArray): IntArray {
            var index = 0
            while (hasNext) {
                array[index++] = next()
            }
            return array
        }
    }
}
