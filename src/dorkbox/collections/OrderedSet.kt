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
 * A [ObjectSet] that also stores keys in an [ArrayList] using the insertion order. Null keys are not allowed.
 *
 *
 * [Iteration][.iterator] is ordered and faster than an unordered set.
 *
 * This class performs fast contains (typically O(1), worst case O(n) but that is rare in practice). Remove is somewhat slower due
 * to [.orderedItems]. Add may be slightly slower, depending on hash collisions. Hashcodes are rehashed to reduce
 * collisions and the need to resize. Load factors greater than 0.91 greatly increase the chances to resize to the next higher POT
 * size.
 *
 *
 * Unordered sets and maps are not designed to provide especially fast iteration. Iteration is faster with OrderedSet and
 * OrderedMap.
 *
 *
 * This implementation uses linear probing with the backward shift algorithm for removal.
 *
 * Hashcodes are rehashed using Fibonacci hashing, instead of the more common power-of-two mask, to better distribute poor
 * hashCodes (see [Malte Skarupke's blog post](https://probablydance.com/2018/06/16/fibonacci-hashing-the-optimization-that-the-world-forgot-or-a-better-alternative-to-integer-modulo/)).
 *
 * Linear probing continues to work even when all hashCodes collide, just more slowly.
 *
 * @author Nathan Sweet
 * @author Tommy Ettinger
 */
class OrderedSet<T> : ObjectSet<T> where T : Any, T : Comparable<T> {
    companion object {
        val version = Collections.version

        fun <T> with(vararg array: T): OrderedSet<T> where T : Any, T : Comparable<T> {
            val set = OrderedSet<T>()
            set.addAll(*array)
            return set
        }
    }

    private val items: ArrayList<T>

    @Transient
    var iterator1: OrderedSetIterator<T>? = null

    @Transient
    var iterator2: OrderedSetIterator<T>? = null

    constructor() {
        items = ArrayList()
    }

    constructor(initialCapacity: Int, loadFactor: Float) : super(initialCapacity, loadFactor) {
        items = ArrayList(initialCapacity)
    }

    constructor(initialCapacity: Int) : super(initialCapacity) {
        items = ArrayList(initialCapacity)
    }

    constructor(set: OrderedSet<out T>) : super(set) {
        items = ArrayList(set.items)
    }

    /** Sorts this array. The array elements must implement [Comparable]. This method is not thread safe.  */
    fun sort() {
        items.sort()
    }

    /** Sorts the array. This method is not thread safe.  */
    fun sort(comparator: Comparator<in T>) {
        items.sortWith(comparator)
    }

    override fun add(key: T): Boolean {
        if (!super.add(key)) return false
        items.add(key)
        return true
    }

    /**
     * Sets the key at the specfied index. Returns true if the key was added to the set or false if it was already in the set. If
     * this set already contains the key, the existing key's index is changed if needed and false is returned.
     */
    fun add(key: T, index: Int): Boolean {
        if (!super.add(key)) {
            var oldIndex = -1
            items.forEachIndexed { index, item ->
                if (item === key) {
                    oldIndex = index
                    return@forEachIndexed
                }
            }

            if (oldIndex != index) {
                val oldItem = items.removeAt(oldIndex)
                items.add(index, oldItem)
            }
            return false
        }
        items.add(index, key)
        return true
    }

    fun addAll(set: OrderedSet<T>) {
        ensureCapacity(set.size)

        val keys = set.items
        var i = 0
        val n = set.items.size
        while (i < n) {
            add(keys[i])
            i++
        }
    }

    override fun remove(element: T): Boolean {
        if (!super.remove(element)) return false
        items.remove(element)
        return true
    }

    fun removeIndex(index: Int): T {
        val itemAtIndex = items.removeAt(index)
        super.remove(itemAtIndex)
        return itemAtIndex
    }

    /**
     * Changes the item `before` to `after` without changing its position in the order. Returns true if `after`
     * has been added to the OrderedSet and `before` has been removed; returns false if `after` is already present or
     * `before` is not present. If you are iterating over an OrderedSet and have an index, you should prefer
     * [.alterIndex], which doesn't need to search for an index like this does and so can be faster.
     *
     * @param before an item that must be present for this to succeed
     * @param after an item that must not be in this set for this to succeed
     *
     * @return true if `before` was removed and `after` was added, false otherwise
     */
    fun alter(before: T, after: T): Boolean {
        if (contains(after)) return false
        if (!super.remove(before)) return false
        super.add(after)
        items[items.indexOf(before)] = after
        return true
    }

    /**
     * Changes the item at the given `index` in the order to `after`, without changing the ordering of other items. If
     * `after` is already present, this returns false; it will also return false if `index` is invalid for the size of
     * this set. Otherwise, it returns true. Unlike [.alter], this operates in constant time.
     *
     * @param index the index in the order of the item to change; must be non-negative and less than [.size]
     * @param after the item that will replace the contents at `index`; this item must not be present for this to succeed
     *
     * @return true if `after` successfully replaced the contents at `index`, false otherwise
     */
    fun alterIndex(index: Int, after: T): Boolean {
        if (index < 0 || index >= size || contains(after)) return false
        super.remove(items[index])
        super.add(after)
        items[index] = after
        return true
    }

    override fun clear(maximumCapacity: Int) {
        items.clear()
        super.clear(maximumCapacity)
    }

    override fun clear() {
        items.clear()
        super.clear()
    }

    fun orderedItems(): ArrayList<T> {
        return items
    }

    override fun iterator(): OrderedSetIterator<T> {
        if (allocateIterators) return OrderedSetIterator(this)
        if (iterator1 == null) {
            iterator1 = OrderedSetIterator(this)
            iterator2 = OrderedSetIterator(this)
        }
        if (!iterator1!!.valid) {
            iterator1!!.reset()
            iterator1!!.valid = true
            iterator2!!.valid = false
            return iterator1 as OrderedSetIterator<T>
        }
        iterator2!!.reset()
        iterator2!!.valid = true
        iterator1!!.valid = false
        return iterator2 as OrderedSetIterator<T>
    }

    override fun toString(): String {
        if (size == 0) return "{}"
        val items = items
        val buffer = StringBuilder(32)
        buffer.append('{')
        buffer.append(items[0])
        for (i in 1 until size) {
            buffer.append(", ")
            buffer.append(items[i])
        }
        buffer.append('}')
        return buffer.toString()
    }

    override fun toString(separator: String): String {
        return items.joinToString(separator)
    }

    class OrderedSetIterator<T>(set: OrderedSet<T>) : ObjectSetIterator<T>(set) where T : Any, T : Comparable<T> {
        private val items: ArrayList<T>

        init {
            items = set.items
        }

        override fun reset() {
            nextIndex = 0
            hasNext = set.size > 0
        }

        override fun next(): T {
            if (!hasNext) throw NoSuchElementException()
            if (!valid) throw RuntimeException("#iterator() cannot be used nested.")
            val key = items[nextIndex]
            nextIndex++
            hasNext = nextIndex < set.size
            return key
        }

        override fun remove() {
            check(nextIndex >= 0) { "next must be called before remove." }
            nextIndex--
            (set as OrderedSet<*>).removeIndex(nextIndex)
        }

        override fun toArray(): Array<T> {
            @Suppress("UNCHECKED_CAST")
            return Array(set.size - nextIndex) { next() as Any } as Array<T>
        }

        override fun toArray(array: Array<T>): Array<T> {
            var i = nextIndex
            while(hasNext) {
                array[i++] = next()
            }

            nextIndex = items.size
            hasNext = false
            return array
        }
    }
}
