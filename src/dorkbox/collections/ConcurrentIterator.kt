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

import java.util.concurrent.atomic.*

/**
 * @author dorkbox, llc
 */
class ConcurrentIterator<T> {
    /**
     * Specifies the load-factor for the IdentityMap used
     */
    private var loadFactor = 0.8f
    private val ID = ID_COUNTER.getAndIncrement()

    // This is only touched by a single thread, maintains a map of entries for FAST lookup during remove.
    private val entries = IdentityMap<T, ConcurrentEntry<T>>(32, loadFactor)

    // this is still inside the single-writer, and can use the same techniques as subscription manager (for thread safe publication)
    @Volatile
    private var head: ConcurrentEntry<T>? = null // reference to the first element

    constructor()
    constructor(loadFactor: Float) {
        this.loadFactor = loadFactor
    }

    /**
     * single writer principle!
     * called from within SYNCHRONIZE
     */
    @Synchronized
    fun clear() {
        entries.clear()
        head = null
    }

    /**
     * Performs the given [action] on each element. List modification can occur at the same time as list iteration.
     */
    inline fun forEach(action: (T) -> Unit) {
        // this is to just a concurrent iterable
        // access a snapshot (single-writer-principle)
        @Suppress("UNCHECKED_CAST")
        var current: ConcurrentEntry<T>? = headREF.get(this) as ConcurrentEntry<T>?

        var updatable: T
        while (current != null) {
            // Concurrent iteration...
            updatable = current.value
            current = current.next()

            // update the data
            action(updatable)
        }
    }

    /**
     * Performs the given [action] on each element. List modification can occur at the same time as list iteration.
     */
    inline fun forEachRemovable(action: ConcurrentEntry<T>.(T) -> Unit) {
        // this is to just a concurrent iterable
        // access a snapshot (single-writer-principle)
        @Suppress("UNCHECKED_CAST")
        var current: ConcurrentEntry<T>? = headREF.get(this) as ConcurrentEntry<T>?

        var updatable: T
        while (current != null) {
            // Concurrent iteration...
            updatable = current.value

            // update the data
            action(current, updatable)

            current = current.next()
        }
    }

    /**
     * single writer principle!
     * called from within SYNCHRONIZE
     *
     * @param listener the object that will receive messages during publication
     */
    @Synchronized
    fun add(listener: T) {
        @Suppress("UNCHECKED_CAST")
        var head: ConcurrentEntry<T>? = headREF[this] as ConcurrentEntry<T>?
        if (!entries.containsKey(listener)) {
            head = ConcurrentEntry(listener, head)
            entries.put(listener, head)
            headREF.lazySet(this, head)
        }
    }

    /**
     * single writer principle!
     * called from within SYNCHRONIZE
     *
     * @param listener the object that will NO LONGER be part of the collection
     */
    @Synchronized
    fun remove(listener: T): Boolean {
        val concurrentEntry: ConcurrentEntry<T>? = entries[listener]
        if (concurrentEntry != null) {
            return remove(concurrentEntry)
        }
        return false
    }

    /**
     * single writer principle!
     * called from within SYNCHRONIZE
     *
     * @param concurrentEntry the object holder that will NO LONGER be part of the collection
     */
    @Synchronized
    fun remove(concurrentEntry: ConcurrentEntry<T>): Boolean {
        @Suppress("UNCHECKED_CAST")
        var head: ConcurrentEntry<T>? = headREF[this] as ConcurrentEntry<T>?

        if (concurrentEntry == head) {
            // if it was second, now it's first
            head = head.next()
            //oldHead.clear(); // optimize for GC not possible because of potentially running iterators
        } else {
            concurrentEntry.remove()
        }
        headREF.lazySet(this, head)
        entries.remove(concurrentEntry.value)
        return true
    }


    /**
     * single writer principle!
     * called from within SYNCHRONIZE
     */
    @Synchronized
    fun size(): Int {
        return entries.size
    }

    override fun hashCode(): Int {
        return ID
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other == null) {
            return false
        }
        if (javaClass != other.javaClass) {
            return false
        }

        other as ConcurrentIterator<*>
        return ID == other.ID
    }

    companion object {
        const val version = Collections.version
        private val ID_COUNTER = AtomicInteger()

        // Recommended for best performance while adhering to the "single writer principle". Must be static-final
        val headREF: AtomicReferenceFieldUpdater<ConcurrentIterator<*>, ConcurrentEntry<*>?> = AtomicReferenceFieldUpdater.newUpdater(
            ConcurrentIterator::class.java, ConcurrentEntry::class.java, "head"
        )
    }
}
