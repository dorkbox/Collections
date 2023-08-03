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
 * The MIT License
 *
 * Copyright 2013 Tim Boudreau.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package dorkbox.collections

import kotlin.math.abs

/**
 * General-purpose binary search algorithm;  you pass in an array or list
 * wrapped in an instance of `Indexed`, and an
 * `Evaluator` which converts the contents of the list into
 * numbers used by the binary search algorithm.  Note that the data
 * (as returned by the Indexed array/list) ***must be in order from
 * low to high***.  The indices need not be contiguous (presumably
 * they are not or you wouldn't be using this class), but they must be
 * sorted.  If assertions are enabled, this is enforced;  if not, very
 * bad things (endless loops, etc.) can happen as a consequence of passing
 * unsorted data in.
 *
 *
 * This class is not thread-safe and the size and contents of the `Indexed`
 * should not change while a search is being performed.
 *
 * @author Tim Boudreau
 */
class BinarySearch<T>(private val eval: Evaluator<T>, private val indexed: Indexed<T>) {
    companion object {
        const val version = Collections.version
    }

    /**
     * Create a new binary search.
     *
     * @param eval The thing which converts elements into numbers
     * @param indexed A collection, list or array
     */
    init {
        assert(checkSorted())
    }

    constructor(eval: Evaluator<T>, l: List<T>) : this(eval, ListWrap<T>(l))

    private fun checkSorted(): Boolean {
        var `val` = Long.MIN_VALUE
        val sz = indexed.size()
        for (i in 0 until sz) {
            val t = indexed[i]
            val nue = eval.getValue(t)
            if (`val` != Long.MIN_VALUE) {
                require(nue >= `val`) { "Collection is not sorted at " + i + " - " + indexed }
            }
            `val` = nue
        }
        return true
    }

    fun search(value: Long, bias: Bias): Long {
        return search(0, indexed.size() - 1, value, bias)
    }

    fun match(prototype: T, bias: Bias): T? {
        val value = eval.getValue(prototype)
        val index = search(value, bias)
        return if (index == -1L) null else indexed[index]
    }

    fun searchFor(value: Long, bias: Bias): T? {
        val index = search(value, bias)
        return if (index == -1L) null else indexed[index]
    }

    private fun search(start: Long, end: Long, value: Long, bias: Bias): Long {
        val range = end - start
        if (range == 0L) {
            return start
        }
        if (range == 1L) {
            val ahead = indexed[end]
            val behind = indexed[start]
            val v1 = eval.getValue(behind)
            val v2 = eval.getValue(ahead)
            return when (bias) {
                Bias.BACKWARD -> start
                Bias.FORWARD  -> end
                Bias.NEAREST  -> if (v1 == value) {
                    start
                }
                else if (v2 == value) {
                    end
                }
                else {
                    if (abs((v1 - value).toDouble()) < abs((v2 - value).toDouble())) {
                        start
                    }
                    else {
                        end
                    }
                }

                Bias.NONE     -> if (v1 == value) {
                    start
                }
                else if (v2 == value) {
                    end
                }
                else {
                    -1
                }
            }

        }
        val mid = start + range / 2
        val vm = eval.getValue(indexed[mid])

        return if (value >= vm) {
            search(mid, end, value, bias)
        }
        else {
            search(start, mid, value, bias)
        }
    }

    /**
     * Converts an object into a numeric value that is used to
     * perform binary search
     * @param <T>
    </T> */
    interface Evaluator<T> {
        fun getValue(obj: T): Long
    }

    /**
     * Abstraction for list-like things which have a length and indices
     * @param <T>
    </T> */
    interface Indexed<T> {
        operator fun get(index: Long): T
        fun size(): Long
    }

    private class ListWrap<T> internal constructor(private val l: List<T>) : Indexed<T> {
        override fun get(index: Long): T {
            return l[index.toInt()]
        }

        override fun size(): Long {
            return l.size.toLong()
        }

        override fun toString(): String {
            return super.toString() + '{' + l + '}'
        }
    }
}
