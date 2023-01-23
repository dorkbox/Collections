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

@file:Suppress("unused")

package dorkbox.collections.ahoCorasick

import java.io.IOException
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable
import java.util.*

/**
 * An implementation of Aho Corasick algorithm based on Double Array Trie
 *
 * Will create a DoubleArray Trie from a Map or InputStream (if previously saved)
 *
 * @author hankcs, dorkbox
 */
abstract class BaseCharTrie<K, V>(map: Map<K, V>?, inputStream: ObjectInputStream?) : Serializable {

    /**
     * check array of the Double Array Trie structure
     */
    private val check: IntArray

    /**
     * base array of the Double Array Trie structure
     */
    private val base: IntArray

    /**
     * fail table of the Aho Corasick automata
     */
    private val fail: IntArray

    /**
     * output table of the Aho Corasick automata
     */
    private val output: Array<IntArray?>

    /**
     * outer value array
     */
    internal val v: Array<V>

    /**
     * the length of every key
     */
    internal val l: IntArray

    /**
     * the size of base and check array
     */
    private val checkSize: Int

    init {
        when {
            map != null -> {
                @Suppress("UNCHECKED_CAST")
                v = kotlin.jvm.internal.collectionToArray(map.values) as Array<V>
                l = IntArray(map.size)

                val builder = builder()
                builder.build(map)

                fail = builder.fail
                base = builder.base
                check = builder.check

                checkSize = builder.size
                output = builder.output
            }

            inputStream != null -> {
                @Suppress("UNCHECKED_CAST")
                v = inputStream.readObject() as Array<V>
                l = inputStream.readObject() as IntArray

                fail = inputStream.readObject() as IntArray
                base = inputStream.readObject() as IntArray
                check = inputStream.readObject() as IntArray
                checkSize = inputStream.readObject() as Int

                @Suppress("UNCHECKED_CAST")
                output = inputStream.readObject() as Array<IntArray?>
            }
            else -> throw NullPointerException("Map or InputStream must be specified!")
        }
    }

    internal abstract fun builder(): BaseCharBuilder<K, V>

    /**
     * Save
     */
    @Throws(IOException::class)
    fun save(out: ObjectOutputStream) {
        out.writeObject(v)
        out.writeObject(l)
        out.writeObject(fail)
        out.writeObject(base)
        out.writeObject(check)
        out.writeObject(checkSize)
        out.writeObject(output)
    }


    /**
     * Get the size of the keywords
     */
    val size: Int
    get() {
        return v.size
    }

    /**
     * Returns the backing keywords IN THEIR NATURAL ORDER, in the case that you need access to the original FSM data.
     *
     * @return for example, if the FSM was populated with [reddit.com, cnn.com], this will return [cnn.com, reddit.com]
     */
    val keywords: Array<V>
    get() {
        return v
    }

    /**
     * Parses text and returns true if there are PARTIALLY matching results. For exact matches only it is better to use `matches`
     *
     * @return true if there is a match or partial match. "fun.reddit.com" will partially match to "reddit.com"
     */
    fun hasPartialMatch(text: String): Boolean {
        return parseText(text).isNotEmpty()
    }

    /**
     * Parses text and finds PARTIALLY matching results. For exact matches only it is better to use `matches`
     *
     * @return a list of outputs that contain matches or partial matches. The returned list will specify HOW MUCH of the text matches (A full match would be from 0 (the start), to N (the length of the text).
     */
    fun partialMatch(text: String): List<Hit<V>> {
        return parseText(text)
    }

    /**
     * Parse text
     *
     * @return a list of outputs
     */
    fun parseText(text: CharSequence): List<Hit<V>> {
        var position = 1
        var currentState = 0
        val collectedEmits = LinkedList<Hit<V>>()  // unknown size, so

        for (element in text) {
            currentState = getState(currentState, element)
            storeEmits(position++, currentState, collectedEmits)
        }

        return collectedEmits
    }

    /**
     * Parse text
     *
     * @param text The text
     * @param processor A processor which handles the output
     */
    fun parseText(text: CharSequence,
                  processor: IHit<V>
    ) {
        var position = 1
        var currentState = 0
        for (element in text) {
            currentState = getState(currentState, element)
            val hitArray = output[currentState]
            if (hitArray != null) {
                for (hit in hitArray) {
                    processor.hit(position - l[hit], position, v[hit])
                }
            }
            position++
        }
    }

