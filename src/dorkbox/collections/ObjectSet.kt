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

import java.util.*
import kotlin.math.ceil
import kotlin.math.max

/**
 * An unordered set where the keys are objects. Null keys are not allowed. No allocation is done except when growing the table
 * size.
 *
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
 * hashing, instead of the more common power-of-two mask, to better distribute poor hashCodes (see [Malte
 * Skarupke's blog post](https://probablydance.com/2018/06/16/fibonacci-hashing-the-optimization-that-the-world-forgot-or-a-better-alternative-to-integer-modulo/)). Linear probing continues to work even when all hashCodes collide, just more slowly.
 * @author Nathan Sweet
 * @author Tommy Ettinger
 */
open class ObjectSet<T: Any> : MutableSet<T> {
    companion object {
        const val version = Collections.version

        fun <T: Any> with(vararg array: T): ObjectSet<T> {
            val set = ObjectSet<T>()
            set.addAll(*array)
            return set
        }

        fun tableSize(capacity: Int, loadFactor: Float): Int {
            require(capacity >= 0) { "capacity must be >= 0: $capacity" }
            val tableSize: Int = Collections.nextPowerOfTwo(
                max(
                    2.0, ceil((capacity / loadFactor).toDouble()).toInt().toDouble()
                ).toInt()
            )
            require(tableSize <= 1 shl 30) { "The required capacity is too large: $capacity" }
            return tableSize
        }
    }

    override var size = 0

    var keyTable: Array<T?>
    var loadFactor: Float
    var threshold: Int

    /**
     * Used by [.place] to bit shift the upper bits of a `long` into a usable range (>= 0 and <=
     * [.mask]). The shift can be negative, which is convenient to match the number of bits in mask: if mask is a 7-bit
     * number, a shift of -7 shifts the upper 7 bits into the lowest 7 positions. This class sets the shift > 32 and < 64,
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
    private var iterator1: ObjectSetIterator<T>? = null

    @Transient
    private var iterator2: ObjectSetIterator<T>? = null

    /**
     * Creates a new set with the default capacity of 51 and loadfactor of 0.89
     */
    constructor(): this(51, 0.8f)

    /**
     * Creates a new set with the specified initial capacity and load factor. This set will hold initialCapacity items before
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

        @Suppress("UNCHECKED_CAST")
        keyTable = arrayOfNulls<Any>(tableSize) as Array<T?>
    }

    /**
     * Creates a new set identical to the specified set.
     */
    constructor(set: ObjectSet<out T>) : this((set.keyTable.size * set.loadFactor).toInt(), set.loadFactor) {
        System.arraycopy(set.keyTable, 0, keyTable, 0, set.keyTable.size)
        @Suppress("LeakingThis")
        size = set.size
    }

    /**
     *  Returns an index >= 0 and <= [.mask] for the specified `item`.
     *
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
    protected fun place(item: T): Int {
        return (item.hashCode() * -0x61c8864680b583ebL ushr shift).toInt()
    }

    /**
     * Returns the index of the key if already present, else -(index + 1) for the next empty index. This can be overridden in this
     * pacakge to compare for equality differently than [Object.equals].
     */
    fun locateKey(key: T?): Int {
        requireNotNull(key) { "key cannot be null." }
        val keyTable = keyTable
        var i = place(key)
        while (true) {
            val other: T = keyTable[i] ?: return -(i + 1)
            // Empty space is available.
            if (other == key) return i // Same key was found.
            i = i + 1 and mask
        }
    }

    override fun addAll(elements: Collection<T>): Boolean {
        var added = false
        elements.forEach {
            added = added || add(it)
        }
        return added
    }

    /**
     * Returns true if the key was added to the set or false if it was already in the set. If this set already contains the key,
     * the call leaves the set unchanged and returns false.
     */
    override fun add(element: T): Boolean {
        var i = locateKey(element)
        if (i >= 0) return false // Existing key was found.
        i = -(i + 1) // Empty space was found.
        keyTable[i] = element
        if (++size >= threshold) resize(keyTable.size shl 1)
        return true
    }

    fun addAll(array: Array<out T>) {
        addAll(array, 0, array.size)
    }

    fun addAll(array: Array<out T>, offset: Int, length: Int) {
        require(offset + length <= array.size) { "offset + length must be <= size: " + offset + " + " + length + " <= " + array.size }
        addAll(array, offset, length)
    }

