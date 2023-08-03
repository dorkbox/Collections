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

/** Interface used to select items within an iterator against a predicate.
 * @author Xoppa
 */
interface Predicate<T> {
    /** @return true if the item matches the criteria and should be included in the iterators items
     */
    fun evaluate(arg0: T): Boolean
    class PredicateIterator<T>(iterator: MutableIterator<T>, predicate: Predicate<T>?) : MutableIterator<T?> {
        var iterator: MutableIterator<T>? = null
        var predicate: Predicate<T>? = null
        var end = false
        var peeked = false
        var next: T? = null

        constructor(iterable: Iterable<T>, predicate: Predicate<T>?) : this(iterable.iterator() as MutableIterator<T>, predicate)

        init {
            set(iterator, predicate)
        }

        operator fun set(iterable: Iterable<T>, predicate: Predicate<T>?) {
            set(iterable.iterator() as MutableIterator<T>, predicate)
        }

        operator fun set(iterator: MutableIterator<T>, predicate: Predicate<T>?) {
            this.iterator = iterator
            this.predicate = predicate

            peeked = false
            end = peeked
            next = null
        }

        override fun hasNext(): Boolean {
            if (end) return false
            if (next != null) return true
            peeked = true
            while (iterator!!.hasNext()) {
                val n = iterator!!.next()
                if (predicate!!.evaluate(n)) {
                    next = n
                    return true
                }
            }
            end = true
            return false
        }

        override fun next(): T? {
            if (next == null && !hasNext()) return null
            val result = next
            next = null
            peeked = false
            return result
        }

        override fun remove() {
            if (peeked) throw RuntimeException("Cannot remove between a call to hasNext() and next().")
            iterator!!.remove()
        }
    }

    class PredicateIterable<T>(iterable: Iterable<T>?, predicate: Predicate<T>?) : Iterable<T> {
        var iterable: Iterable<T>? = null
        var predicate: Predicate<T>? = null
        var iterator: PredicateIterator<T>? = null

        init {
            set(iterable, predicate)
        }

        operator fun set(iterable: Iterable<T>?, predicate: Predicate<T>?) {
            this.iterable = iterable
            this.predicate = predicate
        }

        /** Returns an iterator. Note that the same iterator instance is returned each time this method is called. Use the
         * [Predicate.PredicateIterator] constructor for nested or multithreaded iteration.  */
        override fun iterator(): MutableIterator<T> {
            if (iterator == null) {
                iterator = PredicateIterator<T>(iterable!!.iterator() as MutableIterator<T>, predicate)
            }
            else {
                iterator!!.set(iterable!!.iterator() as MutableIterator<T>, predicate)
            }
            @Suppress("UNCHECKED_CAST")
            return iterator as MutableIterator<T>
        }
    }
}