    /**
     * Parse text
     *
     * @param text The text
     * @param processor A processor which handles the output
     */
    fun parseText(text: CharSequence,
                  processor: IHitCancellable<V>
    ) {
        var position = 1
        var currentState = 0
        for (element in text) {
            position++
            currentState = getState(currentState, element)
            val hitArray = output[currentState]
            if (hitArray != null) {
                for (hit in hitArray) {
                    val proceed = processor.hit(position - l[hit], position, v[hit])
                    if (!proceed) {
                        return
                    }
                }
            }
        }
    }

    /**
     * Parse text
     *
     * @param text The text
     * @param processor A processor which handles the output
     */
    fun parseText(text: CharArray,
                  processor: IHit<V>
    ) {
        var position = 1
        var currentState = 0
        for (c in text) {
            currentState = getState(currentState, c)
            val hitArray = output[currentState]
            if (hitArray != null) {
                for (hit in hitArray) {
                    processor.hit(position - l[hit], position, v[hit])
                }
            }
            position++
        }
    }

    /**
     * Parse text
     *
     * @param text The text
     * @param processor A processor which handles the output
     */
    fun parseText(text: CharArray,
                  processor: IHitFull<V>
    ) {
        var position = 1
        var currentState = 0
        for (c in text) {
            currentState = getState(currentState, c)
            val hitArray = output[currentState]
            if (hitArray != null) {
                for (hit in hitArray) {
                    processor.hit(position - l[hit], position, v[hit], hit)
                }
            }
            position++
        }
    }

    /**
     * Checks that string contains at least one substring
     *
     * @param text source text to check
     *
     * @return `true` if string contains at least one substring
     */
    fun matches(text: String): Boolean {
        var currentState = 0
        for (element in text) {
            currentState = getState(currentState, element)
            val hitArray = output[currentState]
            if (hitArray != null) {
                return true
            }
        }
        return false
    }

    /**
     * Search first match in string
     *
     * @param text source text to check
     *
     * @return first match or `null` if there are no matches
     */
    fun findFirst(text: String): Hit<V>? {
        var position = 1
        var currentState = 0
        for (element in text) {
            currentState = getState(currentState, element)
            val hitArray = output[currentState]
            if (hitArray != null) {
                val hitIndex = hitArray[0]
                return Hit(position - l[hitIndex], position, v[hitIndex])
            }
            position++
        }
        return null
    }

    /**
     * Pick the value by index in value array <br></br>
     * Notice that to be more efficiently, this method DOES NOT check the parameter
     *
     * @param index The index
     *
     * @return The value
     */
    operator fun get(index: Int): V {
        return v[index]
    }


    /**
     * transmit state, supports failure function
     */
    private fun getState(currentState: Int,
                         character: Char): Int {

        @Suppress("NAME_SHADOWING")
        var currentState = currentState

        var newCurrentState = transitionWithRoot(currentState, character)  // First press success
        while (newCurrentState == -1)
        // If the jump fails, press failure to jump
        {
            currentState = fail[currentState]
            newCurrentState = transitionWithRoot(currentState, character)
        }
        return newCurrentState
    }

    /**
     * store output
     */
    private fun storeEmits(position: Int,
                           currentState: Int,
                           collectedEmits: MutableList<Hit<V>>) {
        val hitArray = output[currentState]
        if (hitArray != null) {
            for (hit in hitArray) {
                collectedEmits.add(Hit(position - l[hit], position, v[hit]))
            }
        }
    }

    /**
     * transition of a state
     */
    private fun transition(current: Int,
                           c: Char): Int {
        var b = current
        var p: Int

        p = b + c.code + 1
        if (b == check[p]) {
            b = base[p]
        }
        else {
            return -1
        }

        p = b
        return p
    }

    /**
     * transition of a state, if the state is root and it failed, then returns the root
     */
    private fun transitionWithRoot(nodePos: Int,
                                   c: Char): Int {
        val b = base[nodePos]
        val p: Int

        p = b + c.code + 1
        return if (b != check[p]) {
            if (nodePos == 0) {
                0
            }
            else -1
        }
        else p
    }

    /**
     * match exactly by a key-char array
     *
     * @param keyChars the key (as a Character array)
     *
     * @return the index of the key, you can use it as a perfect hash function
     */
    fun exactMatchSearch(keyChars: CharArray): Int {
        return exactMatchSearch(keyChars, 0, 0, 0)
    }

