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
import dorkbox.collections.Collections.random
import dorkbox.collections.ObjectMap.Companion.dummy
import dorkbox.collections.ObjectMap.Entry
import java.lang.IllegalStateException
import java.util.*
import kotlin.math.max
import kotlin.math.min

/**
 * An ordered or unordered map of objects. This implementation uses arrays to store the keys and values, which means
 * [gets][.getKey] do a comparison for each key in the map. This is slower than a typical hash map
 * implementation, but may be acceptable for small maps and has the benefits that keys and values can be accessed by index, which
 * makes iteration fast. If ordered is false, this class avoids a memory copy when removing elements (the last
 * element is moved to the removed element's position).
 *
 * @author Nathan Sweet
 */
class ArrayMap<K: Any, V> : MutableMap<K, V?>, MutableIterable<Entry<K, V?>> {
    companion object {
        const val version = Collections.version
    }

    var keyTable: Array<K?>
    var valueTable: Array<V?>
    var size_ = 0
    var ordered: Boolean

    @Transient
    private var entries1: Entries<K, V>? = null

    @Transient
    private var entries2: Entries<K, V>? = null

    @Transient
    private var values1: Values<V>? = null

    @Transient
    private var values2: Values<V>? = null

    @Transient
    private var keys1: Keys<K>? = null

    @Transient
    private var keys2: Keys<K>? = null

    /**
     * Creates an ordered map with the default capacity.
     */
    constructor() : this(true, 16)

    /**
     * Creates an ordered map with the specified capacity.
     */
    constructor(capacity: Int) : this(true, capacity)

    /**
     * Creates an ordered map
     *
     * @param ordered If false, methods that remove elements may change the order of other elements in the arrays, which avoids a
     * memory copy.
     * @param capacity Any elements added beyond this will cause the backing arrays to be grown.
     */
    @Suppress("UNCHECKED_CAST")
    constructor(ordered: Boolean = true, capacity: Int = 16) {
        this.ordered = ordered
        keyTable = arrayOfNulls<Any>(capacity) as Array<K?>
        valueTable = arrayOfNulls<Any>(capacity) as Array<V?>
    }

    /**
     * Creates a new map with [.keys] and [.values] of the specified type.
     *
     * @param ordered If false, methods that remove elements may change the order of other elements in the arrays, which avoids a
     * memory copy.
     * @param capacity Any elements added beyond this will cause the backing arrays to be grown.
     */
    constructor(ordered: Boolean, capacity: Int, keyArrayType: Class<K>, valueArrayType: Class<V>) {
        this.ordered = ordered

        @Suppress("UNCHECKED_CAST")
        keyTable = java.lang.reflect.Array.newInstance(keyArrayType, capacity) as Array<K?>

        @Suppress("UNCHECKED_CAST")
        valueTable = java.lang.reflect.Array.newInstance(valueArrayType, capacity) as Array<V?>
    }

    /**
     * Creates an ordered map with [.keys] and [.values] of the specified type and a capacity of 16.
     */
    constructor(keyArrayType: Class<K>, valueArrayType: Class<V>) : this(false, 16, keyArrayType, valueArrayType)

    /**
     * Creates a new map containing the elements in the specified map. The new map will have the same type of backing arrays and
     * will be ordered if the specified map is ordered. The capacity is set to the number of elements, so any subsequent elements
     * added will cause the backing arrays to be grown.
     */
    @Suppress("UNCHECKED_CAST")
    constructor(array: ArrayMap<K, V>) : this(
        array.ordered,
        array.size_,
        array.keyTable.javaClass.componentType as Class<K>,
        array.valueTable.javaClass.componentType as Class<V>
    ) {
        size_ = array.size_
        System.arraycopy(array.keyTable, 0, keyTable, 0, size_)
        System.arraycopy(array.valueTable, 0, valueTable, 0, size_)
    }