    @Suppress("UNCHECKED_CAST")
    fun addAll(vararg array: T): Boolean {
        return addAll(array as Array<T>, 0, array.size)
    }

    fun addAll(array: Array<T>, offset: Int, length: Int): Boolean {
        ensureCapacity(length)
        val oldSize = size
        var i = offset
        val n = i + length
        while (i < n) {
            add(array[i])
            i++
        }
        return oldSize != size
    }

    fun addAll(set: ObjectSet<T>) {
        ensureCapacity(set.size)
        val keyTable = set.keyTable
        var i = 0
        val n = keyTable.size
        while (i < n) {
            val key: T? = keyTable[i]
            key?.let { add(it) }
            i++
        }
    }

    /**
     * Skips checks for existing keys, doesn't increment size.
     */
    private fun addResize(key: T) {
        val keyTable = keyTable
        var i = place(key)
        while (true) {
            if (keyTable[i] == null) {
                keyTable[i] = key
                return
            }
            i = (i + 1) and mask
        }
    }

    override fun retainAll(elements: Collection<T>): Boolean {
        var removed = false
        keyTable.forEach { key ->
            if (key != null) {
                if (!elements.contains(key)) {
                    removed = remove(key) || removed
                }
            }
        }
        return removed
    }

    override fun removeAll(elements: Collection<T>): Boolean {
        var removed = false
        elements.forEach {
            if (remove(it)) {
                removed = true
            }
        }
        return removed
    }

    /**
     * Returns true if the key was removed.
     */
     override fun remove(element: T): Boolean {
        var i = locateKey(element)
        if (i < 0) return false

        val keyTable = keyTable
        val mask = mask
        var next = (i + 1) and mask

        var k: T?
        while (keyTable[next].also { k = it } != null) {
            val placement = place(k!!)
            if ((next - placement and mask) > (i - placement and mask)) {
                keyTable[i] = k
                i = next
            }
            next = (next + 1) and mask
        }
        keyTable[i] = null
        size--
        return true
    }

    /**
     * Returns true if the set has one or more items.
     */
    fun notEmpty(): Boolean {
        return size > 0
    }

    /**
     * Returns true if the set is empty.
     */
    override fun isEmpty(): Boolean {
        return size == 0
    }

    /**
     * Reduces the size of the backing arrays to be the specified capacity / loadFactor, or less. If the capacity is already less,
     * nothing is done. If the set contains more items than the specified capacity, the next highest power of two capacity is used
     * instead.
     */
    fun shrink(maximumCapacity: Int) {
        require(maximumCapacity >= 0) { "maximumCapacity must be >= 0: $maximumCapacity" }
        val tableSize = tableSize(maximumCapacity, loadFactor)
        if (keyTable.size > tableSize) resize(tableSize)
    }

    /**
     * Clears the set and reduces the size of the backing arrays to be the specified capacity / loadFactor, if they are larger.
     * The reduction is done by allocating new arrays, though for large arrays this can be faster than clearing the existing
     * array.
     */
    open fun clear(maximumCapacity: Int) {
        val tableSize = tableSize(maximumCapacity, loadFactor)
        if (keyTable.size <= tableSize) {
            clear()
            return
        }
        size = 0
        resize(tableSize)
    }

    /**
     * Clears the set, leaving the backing arrays at the current capacity. When the capacity is high and the population is low,
     * iteration can be unnecessarily slow. [.clear] can be used to reduce the capacity.
     */
    override fun clear() {
        if (size == 0) return
        size = 0
        Arrays.fill(keyTable, null)
    }

    override fun containsAll(elements: Collection<T>): Boolean {
        elements.forEach {
            if (!contains(it)) {
                return false
            }
        }
        return true
    }

    override operator fun contains(element: T): Boolean {
        return locateKey(element) >= 0
    }

    operator fun get(key: T): T? {
        val i = locateKey(key)
        return if (i < 0) null else keyTable[i]
    }

    fun first(): T? {
        val keyTable = keyTable
        var i = 0
        val n = keyTable.size
        while (i < n) {
            if (keyTable[i] != null) return keyTable[i]
            i++
        }
        throw IllegalStateException("ObjectSet is empty.")
    }

    /**
     * Increases the size of the backing array to accommodate the specified number of additional items / loadFactor. Useful before
     * adding many items to avoid multiple backing array resizes.
     */
    fun ensureCapacity(additionalCapacity: Int) {
        val tableSize = tableSize(size + additionalCapacity, loadFactor)
        if (keyTable.size < tableSize) resize(tableSize)
    }

