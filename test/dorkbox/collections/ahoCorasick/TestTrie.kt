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
package dorkbox.collections.ahoCorasick

import org.junit.Test
import java.util.*

class TestTrie {
    @Test
    fun trieFromStringMap() {
        val strings = arrayOf("khanacademy.com", "cnn.com", "google.com", "fun.reddit.com", "reddit.com")
        val keys = Arrays.asList(*strings)
        var text: String
        run {
            val map = TreeMap<String, String>()
            for (key in keys) {
                map[key] = key
            }
            val fsm = FiniteStateMachine.build(map)
            text = "reddit.google.com"
            println("Searching : $text")
            println(fsm.partialMatch(text))
            println("Found: " + fsm.matches(text))
            println()

            text = "reddit.com"
            println("Searching : $text")
            println(fsm.partialMatch(text))
            println("Found: " + fsm.matches(text))
            println()

            text = "fun.reddit.com"
            println("Searching : $text")
            println(fsm.partialMatch(text))
            println("Found: " + fsm.matches(text))
        }
    }

    @Test
    fun trieFromByteArrayMap() {
        val strings = arrayOf(
            "khanacademy.com".toByteArray(),
            "cnn.com".toByteArray(),
            "google.com".toByteArray(),
            "fun.reddit.com".toByteArray(),
            "reddit.com".toByteArray())
        val keys = Arrays.asList(*strings)
        var text: String
        run {
            val map = TreeMap<ByteArray, String>()
            for (key in keys) {
                map[key] = String(key)
            }
            val fsm = FiniteStateMachine.build(map)

            text = "reddit.google.com"
            println("Searching : $text")
            var result = fsm.partialMatch(text.toByteArray())
            result.forEach { it ->
                println(it.toString())
            }

            result = fsm.partialMatch(text.toByteArray())
            result.forEach { it ->
                println(it.toString())
            }
            println("Found: " + fsm.matches(text.toByteArray()))
            println()

            text = "reddit.com"
            println("Searching : $text")
            result = fsm.partialMatch(text.toByteArray())
            result.forEach { it ->
                println(it.toString())
            }
            println("Found: " + fsm.matches(text.toByteArray()))
            println()

            text = "fun.reddit.com"
            println("Searching : $text")
            result = fsm.partialMatch(text.toByteArray())
            result.forEach { it ->
                println(it.toString())
            }
            println("Found: " + fsm.matches(text.toByteArray()))
        }
    }

    @Test
    fun trieFromStringList() {
        val strings = arrayOf("khanacademy.com", "cnn.com", "google.com", "fun.reddit.com", "reddit.com")
        val keys = Arrays.asList(*strings)
        var text: String
        run {
            val fsm = FiniteStateMachine.build(keys)
            text = "reddit.google.com"
            println("Searching : $text")
            println(fsm.partialMatch(text))
            println("Found: " + fsm.matches(text))
            println()

            text = "reddit.com"
            println("Searching : $text")
            println(fsm.partialMatch(text))
            println("Found: " + fsm.matches(text))
            println()

            text = "fun.reddit.com"
            println("Searching : $text")
            println(fsm.partialMatch(text))
            println("Found: " + fsm.matches(text))
        }
    }

    @Test
    fun trieFromByteArrayList() {
        val strings = arrayOf(
            "khanacademy.com".toByteArray(),
            "cnn.com".toByteArray(),
            "google.com".toByteArray(),
            "fun.reddit.com".toByteArray(),
            "reddit.com".toByteArray())

        val keys = Arrays.asList(*strings)
        var text: String
        run {
            val fsm = FiniteStateMachine.build(keys)
            text = "reddit.google.com"
            println("Searching : $text")
            var result = fsm.partialMatch(text.toByteArray())
            result.forEach { it ->
                println(it.toString())
            }
            println("Found: " + fsm.matches(text.toByteArray()))
            println()

            text = "reddit.com"
            println("Searching : $text")
            result = fsm.partialMatch(text.toByteArray())
            result.forEach { it ->
                println(it.toString())
            }
            println("Found: " + fsm.matches(text.toByteArray()))
            println()

            text = "fun.reddit.com"
            println("Searching : $text")
            result = fsm.partialMatch(text.toByteArray())
            result.forEach { it ->
                println(it.toString())
            }
            println("Found: " + fsm.matches(text.toByteArray()))
        }
    }

    @Test
    fun trieFromStringVarArg() {
        val strings = arrayOf("khanacademy.com", "cnn.com", "google.com", "fun.reddit.com", "reddit.com")
        var text: String
        run {
            val fsm = FiniteStateMachine.build(*strings)
            text = "reddit.google.com"
            println("Searching : $text")
            println(fsm.partialMatch(text))
            println("Found: " + fsm.matches(text))
            println()

            text = "reddit.com"
            println("Searching : $text")
            println(fsm.partialMatch(text))
            println("Found: " + fsm.matches(text))
            println()

            text = "fun.reddit.com"
            println("Searching : $text")
            println(fsm.partialMatch(text))
            println("Found: " + fsm.matches(text))
        }
    }

    @Test
    fun trieFromByteArrayVarArg() {
        val strings = arrayOf(
            "khanacademy.com".toByteArray(),
            "cnn.com".toByteArray(),
            "google.com".toByteArray(),
            "fun.reddit.com".toByteArray(),
            "reddit.com".toByteArray())

        var text: String
        run {
            val fsm = FiniteStateMachine.build(*strings)

            text = "reddit.google.com"
            println("Searching : $text")
            var result = fsm.partialMatch(text.toByteArray())
            result.forEach { it ->
                println(it.toString())
            }
            println("Found: " + fsm.matches(text.toByteArray()))
            println()

            text = "reddit.com"
            println("Searching : $text")
            result = fsm.partialMatch(text.toByteArray())
            result.forEach { it ->
                println(it.toString())
            }
            println("Found: " + fsm.matches(text.toByteArray()))
            println()

            text = "fun.reddit.com"
            println("Searching : $text")
            result = fsm.partialMatch(text.toByteArray())
            result.forEach { it ->
                println(it.toString())
            }
            println("Found: " + fsm.matches(text.toByteArray()))
        }
    }

    @Test
    fun fmsOutput() {
        val strings = arrayOf("khanacademy.com", "cnn.com", "google.com", "fun.reddit.com", "reddit.com")
        val fsm = FiniteStateMachine.build(*strings)

        run {
            println("Keywords Orig: " + Arrays.toString(strings))
            println("Keywords FSM : " + Arrays.toString(fsm.keywords))
        }
    }
}
