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

/*
 * AhoCorasickDoubleArrayTrie Project
 *      https://github.com/hankcs/AhoCorasickDoubleArrayTrie
 *
 * Copyright 2008-2018 hankcs <me@hankcs.com>
 * You may modify and redistribute as long as this attribution remains.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dorkbox.collections.ahoCorasick

import java.io.ObjectInputStream

class DoubleArrayByteArrayTrie<V>(map: Map<ByteArray, V>? = null, inputStream: ObjectInputStream? = null):
    BaseByteTrie<ByteArray, V>(map, inputStream) {

    override fun builder(): BaseByteBuilder<ByteArray, V> {
        return object: BaseByteBuilder<ByteArray, V>() {
            /**
             * add a keyword
             *
             * @param keyword a keyword
             * @param index the index of the keyword
             */
            override fun addKeyword(keyword: ByteArray, index: Int) {
                var currentState = this.rootState
                keyword.forEach { character ->
                    currentState = currentState!!.addState(character)
                }

                currentState!!.addEmit(index)
                this@DoubleArrayByteArrayTrie.l[index] = keyword.size
            }
        }
    }


    /**
     * Get value by a ByteArray key, just like a map.get() method
     *
     * @param key The key
     */
    operator fun get(key: ByteArray): V? {
        val index = exactMatchSearch(key)
        return if (index >= 0) {
            v[index]
        }
        else null

    }

    /**
     * Update a value corresponding to a key
     *
     * @param key the key
     * @param value the value
     *
     * @return successful or not（failure if there is no key）
     */
    operator fun set(key: ByteArray,
                     value: V): Boolean {
        val index = exactMatchSearch(key)
        if (index >= 0) {
            v[index] = value
            return true
        }

        return false
    }
}
