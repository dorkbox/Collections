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
 * Implementation of Tony Hoare's quickselect algorithm. Running time is generally O(n), but worst case is O(n^2) Pivot choice is
 * median of three method, providing better performance than a random pivot for partially sorted data.
 * http://en.wikipedia.org/wiki/Quickselect
 *
 * @author Jon Renner
 */
class QuickSelect<T> {
    companion object {
        const val version = Collections.version
    }

    private lateinit var array: Array<T>
    private var comp: Comparator<in T>? = null

    fun select(items: Array<T>, comp: Comparator<T>?, n: Int, size: Int): Int {
        array = items
        this.comp = comp
        return recursiveSelect(0, size - 1, n)
    }

    private fun partition(left: Int, right: Int, pivot: Int): Int {
        val pivotValue = array[pivot]
        swap(right, pivot)
        var storage = left
        for (i in left until right) {
            if (comp!!.compare(array[i], pivotValue) < 0) {
                swap(storage, i)
                storage++
            }
        }
        swap(right, storage)
        return storage
    }

    private fun recursiveSelect(left: Int, right: Int, k: Int): Int {
        if (left == right) return left
        val pivotIndex = medianOfThreePivot(left, right)
        val pivotNewIndex = partition(left, right, pivotIndex)
        val pivotDist = pivotNewIndex - left + 1
        val result: Int
        result = if (pivotDist == k) {
            pivotNewIndex
        }
        else if (k < pivotDist) {
            recursiveSelect(left, pivotNewIndex - 1, k)
        }
        else {
            recursiveSelect(pivotNewIndex + 1, right, k - pivotDist)
        }
        return result
    }

    /**
     * Median of Three has the potential to outperform a random pivot, especially for partially sorted arrays
     */
    private fun medianOfThreePivot(leftIdx: Int, rightIdx: Int): Int {
        val left = array[leftIdx]
        val midIdx = (leftIdx + rightIdx) / 2
        val mid = array[midIdx]
        val right = array[rightIdx]

        // spaghetti median of three algorithm
        // does at most 3 comparisons
        return if (comp!!.compare(left, mid) > 0) {
            if (comp!!.compare(mid, right) > 0) {
                midIdx
            }
            else if (comp!!.compare(left, right) > 0) {
                rightIdx
            }
            else {
                leftIdx
            }
        }
        else {
            if (comp!!.compare(left, right) > 0) {
                leftIdx
            }
            else if (comp!!.compare(mid, right) > 0) {
                rightIdx
            }
            else {
                midIdx
            }
        }
    }

    private fun swap(left: Int, right: Int) {
        val tmp = array[left]
        array[left] = array[right]
        array[right] = tmp
    }
}