    override fun put(key: K, value: V?): V? {
        val oldValue = get(key)
        putIndex(key, value)
        return oldValue
    }

    /**
     * @return the index within the backing arrays where the item is put
     */
    fun putIndex(key: K, value: V?): Int {
        var index = indexOfKey(key)
        if (index == -1) {
            if (size_ == keyTable.size) resize(max(8.0, (size_ * 1.75f).toInt().toDouble()).toInt())
            index = size_++
        }

        keyTable[index] = key
        valueTable[index] = value
        return index
    }

    /**
     * @return the actual index within the backing arrays where the item is put
     */
    fun put(key: K, value: V, index: Int): Int {
        val existingIndex = indexOfKey(key)
        if (existingIndex != -1) {
            removeIndex(existingIndex)
        }
        else if (size_ == keyTable.size) {
            resize(max(8.0, (size_ * 1.75f).toInt().toDouble()).toInt())
        }

        System.arraycopy(keyTable, index, keyTable, index + 1, size_ - index)
        System.arraycopy(valueTable, index, valueTable, index + 1, size_ - index)

        keyTable[index] = key
        valueTable[index] = value
        size_++
        return index
    }

    override fun remove(key: K): V? {
        return removeKey(key)
    }

    override fun putAll(from: Map<out K, V?>) {
        from.forEach { (k,v) ->
            put(k, v)
        }
    }

    fun putAll(map: ArrayMap<out K, out V>, offset: Int = 0, length: Int = map.size_) {
        require(offset + length <= map.size_) { "offset + length must be <= size: $offset + $length <= ${map.size_}" }

        val sizeNeeded = size_ + length - offset
        if (sizeNeeded >= keyTable.size) {
            resize(max(8.0, (sizeNeeded * 1.75f).toInt().toDouble()).toInt())
        }

        System.arraycopy(map.keyTable, offset, keyTable, size_, length)
        System.arraycopy(map.valueTable, offset, valueTable, size_, length)
        size_ += length
    }

    /**
     * Returns the value (which may be null) for the specified key, or null if the key is not in the map. Note this does a
     * .equals() comparison of each key in reverse order until the specified key is found.
     */
    override operator fun get(key: K): V? {
        return get(key, null)
    }

    /**
     * Returns the value (which may be null) for the specified key, or the default value if the key is not in the map. Note this
     * does a .equals() comparison of each key in reverse order until the specified key is found.
     */
    operator fun get(key: K?, defaultValue: V?): V? {
        val keys = keyTable
        var i = size_ - 1
        if (key == null) {
            while (i >= 0) {
                if (keys[i] === key) return valueTable[i]
                i--
            }
        }
        else {
            while (i >= 0) {
                if (key == keys[i]) return valueTable[i]
                i--
            }
        }
        return defaultValue
    }

    /**
     * Returns the key for the specified value.
     * Note this does a comparison of each value in reverse order until the specified value is found.
     *
     * @param identity If true, == comparison will be used. If false, .equals() comparison will be used.
     */
    fun getKey(value: V?, identity: Boolean): K? {
        val values = valueTable
        var i = size_ - 1

        if (identity || value == null) {
            while (i >= 0) {
                if (values[i] === value) {
                    return keyTable[i]
                }
                i--
            }
        }
        else {
            while (i >= 0) {
                if (value == values[i]) {
                    return keyTable[i]
                }
                i--
            }
        }
        return null
    }

    fun getKeyAt(index: Int): K? {
        if (index >= size_) throw IndexOutOfBoundsException(index.toString())
        return keyTable[index]
    }

    fun getValueAt(index: Int): V? {
        if (index >= size_) throw IndexOutOfBoundsException(index.toString())
        return valueTable[index]
    }

    fun firstKey(): K? {
        check(size_ != 0) { "Map is empty." }
        return keyTable[0]
    }

    fun firstValue(): V? {
        check(size_ != 0) { "Map is empty." }
        return valueTable[0]
    }

