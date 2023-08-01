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
 * Copyright 2011 See AUTHORS file.
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

import dorkbox.collections.BinaryHeap.Node
import java.util.*

/** A binary heap that stores nodes which each have a float value and are sorted either lowest first or highest first. The
 * [Node] class can be extended to store additional information.
 * @author Nathan Sweet
 */
class BinaryHeap<T : BinaryHeap.Node?> @JvmOverloads constructor(capacity: Int = 16, private val isMaxHeap: Boolean = false) {
    companion object {
        const val version = Collections.version
    }

    var size = 0
    private var nodes: Array<Node?>

    init {
        nodes = arrayOfNulls(capacity)
    }

    /**
     * Adds the node to the heap using its current value. The node should not already be in the heap.
     * @return The specified node.
     */
    fun add(node: T): T {
        // Expand if necessary.
        if (size == nodes.size) {
            val newNodes = arrayOfNulls<Node>(size shl 1)
            System.arraycopy(nodes, 0, newNodes, 0, size)
            nodes = newNodes
        }
        // Insert at end and bubble up.
        node!!.index = size
        nodes[size] = node
        up(size++)
        return node
    }

    /** Sets the node's value and adds it to the heap. The node should not already be in the heap.
     * @return The specified node.
     */
    fun add(node: T, value: Float): T {
        node!!.value = value
        return add(node)
    }

    /** Returns true if the heap contains the specified node.
     * @param identity If true, == comparison will be used. If false, .equals() comparison will be used.
     */
    fun contains(node: T?, identity: Boolean): Boolean {
        requireNotNull(node) { "node cannot be null." }
        if (identity) {
            for (n in nodes) if (n === node) return true
        }
        else {
            for (other in nodes) if (other == node) return true
        }
        return false
    }

    /** Returns the first item in the heap. This is the item with the lowest value (or highest value if this heap is configured as
     * a max heap).  */
    fun peek(): T? {
        check(size != 0) { "The heap is empty." }
        return nodes[0] as T?
    }

    /** Removes the first item in the heap and returns it. This is the item with the lowest value (or highest value if this heap is
     * configured as a max heap).  */
    fun pop(): T? {
        val removed = nodes[0]
        if (--size > 0) {
            nodes[0] = nodes[size]
            nodes[size] = null
            down(0)
        }
        else nodes[0] = null
        return removed as T?
    }

    /** @return The specified node.
     */
    fun remove(node: T): T {
        if (--size > 0) {
            val moved = nodes[size]
            nodes[size] = null
            nodes[node!!.index] = moved
            if ((moved!!.value < node.value) xor isMaxHeap) up(node.index) else down(node.index)
        }
        else nodes[0] = null
        return node
    }

    /** Returns true if the heap has one or more items.  */
    fun notEmpty(): Boolean {
        return size > 0
    }

    val isEmpty: Boolean
        /** Returns true if the heap is empty.  */
        get() = size == 0

    fun clear() {
        Arrays.fill(nodes, 0, size, null)
        size = 0
    }

    /** Changes the value of the node, which should already be in the heap.  */
    fun setValue(node: T, value: Float) {
        val oldValue = node!!.value
        node.value = value
        if ((value < oldValue) xor isMaxHeap) up(node.index) else down(node.index)
    }

    private fun up(index: Int) {
        var index = index
        val nodes = nodes
        val node = nodes[index]
        val value = node!!.value
        while (index > 0) {
            val parentIndex = index - 1 shr 1
            val parent = nodes[parentIndex]
            if ((value < parent!!.value) xor isMaxHeap) {
                nodes[index] = parent
                parent.index = index
                index = parentIndex
            }
            else break
        }
        nodes[index] = node
        node.index = index
    }

    private fun down(index: Int) {
        var index = index
        val nodes = nodes
        val size = size
        val node = nodes[index]
        val value = node!!.value
        while (true) {
            val leftIndex = 1 + (index shl 1)
            if (leftIndex >= size) break
            val rightIndex = leftIndex + 1

            // Always has a left child.
            val leftNode = nodes[leftIndex]
            val leftValue = leftNode!!.value

            // May have a right child.
            var rightNode: Node?
            var rightValue: Float
            if (rightIndex >= size) {
                rightNode = null
                rightValue = if (isMaxHeap) -Float.MAX_VALUE else Float.MAX_VALUE
            }
            else {
                rightNode = nodes[rightIndex]
                rightValue = rightNode!!.value
            }

            // The smallest of the three values is the parent.
            if ((leftValue < rightValue) xor isMaxHeap) {
                if (leftValue == value || (leftValue > value) xor isMaxHeap) break
                nodes[index] = leftNode
                leftNode.index = index
                index = leftIndex
            }
            else {
                if (rightValue == value || (rightValue > value) xor isMaxHeap) break
                nodes[index] = rightNode
                if (rightNode != null) rightNode.index = index
                index = rightIndex
            }
        }
        nodes[index] = node
        node.index = index
    }

    override fun equals(obj: Any?): Boolean {
        if (obj !is BinaryHeap<*>) return false
        val other = obj
        if (other.size != size) return false
        val nodes1 = nodes
        val nodes2 = other.nodes
        var i = 0
        val n = size
        while (i < n) {
            if (nodes1[i]!!.value != nodes2[i]!!.value) return false
            i++
        }
        return true
    }

    override fun hashCode(): Int {
        var h = 1
        val nodes = nodes
        var i = 0
        val n = size
        while (i < n) {
            h = h * 31 + java.lang.Float.floatToIntBits(nodes[i]!!.value)
            i++
        }
        return h
    }

    override fun toString(): String {
        if (size == 0) return "[]"
        val nodes = nodes
        val buffer = StringBuilder(32)
        buffer.append('[')
        buffer.append(nodes[0]!!.value)
        for (i in 1 until size) {
            buffer.append(", ")
            buffer.append(nodes[i]!!.value)
        }
        buffer.append(']')
        return buffer.toString()
    }

    /** A binary heap node.
     * @author Nathan Sweet
     */
    open class Node
    /** @param value The initial value for the node. To change the value, use [BinaryHeap.add] if the node is
     * not in the heap, or [BinaryHeap.setValue] if the node is in the heap.
     */(var value: Float) {
        var index = 0
        override fun toString(): String {
            return value.toString()
        }
    }
}