    @Suppress("UNCHECKED_CAST")
    private fun resize(newSize: Int) {
        val oldCapacity = keyTable.size
        threshold = (newSize * loadFactor).toInt()
        mask = newSize - 1
        shift = java.lang.Long.numberOfLeadingZeros(mask.toLong())

        val oldKeyTable = keyTable
        keyTable = arrayOfNulls<Any>(newSize) as Array<T?>
        if (size > 0) {
            for (i in 0 until oldCapacity) {
                val key = oldKeyTable[i]
                key?.let { addResize(it) }
            }
        }
    }

    override fun hashCode(): Int {
        var h = size
        val keyTable = keyTable
        var i = 0
        val n = keyTable.size
        while (i < n) {
            val key: T? = keyTable[i]
            if (key != null) h += key.hashCode()
            i++
        }
        return h
    }

    @Suppress("UNCHECKED_CAST")
    override fun equals(other: Any?): Boolean {
        if (other !is ObjectSet<*>) return false
        other as ObjectSet<T>
        if (other.size != size) return false

        val keyTable = keyTable
        var i = 0
        val n = keyTable.size
        while (i < n) {
            if (keyTable[i] != null && !other.contains(keyTable[i])) return false
            i++
        }
        return true
    }

    override fun toString(): String {
        return '{'.toString() + toString(", ") + '}'
    }

    open fun toString(separator: String): String {
        if (size == 0) return ""
        val buffer = StringBuilder(32)
        val keyTable = keyTable
        var i = keyTable.size
        while (i-- > 0) {
            val key: T = keyTable[i] ?: continue
            buffer.append(if (key === this) "(this)" else key)
            break
        }
        while (i-- > 0) {
            val key: T = keyTable[i] ?: continue
            buffer.append(separator)
            buffer.append(if (key === this) "(this)" else key)
        }
        return buffer.toString()
    }

    /**
     * Returns an iterator for the keys in the set. Remove is supported.
     *
     * If [Collections.allocateIterators] is false, the same iterator instance is returned each time this method is called.
     * Use the [ObjectSetIterator] constructor for nested or multithreaded iteration.
     */
    override fun iterator(): ObjectSetIterator<T> {
        if (Collections.allocateIterators) return ObjectSetIterator(this)
        if (iterator1 == null) {
            iterator1 = ObjectSetIterator(this)
            iterator2 = ObjectSetIterator(this)
        }
        if (!iterator1!!.valid) {
            iterator1!!.reset()
            iterator1!!.valid = true
            iterator2!!.valid = false
            return iterator1 as ObjectSetIterator<T>
        }
        iterator2!!.reset()
        iterator2!!.valid = true
        iterator1!!.valid = false
        return iterator2 as ObjectSetIterator<T>
    }

    open class ObjectSetIterator<K: Any>(val set: ObjectSet<K>) : Iterable<K>, MutableIterator<K> {
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

        private fun findNextIndex() {
            val keyTable = set.keyTable
            val n = set.keyTable.size
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
            val keyTable = set.keyTable
            val mask = set.mask
            var next = i + 1 and mask
            var key: K?
            while (keyTable[next].also { key = it } != null) {
                val placement = set.place(key!!)
                if ((next - placement and mask) > (i - placement and mask)) {
                    keyTable[i] = key
                    i = next
                }
                next = next + 1 and mask
            }
            keyTable[i] = null
            set.size--
            if (i != currentIndex) --nextIndex
            currentIndex = -1
        }

        override fun hasNext(): Boolean {
            if (!valid) throw RuntimeException("#iterator() cannot be used nested.")
            return hasNext
        }

        override fun next(): K {
            if (!hasNext) throw NoSuchElementException()
            if (!valid) throw RuntimeException("#iterator() cannot be used nested.")
            val key = set.keyTable[nextIndex]
            currentIndex = nextIndex
            findNextIndex()
            return key!!
        }

        override fun iterator(): ObjectSetIterator<K> {
            return this
        }

        /**
         * Returns a new array containing the remaining values.
         */
        open fun toArray(): Array<K> {
            @Suppress("UNCHECKED_CAST")
            return Array<Any>(set.size) { next() } as Array<K>
        }

        /**
         * Adds the remaining values to the array.
         */
        open fun toArray(array: Array<K>): Array<K> {
            var i = 0
            while(hasNext) {
                array[i++] = next()
            }
            return array
        }
    }
}