    fun setKey(index: Int, key: K) {
        if (index >= size_) throw IndexOutOfBoundsException(index.toString())
        keyTable[index] = key
    }

    fun setValue(index: Int, value: V) {
        if (index >= size_) throw IndexOutOfBoundsException(index.toString())
        valueTable[index] = value
    }

    fun insert(index: Int, key: K, value: V) {
        if (index > size_) throw IndexOutOfBoundsException(index.toString())
        if (size_ == keyTable.size) resize(max(8.0, (size_ * 1.75f).toInt().toDouble()).toInt())
        if (ordered) {
            System.arraycopy(keyTable, index, keyTable, index + 1, size_ - index)
            System.arraycopy(valueTable, index, valueTable, index + 1, size_ - index)
        }
        else {
            keyTable[size_] = keyTable[index]
            valueTable[size_] = valueTable[index]
        }
        size_++
        keyTable[index] = key
        valueTable[index] = value
    }

    override fun containsKey(key: K): Boolean {
        return containsAnyKey(key)
    }

    fun containsNullKey(): Boolean {
        return containsAnyKey(null)
    }

    private fun containsAnyKey(key: K?): Boolean {
        val keys = keyTable
        var i = size_ - 1
        if (key == null) {
            while (i >= 0) if (key === keys[i--]) return true
        }
        else {
            while (i >= 0) if (key == keys[i--]) return true
        }
        return false
    }

    override fun containsValue(value: V?): Boolean {
        return containsValue(value, false)
    }

    /**
     * @param identity If true, == comparison will be used. If false, .equals() comparison will be used.
     */
    fun containsValue(value: V?, identity: Boolean): Boolean {
        val values = valueTable
        var i = size_ - 1
        if (identity || value == null) {
            while (i >= 0) if (values[i--] === value) return true
        }
        else {
            while (i >= 0) if (value == values[i--]) return true
        }
        return false
    }

    fun indexOfKey(key: K?): Int {
        val keys = keyTable
        if (key == null) {
            var i = 0
            val n = size_
            while (i < n) {
                if (keys[i] === key) return i
                i++
            }
        }
        else {
            var i = 0
            val n = size_
            while (i < n) {
                if (key == keys[i]) return i
                i++
            }
        }
        return -1
    }

    fun indexOfValue(value: V?, identity: Boolean): Int {
        val values = valueTable
        if (identity || value == null) {
            var i = 0
            val n = size_
            while (i < n) {
                if (values[i] === value) return i
                i++
            }
        }
        else {
            var i = 0
            val n = size_
            while (i < n) {
                if (value == values[i]) return i
                i++
            }
        }
        return -1
    }

    fun removeKey(key: K?): V? {
        val keys = keyTable
        if (key == null) {
            var i = 0
            val n = size_
            while (i < n) {
                if (keys[i] === key) {
                    val value = valueTable[i]
                    removeIndex(i)
                    return value
                }
                i++
            }
        }
        else {
            var i = 0
            val n = size_
            while (i < n) {
                if (key == keys[i]) {
                    val value = valueTable[i]
                    removeIndex(i)
                    return value
                }
                i++
            }
        }
        return null
    }

    fun removeValue(value: V?, identity: Boolean): Boolean {
        val values = valueTable
        if (identity || value == null) {
            var i = 0
            val n = size_
            while (i < n) {
                if (values[i] === value) {
                    removeIndex(i)
                    return true
                }
                i++
            }
        }
        else {
            var i = 0
            val n = size_
            while (i < n) {
                if (value == values[i]) {
                    removeIndex(i)
                    return true
                }
                i++
            }
        }
        return false
    }