    /**
     * match exactly by a key
     *
     * @param key the key
     *
     * @return the index of the key, you can use it as a perfect hash function
     */
    fun exactMatchSearch(key: String): Int {
        return exactMatchSearch(key.toCharArray(), pos = 0, len = 0, nodePos = 0)
    }

    /**
     * match exactly by a key
     *
     * @param keyChars the char array of the key
     * @param pos the start index of char array
     * @param len the length of the key
     * @param nodePos the starting position of the node for searching
     *
     * @return the value index of the key, minus indicates null
     */
    internal fun exactMatchSearch(keyChars: CharArray,
                                  pos: Int,
                                  len: Int,
                                  nodePos: Int): Int {
        @Suppress("NAME_SHADOWING")
        var len = len

        @Suppress("NAME_SHADOWING")
        var nodePos = nodePos

        if (len <= 0) {
            len = keyChars.size
        }
        if (nodePos <= 0) {
            nodePos = 0
        }

        var result = -1

        var b = base[nodePos]
        var p: Int

        for (i in pos until len) {
            p = b + keyChars[i].code + 1
            if (b == check[p]) {
                b = base[p]
            }
            else {
                return result
            }
        }

        p = b
        val n = base[p]
        if (b == check[p] && n < 0) {
            result = -n - 1
        }
        return result
    }

    //    /**
    //     * Just for debug when I wrote it
    //     */
    //    public void debug()
    //    {
    //        System.out.println("base:");
    //        for (int i = 0; i < base.length; i++)
    //        {
    //            if (base[i] < 0)
    //            {
    //                System.out.println(i + " : " + -base[i]);
    //            }
    //        }
    //
    //        System.out.println("output:");
    //        for (int i = 0; i < output.length; i++)
    //        {
    //            if (output[i] != null)
    //            {
    //                System.out.println(i + " : " + Arrays.toString(output[i]));
    //            }
    //        }
    //
    //        System.out.println("fail:");
    //        for (int i = 0; i < fail.length; i++)
    //        {
    //            if (fail[i] != 0)
    //            {
    //                System.out.println(i + " : " + fail[i]);
    //            }
    //        }
    //
    //        System.out.println(this);
    //    }
    //
    //    @Override
    //    public String toString()
    //    {
    //        String infoIndex = "i    = ";
    //        String infoChar = "char = ";
    //        String infoBase = "base = ";
    //        String infoCheck = "check= ";
    //        for (int i = 0; i < Math.min(base.length, 200); ++i)
    //        {
    //            if (base[i] != 0 || check[i] != 0)
    //            {
    //                infoChar += "    " + (i == check[i] ? " ×" : (char) (i - check[i] - 1));
    //                infoIndex += " " + String.format("%5d", i);
    //                infoBase += " " + String.format("%5d", base[i]);
    //                infoCheck += " " + String.format("%5d", check[i]);
    //            }
    //        }
    //        return "DoubleArrayTrie：" +
    //                "\n" + infoChar +
    //                "\n" + infoIndex +
    //                "\n" + infoBase +
    //                "\n" + infoCheck + "\n" +
    ////                "check=" + Arrays.toString(check) +
    ////                ", base=" + Arrays.toString(base) +
    ////                ", used=" + Arrays.toString(used) +
    //                "size=" + size
    ////                ", length=" + Arrays.toString(length) +
    ////                ", value=" + Arrays.toString(value) +
    //                ;
    //    }
    //
    //    /**
    //     * A debug class that sequentially outputs variable names and variable values
    //     */
    //    private static class DebugArray
    //    {
    //        Map<String, String> nameValueMap = new LinkedHashMap<String, String>();
    //
    //        public void add(String name, int value)
    //        {
    //            String valueInMap = nameValueMap.get(name);
    //            if (valueInMap == null)
    //            {
    //                valueInMap = "";
    //            }
    //
    //            valueInMap += " " + String.format("%5d", value);
    //
    //            nameValueMap.put(name, valueInMap);
    //        }
    //
    //        @Override
    //        public String toString()
    //        {
    //            String text = "";
    //            for (Map.Entry<String, String> entry : nameValueMap.entrySet())
    //            {
    //                String name = entry.getKey();
    //                String value = entry.getValue();
    //                text += String.format("%-5s", name) + "= " + value + '\n';
    //            }
    //
    //            return text;
    //        }
    //
    //        public void println()
    //        {
    //            System.out.print(this);
    //        }
    //    }

}
