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

import java.util.*

/**
 * A builder to build the AhoCorasickDoubleArrayTrie
 */
internal abstract class BaseCharBuilder<K, V> {
    /**
     * the root state of trie
     */
    internal var rootState: StateChar? = StateChar()

    /**
     * whether the position has been used
     */
    private var used: BooleanArray? = null

    /**
     * the allocSize of the dynamic array
     */
    private var allocSize: Int = 0

    /**
     * a parameter controls the memory growth speed of the dynamic array
     */
    private var progress: Int = 0

    /**
     * the next position to check unused memory
     */
    private var nextCheckPos: Int = 0

    /**
     * the size of the key-pair sets
     */
    private var keySize: Int = 0


    lateinit var output: Array<IntArray?>
    lateinit var fail: IntArray
    lateinit var base: IntArray
    lateinit var check: IntArray
    var size: Int = 0

    /**
     * Build from a map
     *
     * @param map a map containing key-value pairs
     */
    fun build(map: Map<K, V>) {
        val keySet = map.keys

        // Construct a two-point trie tree
        addAllKeyword(keySet)

        // Building a double array trie tree based on a two-point trie tree
        buildDoubleArrayTrie(keySet.size)
        used = null

        // Build the failure table and merge the output table
        constructFailureStates()
        rootState = null
        loseWeight()
    }

    /**
     * fetch siblings of a parent node
     *
     * @param parent parent node
     * @param siblings parent node's child nodes, i . e . the siblings
     *
     * @return the amount of the siblings
     */
    private fun fetch(parent: StateChar,
                      siblings: MutableList<Pair<Int, StateChar>>): Int {

        if (parent.isAcceptable) {
            // This node is a child of the parent and has the output of the parent.
            val fakeNode = StateChar(-(parent.depth + 1))
            fakeNode.addEmit(parent.largestValueId!!)
            siblings.add(Pair(0, fakeNode))
        }

        for ((key, value) in parent.getSuccess()) {
            siblings.add(Pair(key.code + 1, value))
        }

        return siblings.size
    }

    /**
     * add a keyword
     *
     * @param keyword a keyword
     * @param index the index of the keyword
     */
    internal abstract fun addKeyword(keyword: K, index: Int)

    /**
     * add a collection of keywords
     *
     * @param keywordSet the collection holding keywords
     */
    private fun addAllKeyword(keywordSet: Collection<K>) {
        var i = 0
        keywordSet.forEach { keyword ->
            addKeyword(keyword, i++)
        }
    }

    /**
     * construct failure table
     */
    private fun constructFailureStates() {
        fail = IntArray((size + 1).coerceAtLeast(2))
        fail[1] = base[0]
        output = arrayOfNulls(size + 1)

        val queue = ArrayDeque<StateChar>()

        // The first step is to set the failure of the node with depth 1 to the root node.
        this.rootState!!.states.forEach { depthOneState ->
            depthOneState.setFailure(this.rootState!!, fail)
            queue.add(depthOneState)
            constructOutput(depthOneState)
        }

        // The second step is to create a failure table for nodes with depth > 1, which is a bfs
        while (!queue.isEmpty()) {
            val currentState = queue.remove()

            for (transition in currentState.transitions) {
                val targetState = currentState.nextState(transition)
                queue.add(targetState)

                var traceFailureState = currentState.failure()
                while (traceFailureState!!.nextState(transition) == null) {
                    traceFailureState = traceFailureState.failure()
                }

                val newFailureState = traceFailureState.nextState(transition)
                targetState!!.setFailure(newFailureState!!, fail)
                targetState.addEmit(newFailureState.emit())
                constructOutput(targetState)
            }
        }
    }

    /**
     * construct output table
     */
    private fun constructOutput(targetState: StateChar) {
        val emit = targetState.emit()
        if (emit.isEmpty()) {
            return
        }

        val output = IntArray(emit.size)
        val it = emit.iterator()
        for (i in output.indices) {
            output[i] = it.next()
        }

        this.output[targetState.index] = output
    }