    /** Removes and returns the key/values pair at the specified index.  */
    fun removeIndex(index: Int) {
        if (index >= size_) throw IndexOutOfBoundsException(index.toString())
        val keys = keyTable
        size_--
        if (ordered) {
            System.arraycopy(keys, index + 1, keys, index, size_ - index)
            System.arraycopy(valueTable, index + 1, valueTable, index, size_ - index)
        }
        else {
            keys[index] = keys[size_]
            valueTable[index] = valueTable[size_]
        }
        keys[size_] = null
        valueTable[size_] = null
    }

    /** Returns true if the map has one or more items.  */
    fun notEmpty(): Boolean {
        return size_ > 0
    }

    /** Returns true if the map is empty.  */
    override fun isEmpty(): Boolean {
        return size_ == 0
    }

    /** Returns the last key.  */
    fun peekKey(): K? {
        return keyTable[size_ - 1]
    }

    /** Returns the last value.  */
    fun peekValue(): V? {
        return valueTable[size_ - 1]
    }

    /** Clears the map and reduces the size of the backing arrays to be the specified capacity if they are larger.  */
    fun clear(maximumCapacity: Int) {
        if (keyTable.size <= maximumCapacity) {
            clear()
            return
        }
        size_ = 0
        resize(maximumCapacity)
    }
    override val size: Int
        get() = size_

    override fun clear() {
        Arrays.fill(keyTable, 0, size_, null)
        Arrays.fill(valueTable, 0, size_, null)
        size_ = 0
    }


    /**
     * Reduces the size of the backing arrays to the size of the actual number of entries. This is useful to release memory when
     * many items have been removed, or if it is known that more entries will not be added.
     */
    fun shrink() {
        if (keyTable.size == size_) return
        resize(size_)
    }

    /**
     * Increases the size of the backing arrays to accommodate the specified number of additional entries. Useful before adding
     * many entries to avoid multiple backing array resizes.
     */
    fun ensureCapacity(additionalCapacity: Int) {
        require(additionalCapacity >= 0) { "additionalCapacity must be >= 0: $additionalCapacity" }
        val sizeNeeded = size_ + additionalCapacity
        if (sizeNeeded > keyTable.size) resize(
            max(max(8.0, sizeNeeded.toDouble()), (size_ * 1.75f).toInt().toDouble()).toInt()
        )
    }

    protected fun resize(newSize: Int) {
        @Suppress("UNCHECKED_CAST")
        val newKeys = java.lang.reflect.Array.newInstance(keyTable.javaClass.componentType, newSize) as Array<K?>
        System.arraycopy(keyTable, 0, newKeys, 0, min(size_.toDouble(), newKeys.size.toDouble()).toInt())
        keyTable = newKeys

        @Suppress("UNCHECKED_CAST")
        val newValues = java.lang.reflect.Array.newInstance(valueTable.javaClass.componentType, newSize) as Array<V?>
        System.arraycopy(valueTable, 0, newValues, 0, min(size_.toDouble(), newValues.size.toDouble()).toInt())
        valueTable = newValues
    }

    fun reverse() {
        var i = 0
        val lastIndex = size_ - 1
        val n = size_ / 2
        while (i < n) {
            val ii = lastIndex - i
            val tempKey = keyTable[i]
            keyTable[i] = keyTable[ii]
            keyTable[ii] = tempKey

            val tempValue = valueTable[i]
            valueTable[i] = valueTable[ii]
            valueTable[ii] = tempValue
            i++
        }
    }

    fun shuffle() {
        for (i in size_ - 1 downTo 0) {
            val ii = random(i)
            val tempKey = keyTable[i]
            keyTable[i] = keyTable[ii]
            keyTable[ii] = tempKey
            val tempValue = valueTable[i]
            valueTable[i] = valueTable[ii]
            valueTable[ii] = tempValue
        }
    }

    /**
     * Reduces the size of the arrays to the specified size. If the arrays are already smaller than the specified size, no action
     * is taken.
     */
    fun truncate(newSize: Int) {
        if (size_ <= newSize) return
        for (i in newSize until size_) {
            keyTable[i] = null
            valueTable[i] = null
        }
        size_ = newSize
    }

