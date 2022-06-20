/*
 * Copyright 2021 dorkbox, llc
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
    fun trieFromMap() {
        val strings = arrayOf("khanacademy.com", "cnn.com", "google.com", "fun.reddit.com", "reddit.com")
        val keys = Arrays.asList(*strings)
        var text: String
        run {
            val map = TreeMap<String, String>()
            for (key in keys) {
                map[key] = key
            }
            val fsm: FiniteStateMachine<*> = FiniteStateMachine.build(map)
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
    fun trieFromList() {
        val strings = arrayOf("khanacademy.com", "cnn.com", "google.com", "fun.reddit.com", "reddit.com")
        val keys = Arrays.asList(*strings)
        var text: String
        run {
            val fsm: FiniteStateMachine<*> = FiniteStateMachine.build(keys)
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
    fun trieFromVarArg() {
        val strings = arrayOf("khanacademy.com", "cnn.com", "google.com", "fun.reddit.com", "reddit.com")
        var text: String
        run {
            val fsm: FiniteStateMachine<*> = FiniteStateMachine.build(*strings)
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
    fun fmsOutput() {
        val strings = arrayOf("khanacademy.com", "cnn.com", "google.com", "fun.reddit.com", "reddit.com")
        val fsm: FiniteStateMachine<*> = FiniteStateMachine.build(*strings)
        run {
            println("Keywords Orig: " + Arrays.toString(strings))
            println("Keywords FSM : " + Arrays.toString(fsm.getKeywords()))
        }
    }
}
