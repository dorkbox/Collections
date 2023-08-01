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

import kotlin.Array

/**
 * This class is for selecting a ranked element (kth ordered statistic) from an unordered list in faster time than sorting the
 * whole array. Typical applications include finding the nearest enemy unit(s), and other operations which are likely to run as
 * often as every x frames. Certain values of k will result in a partial sorting of the Array.
 *
 *
 * The lowest ranking element starts at 1, not 0. 1 = first, 2 = second, 3 = third, etc. calling with a value of zero will result
 * in a [RuntimeException]
 *
 *
 *
 * This class uses very minimal extra memory, as it makes no copies of the array. The underlying algorithms used are a naive
 * single-pass for k=min and k=max, and Hoare's quickselect for values in between.
 *
 * @author Jon Renner
 */
class Select {
    companion object {
        const val version = Collections.version
        private var instance: Select? = null

        /** Provided for convenience  */
        fun instance(): Select {
            if (instance == null) instance = Select()
            return instance!!
        }
    }

    private var quickSelect: QuickSelect<*>? = null

    fun <T> select(items: Array<T>, comp: Comparator<T>, kthLowest: Int, size: Int): T {
        val idx = selectIndex(items, comp, kthLowest, size)
        return items[idx]
    }

    fun <T> selectIndex(items: Array<T>, comp: Comparator<T>, kthLowest: Int, size: Int): Int {
        if (size < 1) {
            throw RuntimeException("cannot select from empty array (size < 1)")
        }
        else if (kthLowest > size) {
            throw RuntimeException("Kth rank is larger than size. k: $kthLowest, size: $size")
        }
        val idx: Int

        // naive partial selection sort almost certain to outperform quickselect where n is min or max
        if (kthLowest == 1) {
            // find min
            idx = fastMin(items, comp, size)
        }
        else if (kthLowest == size) {
            // find max
            idx = fastMax(items, comp, size)
        }
        else {
            // quickselect a better choice for cases of k between min and max
            if (quickSelect == null) quickSelect = QuickSelect<T>()

            @Suppress("UNCHECKED_CAST")
            val quickSelect = quickSelect!! as QuickSelect<T>
            idx = quickSelect.select(items, comp, kthLowest, size)
        }
        return idx
    }

    /**
     * Faster than quickselect for n = min
     */
    private fun <T> fastMin(items: Array<T>, comp: Comparator<T>, size: Int): Int {
        var lowestIdx = 0
        for (i in 1 until size) {
            val comparison = comp.compare(items[i], items[lowestIdx])
            if (comparison < 0) {
                lowestIdx = i
            }
        }
        return lowestIdx
    }

    /**
     * Faster than quickselect for n = max
     */
    private fun <T> fastMax(items: Array<T>, comp: Comparator<T>, size: Int): Int {
        var highestIdx = 0
        for (i in 1 until size) {
            val comparison = comp.compare(items[i], items[highestIdx])
            if (comparison > 0) {
                highestIdx = i
            }
        }
        return highestIdx
    }
}