    override fun hashCode(): Int {
        val keys = keyTable
        val values = valueTable
        var h = 0
        var i = 0
        val n = size_
        while (i < n) {
            val key: K? = keys[i]
            val value: V? = values[i]
            if (key != null) h += key.hashCode() * 31
            if (value != null) h += value.hashCode()
            i++
        }
        return h
    }


    @Suppress("UNCHECKED_CAST")
    override fun equals(other: Any?): Boolean {
        if (other === this) return true
        if (other !is ArrayMap<*, *>) return false

        other as ArrayMap<K, V>
        if (other.size_ != size_) return false

        val keys = keyTable
        val values = valueTable
        var i = 0
        val n = size_

        while (i < n) {
            val key = keys[i] as K
            val value = values[i]
            if (value == null) {
                if (other.get(key, dummy as V?) != null) {
                    return false
                }
            }
            else {
                if (value != other.get(key)) {
                    return false
                }
            }
            i++
        }
        return true
    }

    /**
     * Uses == for comparison of each value.
     */
    @Suppress("UNCHECKED_CAST")
    fun equalsIdentity(other: Any): Boolean {
        if (other === this) return true
        if (other !is ArrayMap<*, *>) return false

        other as ArrayMap<K, V?>

        if (other.size_ != size_) return false
        val keys = keyTable
        val values = valueTable
        var i = 0
        val n = size_
        while (i < n) {
            if (values[i] !== other.get(keys[i], dummy as V?)) return false
            i++
        }
        return true
    }

    override fun toString(): String {
        if (size_ == 0) return "{}"

        val keys = keyTable
        val values = valueTable
        val buffer = StringBuilder(32)
        buffer.append('{')
        buffer.append(keys[0])
        buffer.append('=')
        buffer.append(values[0])

        for (i in 1 until size_) {
            buffer.append(", ")
            buffer.append(keys[i])
            buffer.append('=')
            buffer.append(values[i])
        }
        buffer.append('}')
        return buffer.toString()
    }

    override fun iterator(): MutableIterator<Entry<K, V?>> {
        return entries()
    }

    @Suppress("UNCHECKED_CAST")
    override val entries: MutableSet<MutableMap.MutableEntry<K, V?>>
        get() = entries() as MutableSet<MutableMap.MutableEntry<K, V?>>

    override val keys: MutableSet<K>
        get() = keys()

    override val values: MutableCollection<V?>
        get() = values()

    /**
     * Returns an iterator for the entries in the map. Remove is supported.
     *
     * If [Collections.allocateIterators] is false, the same iterator instance is returned each time this method is called.
     * Use the [Entries] constructor for nested or multithreaded iteration.
     *
     * @see Collections.allocateIterators
     */
    @Suppress("UNCHECKED_CAST")
    fun entries(): Entries<K, V?> {
        if (allocateIterators) {
            return Entries(this as ArrayMap<K, V?>)
        }

        if (entries1 == null) {
            entries1 = Entries(this)
            entries2 = Entries(this)
        }
        if (!entries1!!.valid) {
            entries1!!.index = 0
            entries1!!.valid = true
            entries2!!.valid = false
            return entries1 as Entries<K, V?>
        }

        entries2!!.index = 0
        entries2!!.valid = true
        entries1!!.valid = false
        return entries2 as Entries<K, V?>
    }

    /**
     * Returns an iterator for the values in the map. Remove is supported.
     *
     * If [Collections.allocateIterators] is false, the same iterator instance is returned each time this method is called.
     * Use the [Entries] constructor for nested or multithreaded iteration.
     *
     * @see Collections.allocateIterators
     */
    @Suppress("UNCHECKED_CAST")
    fun values(): Values<V?> {
        if (allocateIterators) {
            return Values(this as ArrayMap<Any,V?>)
        }

        if (values1 == null) {
            values1 = Values(this as ArrayMap<Any,V?>)
            values2 = Values(this as ArrayMap<Any,V?>)
        }
        if (!values1!!.valid) {
            values1!!.index = 0
            values1!!.valid = true
            values2!!.valid = false
            return values1 as Values<V?>
        }

        values2!!.index = 0
        values2!!.valid = true
        values1!!.valid = false
        return values2 as Values<V?>
    }

