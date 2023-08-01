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

/**
 * An unordered map that uses identity comparison for the object keys. Null keys are not allowed. No allocation is done except
 * when growing the table size.
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
 * hashing, instead of the more common power-of-two mask, to better distribute poor hashCodes (see [Malte Skarupke's blog post](https://probablydance.com/2018/06/16/fibonacci-hashing-the-optimization-that-the-world-forgot-or-a-better-alternative-to-integer-modulo/)).
 *
 * Linear probing continues to work even when all hashCodes collide, just more slowly.
 * @author Tommy Ettinger
 * @author Nathan Sweet
 */
class IdentityMap<K: Any, V> : ObjectMap<K, V> {
    companion object {
        const val version = Collections.version
    }

    /** Creates a new map with an initial capacity of 51 and a load factor of 0.8.  */
    constructor() : super()

    /**
     * Creates a new map with a load factor of 0.8.
     *
     * @param initialCapacity The backing array size is initialCapacity / loadFactor, increased to the next power of two.
     */
    constructor(initialCapacity: Int) : super(initialCapacity)

    /**
     * Creates a new map with the specified initial capacity and load factor. This map will hold initialCapacity items before
     * growing the backing table.
     *
     * @param initialCapacity The backing array size is initialCapacity / loadFactor, increased to the next power of two.
     */
    constructor(initialCapacity: Int, loadFactor: Float) : super(initialCapacity, loadFactor)

    /** Creates a new map identical to the specified map.  */
    constructor(map: IdentityMap<K, V>) : super(map)

    override fun place(item: Any): Int {
        return (System.identityHashCode(item) * -0x61c8864680b583ebL ushr shift).toInt()
    }

    override fun locateKey(key: Any): Int {
        val keyTable = keyTable
        var i = place(key)

        while (true) {
            val other = keyTable[i] ?: return -(i + 1)
            // Empty space is available.
            if (other === key) return i // Same key was found.
            i = (i + 1) and mask
        }
    }

    override fun hashCode(): Int {
        var h = size
        val keyTable = keyTable
        val valueTable = valueTable
        var i = 0
        val n = keyTable.size

        while (i < n) {
            val key = keyTable[i]
            if (key != null) {
                h += System.identityHashCode(key)
                val value = valueTable[i]
                if (value != null) h += value.hashCode()
            }
            i++
        }
        return h
    }
}