    private fun buildDoubleArrayTrie(keySize: Int) {
        progress = 0
        this.keySize = keySize
        resize(65536 * 32) // 32 double bytes

        base[0] = 1
        nextCheckPos = 0

        val rootNode = this.rootState
        val initialCapacity = rootNode!!.getSuccess().entries.size

        val siblings = ArrayList<Pair<Int, StateChar>>(initialCapacity)
        fetch(rootNode, siblings)

        if (siblings.isNotEmpty()) {
            insert(siblings)
        }
    }

    /**
     * allocate the memory of the dynamic array
     */
    private fun resize(newSize: Int): Int {
        val base2 = IntArray(newSize)
        val check2 = IntArray(newSize)
        val used2 = BooleanArray(newSize)

        if (allocSize > 0) {
            System.arraycopy(base, 0, base2, 0, allocSize)
            System.arraycopy(check, 0, check2, 0, allocSize)
            System.arraycopy(used!!, 0, used2, 0, allocSize)
        }

        base = base2
        check = check2
        used = used2

        allocSize = newSize
        return newSize
    }

    /**
     * insert the siblings to double array trie
     *
     * @param siblings the siblings being inserted
     *
     * @return the position to insert them
     */
    private fun insert(siblings: List<Pair<Int, StateChar>>): Int {
        var begin: Int
        var pos = Math.max(siblings[0].first + 1, nextCheckPos) - 1
        var nonzeroNum = 0
        var first = 0

        if (allocSize <= pos) {
            resize(pos + 1)
        }

        outer@
        // The goal of this loop body is to find n free spaces that satisfy base[begin + a1...an] == 0, a1...an are n nodes in siblings
        while (true) {
            pos++

            if (allocSize <= pos) {
                resize(pos + 1)
            }

            if (check[pos] != 0) {
                nonzeroNum++
                continue
            }
            else if (first == 0) {
                nextCheckPos = pos
                first = 1
            }

            begin = pos - siblings[0].first // The distance of the current position from the first sibling node
            if (allocSize <= begin + siblings[siblings.size - 1].first) {
                // progress can be zero
                // Prevent progress from generating zero divide errors
                val l = if (1.05 > 1.0 * keySize / (progress + 1)) 1.05 else 1.0 * keySize / (progress + 1)
                resize((allocSize * l).toInt())
            }

            if (used!![begin]) {
                continue
            }

            for (i in 1 until siblings.size) {
                if (check[begin + siblings[i].first] != 0) {
                    continue@outer
                }
            }

            break
        }

        // -- Simple heuristics --
        // if the percentage of non-empty contents in check between the
        // index
        // 'next_check_pos' and 'check' is greater than some constant value
        // (e.g. 0.9),
        // new 'next_check_pos' index is written by 'check'.
        if (1.0 * nonzeroNum / (pos - nextCheckPos + 1) >= 0.95) {
            // From the position next_check_pos to pos, if the occupied space is above 95%, the next
            // time you insert a node, you can start looking directly at the pos position.
            nextCheckPos = pos
        }
        used!![begin] = true  // valid because resize is called.

        val sizeLimit = begin + siblings[siblings.size - 1].first + 1
        if (size <= sizeLimit) {
            size = sizeLimit
        }


        for (sibling in siblings) {
            check[begin + sibling.first] = begin
        }

        for (sibling in siblings) {
            val newSiblings = ArrayList<Pair<Int, StateChar>>(sibling.second.getSuccess().entries.size + 1)

            if (fetch(sibling.second, newSiblings) == 0) {
                // The termination of a word and not the prefix of other words, in fact, is the leaf node
                base[begin + sibling.first] = 0 - sibling.second.largestValueId!! - 1
                progress++
            }
            else {
                val h = insert(newSiblings)   // depth first search
                base[begin + sibling.first] = h
            }
            sibling.second.index = begin + sibling.first
        }
        return begin
    }

    /**
     * free the unnecessary memory
     */
    private fun loseWeight() {
        base = base.copyOf(size + 65535)
        check = check.copyOf(size + 65535)
    }
}
