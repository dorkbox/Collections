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
 * An unordered set where the items are unboxed ints. No allocation is done except when growing the table size.
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
 *
 * @author Nathan Sweet
 * @author Tommy Ettinger
 */
class IntSet: MutableSet<Int> {
    companion object {
        const val version = Collections.version

        fun with(vararg array: Int): IntSet {
            val set = IntSet()
            set.addAll(array)
            return set
        }
    }

    private var size_ = 0
    var keyTable: IntArray
    var hasZeroValue = false
    private val loadFactor: Float
    private var threshold: Int

    /** Used by [.place] to bit shift the upper bits of a `long` into a usable range (>= 0 and <=
     * [.mask]). The shift can be negative, which is convenient to match the number of bits in mask: if mask is a 7-bit
     * number, a shift of -7 shifts the upper 7 bits into the lowest 7 positions. This class sets the shift &gt; 32 and &lt; 64,
     * which if used with an int will still move the upper bits of an int to the lower bits due to Java's implicit modulus on
     * shifts.
     *
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
    private var iterator1: IntSetIterator? = null

    @Transient
    private var iterator2: IntSetIterator? = null

    /**
     * Creates a new set with an initial capacity of 51 and a load factor of 0.8
     */
    constructor() : this(51,0.8f)

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
        keyTable = IntArray(tableSize)
    }

    /**
     * Creates a new set identical to the specified set.
     */
    constructor(set: IntSet) : this((set.keyTable.size * set.loadFactor).toInt(), set.loadFactor) {
        System.arraycopy(set.keyTable, 0, keyTable, 0, set.keyTable.size)
        size_ = set.size_
        hasZeroValue = set.hasZeroValue
    }

    override val size: Int
        get() = size_

    /**
     * Returns an index >= 0 and <= [.mask] for the specified `item`.
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

    /**
     * Returns true if the key was added to the set or false if it was already in the set.
     */
    override fun add(element: Int): Boolean {
        if (element == 0) {
            if (hasZeroValue) return false
            hasZeroValue = true
            size_++
            return true
        }

        var i = locateKey(element)
        if (i >= 0) return false // Existing key was found.
        i = -(i + 1) // Empty space was found.
        keyTable[i] = element
        if (++size_ >= threshold) resize(keyTable.size shl 1)
        return true
    }

    override fun addAll(elements: Collection<Int>): Boolean {
        var added = false
        elements.forEach {
            added = added || add(it)
        }

        return added
    }

    fun addAll(array: IntArray) {
        addAll(array, 0, array.size)
    }

    fun addAll(array: IntArray, offset: Int, length: Int) {
        require(offset + length <= array.size) { "offset + length must be <= size: $offset + $length <= ${array.size}" }
        ensureCapacity(length)

        var i = offset
        val n = i + length
        while (i < n) {
            add(array[i])
            i++
        }
    }

    fun addAll(set: IntSet) {
        ensureCapacity(set.size_)
        if (set.hasZeroValue) add(0)
        val keyTable = set.keyTable
        var i = 0
        val n = keyTable.size
        while (i < n) {
            val key = keyTable[i]
            if (key != 0) add(key)
            i++
        }
    }

    /**
     * Skips checks for existing keys, doesn't increment size, doesn't need to handle key 0.
     */
    private fun addResize(key: Int) {
        val keyTable = keyTable
        var i = place(key)
        while (true) {
            if (keyTable[i] == 0) {
                keyTable[i] = key
                return
            }
            i = (i + 1) and mask
        }
    }

    /**
     * Returns true if the key was removed.
     */
    override fun remove(element: Int): Boolean {
        if (element == 0) {
            if (!hasZeroValue) return false
            hasZeroValue = false
            size_--
            return true
        }

        var i = locateKey(element)
        if (i < 0) return false
        val keyTable = keyTable
        val mask = mask
        var next = (i + 1) and mask

        var k: Int
        while (keyTable[next].also { k = it } != 0) {
            val placement = place(k)
            if ((next - placement and mask) > (i - placement and mask)) {
                keyTable[i] = k
                i = next
            }
            next = (next + 1) and mask
        }
        keyTable[i] = 0
        size_--
        return true
    }

    /**
     * Returns true if the set has one or more items.
     */
    fun notEmpty(): Boolean {
        return size_ > 0
    }

    /**
     * Returns true if the set is empty.
     */
    override fun isEmpty(): Boolean {
        return size_ == 0
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
     */
    fun clear(maximumCapacity: Int) {
        val tableSize = tableSize(maximumCapacity, loadFactor)
        if (keyTable.size <= tableSize) {
            clear()
            return
        }
        size_ = 0
        hasZeroValue = false
        resize(tableSize)
    }

    override fun clear() {
        if (size_ == 0) return
        size_ = 0
        Arrays.fill(keyTable, 0)
        hasZeroValue = false
    }

    override fun containsAll(elements: Collection<Int>): Boolean {
        elements.forEach {
            if (!contains(it)) {
                return false
            }
        }

        return true
    }

    override operator fun contains(element: Int): Boolean {
        return if (element == 0) {
            hasZeroValue
        } else {
            locateKey(element) >= 0
        }
    }

    fun first(): Int {
        if (hasZeroValue) return 0
        val keyTable = keyTable
        var i = 0
        val n = keyTable.size
        while (i < n) {
            if (keyTable[i] != 0) return keyTable[i]
            i++
        }
        throw IllegalStateException("IntSet is empty.")
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
        keyTable = kotlin.IntArray(newSize)
        if (size_ > 0) {
            for (i in 0 until oldCapacity) {
                val key = oldKeyTable[i]
                if (key != 0) addResize(key)
            }
        }
    }

    override fun hashCode(): Int {
        var h = size_
        val keyTable = keyTable
        var i = 0
        val n = keyTable.size
        while (i < n) {
            val key = keyTable[i]
            if (key != 0) h += key
            i++
        }
        return h
    }

    override fun equals(other: Any?): Boolean {
        if (other !is IntSet) return false
        if (other.size_ != size_) return false
        if (other.hasZeroValue != hasZeroValue) return false
        val keyTable = keyTable
        var i = 0
        val n = keyTable.size
        while (i < n) {
            if (keyTable[i] != 0 && !other.contains(keyTable[i])) {
                return false
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
        var i = keyTable.size

        if (hasZeroValue) {
            buffer.append("0")
        }
        else {
            while (i-- > 0) {
                val key = keyTable[i]
                if (key == 0) continue
                buffer.append(key)
                break
            }
        }

        while (i-- > 0) {
            val key = keyTable[i]
            if (key == 0) continue
            buffer.append(", ")
            buffer.append(key)
        }
        buffer.append(']')
        return buffer.toString()
    }

    /**
     * Returns an iterator for the keys in the set. Remove is supported.
     *
     * If [Collections.allocateIterators] is false, the same iterator instance is returned each time this method is called.
     *
     * Use the [IntSetIterator] constructor for nested or multithreaded iteration.
     */
    override operator fun iterator(): IntSetIterator {
        if (allocateIterators) return IntSetIterator(this)
        if (iterator1 == null) {
            iterator1 = IntSetIterator(this)
            iterator2 = IntSetIterator(this)
        }
        if (!iterator1!!.valid) {
            iterator1!!.reset()
            iterator1!!.valid = true
            iterator2!!.valid = false
            return iterator1!!
        }
        iterator2!!.reset()
        iterator2!!.valid = true
        iterator1!!.valid = false
        return iterator2!!
    }

    override fun retainAll(elements: Collection<Int>): Boolean {
        var removed = false
        keyTable.forEach {
            if (!elements.contains(it)) {
                remove(it)
                removed = true
            }
        }
        return removed
    }

    override fun removeAll(elements: Collection<Int>): Boolean {
        var removed = false
        elements.forEach {
            removed = remove(it) || removed
        }

        return removed
    }

    class IntSetIterator(val set: IntSet): MutableIterator<Int> {
        var hasNext = false
        var nextIndex = 0
        var currentIndex = 0
        var valid = true

        init {
            reset()
        }

        fun reset() {
            currentIndex = INDEX_ILLEGAL
            nextIndex = INDEX_ZERO
            if (set.hasZeroValue) hasNext = true else findNextIndex()
        }

        fun findNextIndex() {
            val keyTable = set.keyTable
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
            if (i == INDEX_ZERO && set.hasZeroValue) {
                set.hasZeroValue = false
            } else if (i < 0) {
                throw IllegalStateException("next must be called before remove.")
            } else {
                val keyTable = set.keyTable
                val mask = set.mask
                var next = (i + 1) and mask
                var key: Int

                while (keyTable[next].also { key = it } != 0) {
                    val placement = set.place(key)
                    if ((next - placement and mask) > (i - placement and mask)) {
                        keyTable[i] = key
                        i = next
                    }
                    next = (next + 1) and mask
                }
                keyTable[i] = 0

                if (i != currentIndex) --nextIndex
                currentIndex = INDEX_ILLEGAL
                set.size_--
            }
        }

        override fun hasNext(): Boolean {
            return hasNext
        }

        override operator fun next(): Int {
            if (!hasNext) throw NoSuchElementException()
            if (!valid) throw RuntimeException("#iterator() cannot be used nested.")
            val key = if (nextIndex == INDEX_ZERO) {
                0
            } else {
                set.keyTable[nextIndex]
            }

            currentIndex = nextIndex
            findNextIndex()
            return key
        }

        /** Returns a new array containing the remaining keys.  */
        fun toArray(): Array<Int> {
            return Array(set.size_) { next() }
        }

        companion object {
            private const val INDEX_ILLEGAL = -2
            private const val INDEX_ZERO = -1
        }
    }
}
