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

package dorkbox.collections

import org.junit.Assert.assertTrue
import org.junit.Test

class ObjectTests {
    private fun map(): ObjectMap<String, Int?> {
        val map = ObjectMap<String, Int?>()
        map["1"] = 1
        map["2"] = 2
        map["3"] = null

        return map
    }

    private fun set(): ObjectSet<String> {
        val set = ObjectSet<String>()
        set.add("1")
        set.add("2")
        set.add("3")

        return set
    }

    @Test
    fun testObjectMap() {
        val map = map()
        assertTrue(map.size == 3)

        assertTrue(map.size == 3)

        assertTrue(map["2"] == 2)
        map.remove("2")
        assertTrue(map.size == 2)

        assertTrue(map["2"] == null)

        map.retainAll { it.key == "1"}
        assertTrue(map.size == 1)
        assertTrue(map["1"] == 1)


        map.clear()

        assertTrue(map.isEmpty())
        assertTrue(map.size == 0)
    }

    @Test
    fun testObjectMapKeys() {
        val map = map()
        assertTrue(map.size == 3)

        val keys = map.keys()
        assertTrue(keys.size == 3)

        assertTrue(map["2"] == 2)
        keys.remove("2")
        assertTrue(map.size == 2)
        assertTrue(keys.size == 2)

        assertTrue(map["2"] == null)

        val keep = listOf("1")
        keys.retainAll(keep)
        assertTrue(map.size == 1)
        assertTrue(map["1"] == 1)

        keys.clear()

        assertTrue(map.isEmpty())
        assertTrue(keys.isEmpty())
        assertTrue(map.size == 0)
        assertTrue(keys.size == 0)
    }

    @Test
    fun testObjectMapValues() {
        val map = map()
        assertTrue(map.size == 3)

        val values = map.values()
        assertTrue(values.size == 3)

        values.remove(2)
        assertTrue(map.size == 2)
        assertTrue(values.size == 2)

        assertTrue(map["2"] == null)

        val keep = listOf(1)
        values.retainAll(keep)
        assertTrue(map.size == 1)
        assertTrue(map["1"] == 1)

        values.clear()

        assertTrue(map.isEmpty())
        assertTrue(values.isEmpty())
        assertTrue(map.size == 0)
        assertTrue(values.size == 0)
    }

    @Test
    fun testObjectMapEntries() {
        val map = map()
        assertTrue(map.size == 3)

        val entries = map.entries()
        assertTrue(entries.size == 3)


        var toRemove: ObjectMap.Entry<String, Int?>? = null
        val iter = entries.iterator()
        while (iter.hasNext()) {
            val entry = iter.next()
            if (entry.key == "2") {
                toRemove = entry
                break
            }
        }
        assertTrue(toRemove?.key == "2")
        entries.remove(toRemove)
        assertTrue(map.size == 2)
        assertTrue(entries.size == 2)

        assertTrue(map["2"] == null)

        val keepEntry = entries.iterator().next()

        val keep = listOf(keepEntry)
        entries.retainAll(keep)
        assertTrue(map.size == 1)
        assertTrue(map["1"] == 1)

        entries.clear()

        assertTrue(map.isEmpty())
        assertTrue(entries.isEmpty())
        assertTrue(map.size == 0)
        assertTrue(entries.size == 0)
    }

    @Test
    fun testObjectMapEntries2() {
        val map = map()
        assertTrue(map.size == 3)

        val entries = map.entries()
        val keepEntry = ObjectMap.Entry<String, Int?>()
        keepEntry.key = "1"
        keepEntry.value = 1

        val keep = listOf(keepEntry)
        entries.retainAll(keep)
        assertTrue(map.size == 1)
        assertTrue(map["1"] == 1)

        entries.clear()

        assertTrue(map.isEmpty())
        assertTrue(entries.isEmpty())
        assertTrue(map.size == 0)
        assertTrue(entries.size == 0)
    }


    @Test
    fun testObjectSet() {
        val set = set()
        assertTrue(set.size == 3)

        assertTrue(set.size == 3)

        assertTrue(set["2"] == "2")
        set.remove("2")
        assertTrue(set.size == 2)

        assertTrue(set["2"] == null)

        set.retainAll { it == "1" }
        assertTrue(set.size == 1)
        assertTrue(set["1"] == "1")

        set.clear()

        assertTrue(set.isEmpty())
        assertTrue(set.size == 0)
    }

    @Test
    fun testObjectSet2() {
        val set = set()
        assertTrue(set.size == 3)

        assertTrue(set.size == 3)

        assertTrue(set["2"] == "2")
        set.remove("2")
        assertTrue(set.size == 2)

        assertTrue(set["2"] == null)

        val keep = listOf("1")
        set.retainAll(keep)
        assertTrue(set.size == 1)
        assertTrue(set["1"] == "1")

        set.clear()

        assertTrue(set.isEmpty())
        assertTrue(set.size == 0)
    }
}