    /**
     * Returns an iterator for the keys in the map. Remove is supported.
     *
     * If [Collections.allocateIterators] is false, the same iterator instance is returned each time this method is called.
     * Use the [Entries] constructor for nested or multithreaded iteration.
     *
     * @see Collections.allocateIterators
     */
    @Suppress("UNCHECKED_CAST")
    fun keys(): Keys<K> {
        if (allocateIterators) {
            return Keys(this as ArrayMap<K,Any>)
        }

        if (keys1 == null) {
            keys1 = Keys(this as ArrayMap<K,Any>)
            keys2 = Keys(this as ArrayMap<K,Any>)
        }

        if (!keys1!!.valid) {
            keys1!!.index = 0
            keys1!!.valid = true
            keys2!!.valid = false
            return keys1!!
        }

        keys2!!.index = 0
        keys2!!.valid = true
        keys1!!.valid = false
        return keys2!!
    }

    class Entries<K: Any, V>(private val map: ArrayMap<K, V>) :  MutableSet<Entry<K, V?>>,Iterable<Entry<K, V?>>, MutableIterator<Entry<K, V?>> {

        var entry: Entry<K, V?> = Entry()
        var index = 0
        var valid = true

        override fun hasNext(): Boolean {
            if (!valid) throw RuntimeException("#iterator() cannot be used nested.")
            return index < map.size_
        }

        override fun add(element: Entry<K, V?>): Boolean {
            map.put(element.key, element.value)
            reset()
            return true
        }

        override fun addAll(elements: Collection<Entry<K, V?>>): Boolean {
            var added = false
            elements.forEach { (k,v) ->
                map.put(k, v)
                added = true
            }
            reset()
            return added
        }

        override val size: Int
            get() = map.size_

        override fun clear() {
            map.clear()
        }

        override fun isEmpty(): Boolean {
            return map.isEmpty()
        }

        override fun containsAll(elements: Collection<Entry<K, V?>>): Boolean {
            elements.forEach { (k,v) ->
                if (map.get(k) != v) {
                    return false
                }
            }
            return true
        }

        override fun contains(element: Entry<K, V?>): Boolean {
            return map.get(element.key) == element.value
        }

        override fun iterator(): MutableIterator<Entry<K, V?>> {
            return this
        }

        override fun retainAll(elements: Collection<Entry<K, V?>>): Boolean {
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

        override fun removeAll(elements: Collection<Entry<K, V?>>): Boolean {
            var removed = false
            elements.forEach { (k,v) ->
                if (map.get(k) == v) {
                    map.removeKey(k)
                    removed = true
                }
            }
            reset()
            return removed
        }

        override fun remove(element: Entry<K, V?>): Boolean {
            if (map.get(element.key) == element.value) {
                map.removeKey(element.key)
                return true
            }
            reset()
            return false
        }

        /** Note the same entry instance is returned each time this method is called.  */
        override fun next(): Entry<K, V?> {
            if (index >= map.size_) throw NoSuchElementException(index.toString())
            if (!valid) throw RuntimeException("#iterator() cannot be used nested.")

            entry.key = map.keyTable[index] as K
            entry.value = map.valueTable[index++]
            return entry
        }

        override fun remove() {
            index--
            map.removeIndex(index)
        }

        fun reset() {
            index = 0
        }
    }

    class Values<V>(map: ArrayMap<Any, V?>) : MutableCollection<V>, Iterable<V>, MutableIterator<V> {
        private val map: ArrayMap<Any, V?>
        var index = 0
        var valid = true

