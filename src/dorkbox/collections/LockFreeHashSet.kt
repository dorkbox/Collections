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
class LockFreeHashSet<E> : MutableSet<E>, Cloneable, Serializable {

    @Volatile
    private var hashSet: MutableSet<E> = HashSet()

    // synchronized is used here to ensure the "single writer principle", and make sure that ONLY one thread at a time can enter this
    // section. Because of this, we can have unlimited reader threads all going at the same time, without contention (which is our
    // use-case 99% of the time)

    constructor()

    constructor(elements: Collection<E>) {
        hashSet.addAll(elements)
    }

    constructor(hashSet: LockFreeHashSet<E>) {
        this.hashSet.addAll(hashSet.hashSet)
    }

    override val size: Int
        get() {
            return setREF[this].size
        }

    val elements: MutableSet<E>
        get() {
            @Suppress("UNCHECKED_CAST")
            return setREF[this] as MutableSet<E>
        }

    override fun isEmpty(): Boolean {
        return setREF[this].isEmpty()
    }

    override fun containsAll(elements: Collection<E>): Boolean {
        return setREF[this].containsAll(elements)
    }

    override fun contains(element: E): Boolean {
        return setREF[this].contains(element)
    }

    @Synchronized
    override fun add(element: E): Boolean {
        return hashSet.add(element)
    }

    @Synchronized
    override fun retainAll(elements: Collection<E>): Boolean {
        return hashSet.retainAll(elements)
    }

    @Synchronized
    override fun removeAll(elements: Collection<E>): Boolean {
        return hashSet.removeAll(elements)
    }

    @Synchronized
    override fun addAll(elements: Collection<E>): Boolean {
        return hashSet.addAll(elements)
    }

    @Synchronized
    override fun remove(element: E): Boolean {
        return hashSet.remove(element)
    }

    override fun iterator(): MutableIterator<E> {
        @Suppress("UNCHECKED_CAST")
        return setREF[this].iterator() as MutableIterator<E>
    }

    fun toArray(): Array<Any> {
        @Suppress("UNCHECKED_CAST")
        return setREF[this].toTypedArray() as Array<Any>
    }

    @Synchronized
    override fun clear() {
        hashSet.clear()
    }

    override fun equals(other: Any?): Boolean {
        return (setREF[this] == other)
    }

    override fun hashCode(): Int {
        return setREF[this].hashCode()
    }

    override fun toString(): String {
        return setREF[this].toString()
    }

    /**
     * Return a non-thread-safe copy of the backing set
     */
    fun toSet(): HashSet<E> {
        @Suppress("UNCHECKED_CAST")
        return HashSet(setREF[this] as HashSet<E>)
    }

    companion object {
        const val version = Collections.version

        // Recommended for best performance while adhering to the "single writer principle". Must be static-final
        private val setREF = AtomicReferenceFieldUpdater.newUpdater(
            LockFreeHashSet::class.java, MutableSet::class.java, "hashSet"
        )
    }
}
