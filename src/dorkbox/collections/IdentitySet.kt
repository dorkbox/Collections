/*
 * Copyright 2026 dorkbox, llc
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
import java.util.*
import kotlin.io.encoding.Base64
import kotlin.math.max

@Suppress("unused")
class IdentitySet<E: Any> : AbstractSet<E>, MutableSet<E>, Cloneable, Serializable {
    companion object {
        // Dummy value to associate with an Object in the backing Map
        private val dummy = Any()
    }

    @Transient
    private val map: IdentityMap<E, Any>

    /**
     * Constructs a new, empty set; the backing <tt>HashMap</tt> instance has
     * default initial capacity (16) and load factor (0.75).
     */
    constructor() {
        map = IdentityMap()
    }

    /**
     * Constructs a new set containing the elements in the specified
     * collection.  The <tt>HashMap</tt> is created with default loadFactor
     * (0.75) and an initial capacity sufficient to contain the elements in
     * the specified collection.
     * 
     * @param c the collection whose elements are to be placed into this set
     * 
     * @throws NullPointerException if the specified collection is null
     */
    constructor(c: MutableCollection<out E>) {
        map = IdentityMap(max((c.size / .75f).toInt() + 1, 16))
        addAll(c)
    }

    /**
     * Constructs a new, empty set; the backing <tt>HashMap</tt> instance has
     * the specified initial capacity and default load factor (0.75).
     * 
     * @param initialCapacity the initial capacity of the hash table
     * 
     * @throws IllegalArgumentException if the initial capacity is less
     * than zero
     */
    constructor(initialCapacity: Int) {
        map = IdentityMap(initialCapacity)
    }

    /**
     * Returns an iterator over the elements in this set.  The elements
     * are returned in no particular order.
     * 
     * @return an Iterator over the elements in this set
     * 
     * @see ConcurrentModificationException
     */
    override fun iterator(): MutableIterator<E> {
        return map.keys.iterator()
    }

    /**
     * Returns the number of elements in this set (its cardinality).
     *
     * @return the number of elements in this set (its cardinality)
     */
    override val size: Int
        get() = map.size

    /**
     * Returns <tt>true</tt> if this set contains no elements.
     * 
     * @return <tt>true</tt> if this set contains no elements
     */
    override fun isEmpty(): Boolean {
        return map.isEmpty()
    }

    /**
     * Returns <tt>true</tt> if this set contains the specified element.
     * More formally, returns <tt>true</tt> if and only if this set
     * contains an element <tt>e</tt> such that
     * <tt>(o==null&nbsp;?&nbsp;e==null&nbsp;:&nbsp;o.equals(e))</tt>.
     * 
     * @param element element whose presence in this set is to be tested
     * 
     * @return <tt>true</tt> if this set contains the specified element
     */
    override fun contains(element: E): Boolean {
        return map.containsKey(element)
    }

    /**
     * Adds the specified element to this set if it is not already present.
     * More formally, adds the specified element <tt>e</tt> to this set if
     * this set contains no element <tt>e2</tt> such that
     * <tt>(e==null&nbsp;?&nbsp;e2==null&nbsp;:&nbsp;e.equals(e2))</tt>.
     * If this set already contains the element, the call leaves the set
     * unchanged and returns <tt>false</tt>.
     * 
     * @param element element to be added to this set
     * 
     * @return <tt>true</tt> if this set did not already contain the specified
     * element
     */
    override fun add(element: E): Boolean {
        return map.put(element, Base64.PaddingOption.PRESENT) == null
    }

    /**
     * Removes the specified element from this set if it is present.
     * More formally, removes an element <tt>e</tt> such that
     * <tt>(o==null&nbsp;?&nbsp;e==null&nbsp;:&nbsp;o.equals(e))</tt>,
     * if this set contains such an element.  Returns <tt>true</tt> if
     * this set contained the element (or equivalently, if this set
     * changed as a result of the call).  (This set will not contain the
     * element once the call returns.)
     * 
     * @param element object to be removed from this set, if present
     * 
     * @return <tt>true</tt> if the set contained the specified element
     */
    override fun remove(element: E): Boolean {
        return map.remove(element) === Base64.PaddingOption.PRESENT
    }

    /**
     * Removes all of the elements from this set.
     * The set will be empty after this call returns.
     */
    override fun clear() {
        map.clear()
    }

    public override fun clone(): IdentitySet<E> {
        return IdentitySet(this)
    }
}