        init {
            this.map = map
        }

        override fun hasNext(): Boolean {
            if (!valid) throw RuntimeException("#iterator() cannot be used nested.")
            return index < map.size_
        }

        override val size: Int
            get() = map.size_

        override fun clear() {
            map.clear()
        }

        override fun addAll(elements: Collection<V>): Boolean {
            throw IllegalStateException("Cannot add values to a map without keys")
        }

        override fun add(element: V): Boolean {
            throw IllegalStateException("Cannot add values to a map without keys")
        }

        override fun isEmpty(): Boolean {
            return map.size_ == 0
        }

        override fun containsAll(elements: Collection<V>): Boolean {
            elements.forEach {
                if (!map.containsValue(it)) {
                    return false
                }
            }
            return true
        }

        override fun contains(element: V): Boolean {
            return map.containsValue(element)
        }

        override fun iterator(): MutableIterator<V> {
            return this
        }

        override fun retainAll(elements: Collection<V>): Boolean {
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

        override fun removeAll(elements: Collection<V>): Boolean {
            var removed = false
            elements.forEach {
                val key = map.getKey(it, false)
                if (key != null) {
                    removed = map.remove(key) != null || removed
                }
            }
            reset()
            return removed
        }

        override fun remove(element: V): Boolean {
            var removed = false
            val key = map.getKey(element, false)
            if (key != null) {
                removed = map.remove(key) != null
            }
            reset()
            return removed
        }

        override fun next(): V {
            if (index >= map.size_) throw NoSuchElementException(index.toString())
            if (!valid) throw RuntimeException("#iterator() cannot be used nested.")

            return map.valueTable[index++]!!
        }

        override fun remove() {
            index--
            map.removeIndex(index)
        }

        fun reset() {
            index = 0
        }

        fun toArray(): ExpandingArray<V> {
            @Suppress("UNCHECKED_CAST")
            return ExpandingArray(true, map.valueTable, index, map.size_ - index) as ExpandingArray<V>
        }

        fun toArray(array: ExpandingArray<V>): ExpandingArray<V> {
            @Suppress("UNCHECKED_CAST")
            array.addAll(map.valueTable as Array<V>, index, map.size_ - index)
            return array
        }
    }

    class Keys<K: Any>(map: ArrayMap<K, Any>) : MutableSet<K>, Iterable<K>, MutableIterator<K> {
        private val map: ArrayMap<K, Any>
        var index = 0
        var valid = true

        init {
            this.map = map
        }

        override fun hasNext(): Boolean {
            if (!valid) throw RuntimeException("#iterator() cannot be used nested.")
            return index < map.size_
        }

        override fun add(element: K): Boolean {
            throw IllegalStateException("Cannot add keys to a map without values")
        }

        override fun addAll(elements: Collection<K>): Boolean {
            throw IllegalStateException("Cannot add keys to a map without values")
        }

        override val size: Int
            get() = map.size_

        override fun clear() {
            map.clear()
            reset()
        }

        override fun isEmpty(): Boolean {
            return map.size_ == 0
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

        override fun iterator(): MutableIterator<K> {
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

        override fun next(): K {
            if (index >= map.size_) throw NoSuchElementException(index.toString())
            if (!valid) throw RuntimeException("#iterator() cannot be used nested.")

            return map.keyTable[index++]!!
        }

        override fun remove() {
            index--
            map.removeIndex(index)
        }

        fun reset() {
            index = 0
        }

        fun toArray(): ExpandingArray<K?> {
            return ExpandingArray(true, map.keyTable, index, map.size_ - index)
        }

        fun toArray(array: ExpandingArray<K>): ExpandingArray<K> {
            @Suppress("UNCHECKED_CAST")
            array.addAll(map.keyTable as Array<K>, index, map.size_ - index)
            return array
        }
    }
}
