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
import java.util.*
import java.util.concurrent.atomic.*

/**
 * This class uses the "single-writer-principle" for lock-free publication.
 *
 * Since there are only 2 methods to guarantee that modifications can only be called one-at-a-time (either it is only called by
 * one thread, or only one thread can access it at a time) -- we chose the 2nd option -- and use 'synchronized' to make sure that only
 * one thread can access this modification methods at a time. Getting or checking the presence of values can then happen in a lock-free
 * manner.
 *
 * According to my benchmarks, this is approximately 25% faster than ConcurrentHashMap for (all types of) reads, and a lot slower for
 * contended writes.
 *
 * This data structure is for many-read/few-write scenarios
 */
class LockFreeLinkedList<E> : MutableList<E>, Cloneable, Serializable {

    @Volatile
    private var list = LinkedList<E>()

    constructor()
    constructor(elements: Collection<E>?) {
        list.addAll(elements!!)
    }

    constructor(list: LockFreeLinkedList<E>) {
        this.list.addAll(list.list)
    }

    // synchronized is used here to ensure the "single writer principle", and make sure that ONLY one thread at a time can enter this
    // section. Because of this, we can have unlimited reader threads all going at the same time, without contention (which is our
    // use-case 99% of the time)
    @Synchronized
    override fun clear() {
        list.clear()
    }

    // synchronized is used here to ensure the "single writer principle", and make sure that ONLY one thread at a time can enter this
    // section. Because of this, we can have unlimited reader threads all going at the same time, without contention (which is our
    // use-case 99% of the time)
    @Synchronized
    override fun add(element: E): Boolean {
        return list.add(element)
    }

    // synchronized is used here to ensure the "single writer principle", and make sure that ONLY one thread at a time can enter this
    // section. Because of this, we can have unlimited reader threads all going at the same time, without contention (which is our
    // use-case 99% of the time)
    @Synchronized
    override fun addAll(elements: Collection<E>): Boolean {
        return list.addAll(elements)
    }

    // synchronized is used here to ensure the "single writer principle", and make sure that ONLY one thread at a time can enter this
    // section. Because of this, we can have unlimited reader threads all going at the same time, without contention (which is our
    // use-case 99% of the time)
    @Synchronized
    override fun addAll(index: Int, elements: Collection<E>): Boolean {
        return list.addAll(index, elements)
    }

    // synchronized is used here to ensure the "single writer principle", and make sure that ONLY one thread at a time can enter this
    // section. Because of this, we can have unlimited reader threads all going at the same time, without contention (which is our
    // use-case 99% of the time)
    override fun removeAll(elements: Collection<E>): Boolean {
        return list.removeAll(elements.toSet())
    }

    // synchronized is used here to ensure the "single writer principle", and make sure that ONLY one thread at a time can enter this
    // section. Because of this, we can have unlimited reader threads all going at the same time, without contention (which is our
    // use-case 99% of the time)
    @Synchronized
    override fun retainAll(elements: Collection<E>): Boolean {
        return retainAll(elements)
    }

    override fun get(index: Int): E {
        @Suppress("UNCHECKED_CAST")
        return listRef[this][index] as E
    }

    // synchronized is used here to ensure the "single writer principle", and make sure that ONLY one thread at a time can enter this
    // section. Because of this, we can have unlimited reader threads all going at the same time, without contention (which is our
    // use-case 99% of the time)
    @Synchronized
    override fun set(index: Int, element: E): E {
        return list.set(index, element)
    }

    // synchronized is used here to ensure the "single writer principle", and make sure that ONLY one thread at a time can enter this
    // section. Because of this, we can have unlimited reader threads all going at the same time, without contention (which is our
    // use-case 99% of the time)
    @Synchronized
    override fun add(index: Int, element: E) {
        list.add(index, element)
    }

    // synchronized is used here to ensure the "single writer principle", and make sure that ONLY one thread at a time can enter this
    // section. Because of this, we can have unlimited reader threads all going at the same time, without contention (which is our
    // use-case 99% of the time)
    @Synchronized
    override fun removeAt(index: Int): E {
        return list.removeAt(index)
    }

    // lock-free get
    override fun indexOf(element: E): Int {
        return listRef[this].indexOf(element)
    }

    // lock-free get
    override fun lastIndexOf(element: E): Int {
        return listRef[this].lastIndexOf(element)
    }

    // lock-free get
    override fun listIterator(): MutableListIterator<E> {
        @Suppress("UNCHECKED_CAST")
        return listRef[this].listIterator() as MutableListIterator<E>
    }

    // lock-free get
    override fun listIterator(index: Int): MutableListIterator<E> {
        @Suppress("UNCHECKED_CAST")
        return listRef[this].listIterator(index) as MutableListIterator<E>
    }

    // lock-free get
    override fun subList(fromIndex: Int, toIndex: Int): MutableList<E> {
        @Suppress("UNCHECKED_CAST")
        return listRef[this].subList(fromIndex, toIndex) as MutableList<E>
    }

    // synchronized is used here to ensure the "single writer principle", and make sure that ONLY one thread at a time can enter this
    // section. Because of this, we can have unlimited reader threads all going at the same time, without contention (which is our
    // use-case 99% of the time)
    @Synchronized
    override fun remove(element: E): Boolean {
        return list.remove(element)
    }

    // lock-free get
    override fun containsAll(elements: Collection<E>): Boolean {
        return listRef[this].containsAll(elements)
    }

    // lock-free get
    override val size: Int
        get() {
            return listRef[this].size
        }

    // lock-free get
    override fun isEmpty(): Boolean {
        return listRef[this].isEmpty()
    }

    // lock-free get
    override operator fun contains(element: E): Boolean {
        // use the SWP to get the value
        return listRef[this].contains(element)
    }

    // lock-free get
    override fun iterator(): MutableIterator<E> {
        @Suppress("UNCHECKED_CAST")
        return listRef[this].iterator() as MutableIterator<E>
    }

    // lock-free get
    fun toArray(): Array<Any> {
        return listRef[this].toTypedArray()
    }

    // lock-free get
    fun <T> toArray(targetArray: Array<T>): Array<T> {
        return listRef[this].toArray(targetArray) as Array<T>
    }

    // lock-free get
    fun elements(): LinkedList<E> {
        @Suppress("UNCHECKED_CAST")
        return listRef[this] as LinkedList<E>
    }

    /**
     * Return a non-thread-safe copy of the backing array
     */
    fun toList(): LinkedList<E> {
        @Suppress("UNCHECKED_CAST")
        return LinkedList(listRef[this] as LinkedList<E>)
    }

    // this must be at the end of the file!
    companion object {
        const val version = Collections.version

        // Recommended for best performance while adhering to the "single writer principle". Must be static-final
        private val listRef = AtomicReferenceFieldUpdater.newUpdater(
            LockFreeLinkedList::class.java, LinkedList::class.java, "list"
        )
    }
}
