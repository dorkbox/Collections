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

//
// not thread-safe!!!
//
/**
 * @author bennidi
 * @author dorkbox, llc Date: 2/3/16
 */
class ConcurrentEntry<T>(value: T, next: ConcurrentEntry<T>?) {
    companion object {
        const val version = Collections.version
    }

    val value: T

    @Volatile
    private var next: ConcurrentEntry<T>? = null

    @Volatile
    private var prev: ConcurrentEntry<T>? = null

    init {
        if (next != null) {
            this.next = next
            next.prev = this
        }
        this.value = value
    }

    fun remove() {
        if (prev != null) {
            prev!!.next = next
            if (next != null) {
                next!!.prev = prev
            }
        } else if (next != null) {
            next!!.prev = null
        }

        // can not nullify references to help GC since running iterators might not see the entire set if this element is their current element
        //next = null;
        //prev = null;
    }

    fun next(): ConcurrentEntry<T>? {
        return next
    }

    fun clear() {
        next = null
    }
}
