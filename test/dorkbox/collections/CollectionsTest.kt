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
@file:Suppress("UNCHECKED_CAST")

package dorkbox.collections

import org.junit.Test
import java.lang.reflect.Constructor
import java.lang.reflect.Method
import kotlin.Any
import kotlin.Array
import kotlin.Boolean
import kotlin.Float
import kotlin.Int
import kotlin.RuntimeException
import kotlin.String
import kotlin.Throwable
import kotlin.arrayOf
import kotlin.arrayOfNulls
import kotlin.intArrayOf
import kotlin.run

/** Tests for the collection classes. Currently, only equals() and hashCode() methods are tested.  */
class CollectionsTest {
    // Objects to use for test keys/values; no duplicates may exist. All arrays are 10 elements.
    private val values = arrayOf<Any>("just", "some", "random", "values", true, false, 50, "nope", "yeah", 53)
    private val valuesWithNulls = arrayOf<Any?>("just", "some", null, "values", true, false, 50, "nope", "yeah", 53)
    private val intValues = arrayOf<Any>(42, 13, 0, -44, 56, 561, 61, -532, -1, 32)
    private val floatValues = arrayOf<Any>(4f, 3.14f, 0f, 5f, 2f, -5f, 43f, 643f, 3525f, 32f)
    private val longValues = arrayOf<Any>(5L, 3L, 41432L, 0L, -4312L, -532L, 1L, 4L, 1362L)
    private val byteValues = arrayOf<Any>(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
    private val shortValues = arrayOf<Any>(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
    private val charValues = arrayOf<Any>('A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J')

    // 49 String keys that all have the same two hashCode() results
    // It is extremely easy to generate String keys that have colliding hashCode()s, so we check to make
    // sure ObjectSet and OrderedSet can tolerate them in case of low-complexity malicious use.
    // If they can tolerate these problem values, then ObjectMap and others should too.
    private val problemValues: Array<Any> =
        ("21oo 0oq1 0opP 0ooo 0pPo 21pP 21q1 1Poo 1Pq1 1PpP 0q31 0pR1 0q2P 0q1o 232P 231o 2331 0pQP 22QP" + " 22Po 22R1 1QQP 1R1o 1QR1 1R2P 1R31 1QPo 1Qup 1S7p 0r8Q 0r7p 0r92 23X2 2492 248Q 247p 22vQ" + " 22up 1S92 1S8Q 23WQ 23Vp 22w2 1QvQ 1Qw2 1RVp 1RWQ 1RX2 0qX2").split(
            " ".toRegex()
        ).dropLastWhile { it.isEmpty() }.toTypedArray()

    /** Checks that the two values are equal, and that their hashcodes are equal.  */
    private fun assertEquals(a: Any, b: Any) {
        if (a != b) throw RuntimeException(
            """${a.javaClass.getSimpleName()} equals() failed:
$a
!=
$b"""
        )
        if (b != a) throw RuntimeException(
            """${a.javaClass.getSimpleName()} equals() failed (not symmetric):
$b
!=
$a"""
        )
        if (a.hashCode() != b.hashCode()) throw RuntimeException(
            """${a.javaClass.getSimpleName()} hashCode() failed:
$a
!=
$b"""
        )
    }

    /** Checks that the two values are not equal, and emits a warning if their hashcodes are equal.  */
    private fun assertNotEquals(a: Any, b: Any) {
        if (a == b) throw RuntimeException(
            """${a.javaClass.getSimpleName()} !equals() failed:
$a
==
$b"""
        )
        if (b == a) throw RuntimeException(
            """${a.javaClass.getSimpleName()} !equals() failed (not symmetric):
$b
==
$a"""
        )
        if (a.hashCode() == b.hashCode()) println(
            """Warning: ${a.javaClass.getSimpleName()} hashCode() may be incorrect:
$a
==
$b"""
        )
    }

    /** Uses reflection to create a new instance of the given type.  */
    private fun newInstance(clazz: Class<*>): Any {
        return try {
            clazz.newInstance()
        }
        catch (ex: Throwable) {
            throw RuntimeException(ex)
        }
    }

    private operator fun invoke(methodName: String, `object`: Any, vararg args: Any?) {
        try {
            val size = args.size
            var theMethod: Method? = null
            for (method in `object`.javaClass.methods) {
                if (methodName == method.name && method.parameterTypes.size == size) {
                    theMethod = method
                    break
                }
            }
            theMethod!!.invoke(`object`, *args)
        }
        catch (ex: Throwable) {
            throw RuntimeException(ex)
        }
    }

    @Suppress("SameParameterValue")
    private operator fun set(fieldName: String, `object`: Any, value: Any) {
        try {
            `object`.javaClass.fields.firstOrNull { it.name == fieldName }?.set(`object`, value)
        }
        catch (ex: Throwable) {
            throw RuntimeException(ex)
        }
    }

    private fun copy(`object`: Any): Any {
        return try {
            var theConstructor: Constructor<Any>? = null

            for (constructor in `object`.javaClass.constructors) {
                if (constructor.parameterTypes.size == 1 &&
                    `object`.javaClass.isAssignableFrom(constructor.getParameterTypes().get(0))
                    )
                 {
                    theConstructor = constructor as Constructor<Any>
                    break
                }
            }

            theConstructor!!.newInstance(`object`)
        }
        catch (ex: Throwable) {
            throw RuntimeException(ex)
        }
    }

    private fun testMap(mapClass: Class<*>, keys: Array<Any>, values: Array<Any?>) {
        println(mapClass)
        val map = newInstance(mapClass)
        var otherMap = newInstance(mapClass)
        assertEquals(map, map)
        run {
            var i = 0
            val n = keys.size
            while (i < n) {
                val anotherMap = copy(map)
                assertEquals(map, anotherMap)
                invoke("put", map, keys[i], values[i])
                invoke("put", otherMap, keys[i], values[i])
                assertEquals(map, otherMap)
                assertNotEquals(map, anotherMap)
                invoke("put", anotherMap, keys[(i + 1) % keys.size], values[i])
                assertNotEquals(map, anotherMap)
                i++
            }
        }

        // perform an iteration test
        var anotherMap = copy(map)as MutableMap<*,*>
        var it = anotherMap.iterator()

        var iterationCount = 0
        while (it.hasNext()) {
            @Suppress("UNUSED_VARIABLE")
            val entry = it.next()
            iterationCount++
        }
        assertEquals(iterationCount, keys.size)

        // perform an iteration and remove test for every index
        run {
            var i = 0
            val n = keys.size
            while (i < n) {
                anotherMap = copy(map) as MutableMap<*,*>
                it = anotherMap.iterator()
                iterationCount = 0
                while (it.hasNext()) {
                    @Suppress("UNUSED_VARIABLE")
                    val entry = it.next()
                    if (iterationCount == i) {
                        it.remove()
                    }
                    iterationCount++
                }
                assertEquals(iterationCount, keys.size)
                i++
            }
        }
        invoke("clear", map)
        otherMap = newInstance(mapClass)
        assertEquals(map, otherMap)
        val clear = intArrayOf(0, 1, 2, 3, keys.size - 1, keys.size, keys.size + 1, 10, 1000)
        var i = 0
        val n = clear.size
        while (i < n) {
            var ii = 0
            val nn = keys.size
            while (ii < nn) {
                invoke("put", map, keys[ii], values[ii])
                invoke("put", otherMap, keys[ii], values[ii])
                ii++
            }
            assertEquals(map, otherMap)
            invoke("clear", map, clear[i])
            otherMap = newInstance(mapClass)
            assertEquals(map, otherMap)
            i++
        }
    }

    @Test
    fun testEmptyMaps() {
        run {
            println(IntIntMap::class.java)
            val map = IntIntMap(0)

            val keys = intValues
            val values = intValues
            val otherMap: Any = IntIntMap(0)
            assertEquals(map, map)
            run {
                var i = 0
                val n = keys.size
                while (i < n) {
                    val anotherMap = copy(map)
                    assertEquals(map, anotherMap)
                    val a = map.get(keys[n - 1] as Int, 0)
                    assertEquals(a as Any, 0)


                    map.put(keys[i] as Int, values[i] as Int)

                    (otherMap as IntIntMap).put(keys[i] as Int, values[i] as Int)
                    assertEquals(map, otherMap)
                    assertNotEquals(map, anotherMap)
                    (anotherMap as IntIntMap).put(keys[(i + 1) % n] as Int, values[i] as Int)
                    assertNotEquals(map, anotherMap)
                    i++
                }
            }

            // perform an iteration test
            var anotherMap = copy(map) as MutableMap<*,*>
            var it = anotherMap.iterator()
            var iterationCount = 0
            while (it.hasNext()) {
                it.next()
                iterationCount++
            }
            assertEquals(iterationCount, keys.size)

            // perform an iteration and remove test for every index
            var i = 0
            val n = keys.size
            while (i < n) {
                anotherMap = copy(map) as MutableMap<*,*>
                it = anotherMap.iterator()
                iterationCount = 0
                while (it.hasNext()) {
                    it.next()
                    if (iterationCount == i) {
                        it.remove()
                    }
                    iterationCount++
                }
                assertEquals(iterationCount, keys.size)
                i++
            }
        }
        run {
            println(IntMap::class.java)

            val map = IntMap<Any>(0)
            val keys = intValues
            val values = intValues
            val otherMap: Any = IntMap<Any?>(0)
            assertEquals(map, map)

            run {
                var i = 0
                val n = keys.size
                while (i < n) {
                    val anotherMap = copy(map)
                    assertEquals(map, anotherMap)
                    if (map[keys[n - 1] as Int] != null) {
                        throw RuntimeException("get() on an impossible key returned non-null")
                    }

                    map.put(keys[i] as Int, values[i] as Int)
                    (otherMap as IntMap<Any>).put(keys[i] as Int, values[i])
                    assertEquals(map, otherMap)
                    assertNotEquals(map, anotherMap)
                    (anotherMap as IntMap<Any>).put(keys[(i + 1) % n] as Int, values[i] as Int)
                    assertNotEquals(map, anotherMap)
                    i++
                }
            }

            // perform an iteration test
            var anotherMap = copy(map) as MutableMap<*,*>
            var it = anotherMap.iterator()
            var iterationCount = 0
            while (it.hasNext()) {
                it.next()
                iterationCount++
            }
            assertEquals(iterationCount, keys.size)

            // perform an iteration and remove test for every index
            var i = 0
            val n = keys.size
            while (i < n) {
                anotherMap = copy(map) as MutableMap<*,*>
                it = anotherMap.iterator()
                iterationCount = 0
                while (it.hasNext()) {
                    it.next()
                    if (iterationCount == i) {
                        it.remove()
                    }
                    iterationCount++
                }
                assertEquals(iterationCount, keys.size)
                i++
            }
        }
    }

    private fun testExpandingArray(values: Array<Any?>) {
        println(ExpandingArray::class.java.name)
        val array = ExpandingArray<Any?>(true, values.size)
        for (i in values.indices) invoke("add", array, values[i])
        val otherArray = ExpandingArray<Any?>(true, values.size)
        for (i in values.indices) invoke("add", otherArray, values[i])
        assertEquals(array, otherArray)

        val unorderedArray = ExpandingArray<Any?>(false, values.size)
        set("ordered", unorderedArray, false)
        val otherUnorderedArray = ExpandingArray<Any?>(false, values.size)
        set("ordered", otherUnorderedArray, false)
        assertEquals(unorderedArray, unorderedArray)
        assertNotEquals(unorderedArray, otherUnorderedArray)
    }

    private fun testSet(setClass: Class<*>, values: Array<Any>) {
        println(setClass)
        val set = newInstance(setClass)
        run {
            var i = 0
            val n = values.size
            while (i < n) {
                invoke("add", set, values[i])
                i++
            }
        }
        val otherSet = newInstance(setClass)
        run {
            var i = 0
            val n = values.size
            while (i < n) {
                invoke("add", otherSet, values[i])
                i++
            }
        }
        val thirdSet = newInstance(setClass)
        var i = 0
        val n = values.size
        while (i < n) {
            invoke("add", thirdSet, values[n - i - 1])
            i++
        }
        assertEquals(set, set)
        assertEquals(set, otherSet)
        assertEquals(set, thirdSet)
        assertEquals(otherSet, set)
        assertEquals(otherSet, otherSet)
        assertEquals(otherSet, thirdSet)
        assertEquals(thirdSet, set)
        assertEquals(thirdSet, otherSet)
        assertEquals(thirdSet, thirdSet)
    }

    @Test
    fun testEntrySet() {
        val hmSize = 1000
        val objArray = arrayOfNulls<Int>(hmSize) as Array<Int>
        val objArray2 = arrayOfNulls<String>(hmSize)  as Array<String>
        for (i in objArray.indices) {
            objArray[i] = i
            objArray2[i] = objArray[i].toString()
        }

        val hm = ObjectMap<String, Int?>()
        for (i in objArray.indices) {
            hm.put(objArray2[i], objArray[i])
        }
        hm.put("test", null)

        val entries = hm.entries()
        val i = entries.iterator()
        while (i.hasNext()) {
            val m = i.next()
            assertEquals(hm.containsKey(m.key), true)
            assertEquals(hm.containsValue(m.value, false), true)
        }

        val iter = entries.iterator()
        iter.reset()
        val casted = hm as ObjectMap<Any, Any>
        casted.remove(iter.next() as Any) // this shouldn't do anything!
        assertEquals(1001, hm.size)
    }

    @Suppress("EqualsOrHashCode")
    @Test
    fun testBinaryHeap() {
        class Node(value: Float) : BinaryHeap.Node(value) {
            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                return if (other == null || javaClass != other.javaClass) {
                    false
                } else {
                    (other as Node).value == value
                }
            }
        }

        val values = arrayOf( // @off
            44.683983f,
            Node(44.683983f),
            160.47682f,
            Node(160.47682f),
            95.038086f,
            Node(95.038086f),
            396.49918f,
            Node(396.49918f),
            835.0006f,
            Node(835.0006f),
            439.67096f,
            Node(439.67096f),
            377.55692f,
            Node(377.55692f),
            373.29028f,
            Node(373.29028f),
            926.524f,
            Node(926.524f),
            189.30789f,
            Node(189.30789f),
            926.524f,
            Node(926.524f),
            924.88995f,
            Node(924.88995f),
            700.856f,
            Node(700.856f),
            342.5846f,
            Node(342.5846f),
            313.3819f,
            Node(313.3819f),
            407.9829f,
            Node(407.9829f),
            1482.5394f,
            Node(1482.5394f),
            1135.7894f,
            Node(1135.7894f),
            362.44937f,
            Node(362.44937f),
            725.86615f,
            Node(725.86615f),
            1656.2006f,
            Node(1656.2006f),
            490.8201f,
            Node(490.8201f),
            725.86615f,
            Node(725.86615f),
            723.18396f,
            Node(723.18396f),
            716.36115f,
            Node(716.36115f),
            490.8201f,
            Node(490.8201f),
            474.9852f,
            Node(474.9852f),
            379.61304f,
            Node(379.61304f),
            465.81775f,
            Node(465.81775f),
            440.83838f,
            Node(440.83838f),
            1690.9901f,
            Node(1690.9901f),
            1711.5605f,
            Node(1711.5605f),
            1795.7483f,
            Node(1795.7483f),
            388.60376f,
            Node(388.60376f),
            2119.6921f,
            Node(2119.6921f),
            1040.5143f,
            Node(1040.5143f),
            1018.3097f,
            Node(1018.3097f),
            1039.8417f,
            Node(1039.8417f),
            1142.326f,
            Node(1142.326f),
            1045.692f,
            Node(1045.692f),
            820.3383f,
            Node(820.3383f),
            474.9852f,
            Node(474.9852f),
            430.27383f,
            Node(430.27383f),
            506.89728f,
            Node(506.89728f),
            973.9379f,
            Node(973.9379f),
            723.18396f,
            Node(723.18396f),
            619.83624f,
            Node(619.83624f),
            1656.2006f,
            Node(1656.2006f),
            1547.9089f,
            Node(1547.9089f),
            1018.3097f,
            Node(1018.3097f),
            930.3666f,
            Node(930.3666f),
            1039.8417f,
            Node(1039.8417f),
            950.749f,
            Node(950.749f),
            1142.326f,
            Node(1142.326f),
            1055.636f,
            Node(1055.636f),
            1045.692f,
            Node(1045.692f),
            958.5852f,
            Node(958.5852f),
            820.3383f,
            Node(820.3383f),
            771.37115f,
            Node(771.37115f),
            506.89728f,
            Node(506.89728f),
            417.02042f,
            Node(417.02042f),
            930.3666f,
            Node(930.3666f),
            864.04517f,
            Node(864.04517f),
            950.749f,
            Node(950.749f),
            879.2704f,
            Node(879.2704f),
            958.5852f,
            Node(958.5852f),
            894.9335f,
            Node(894.9335f),
            1534.2864f,
            Node(1534.2864f),
            619.83624f,
            Node(619.83624f),
            548.92786f,
            Node(548.92786f),
            924.88995f,
            Node(924.88995f),
            905.3478f,
            Node(905.3478f),
            440.83838f,
            Node(440.83838f),
            436.48087f,
            Node(436.48087f),
            1040.5143f,
            Node(1040.5143f),
            950.6953f,
            Node(950.6953f),
            992.51624f,
            Node(992.51624f),
            808.5153f,
            Node(808.5153f),
            876.47845f,
            Node(876.47845f),
            472.963f,
            Node(472.963f),
            465.81775f,
            Node(465.81775f),
            461.85135f,
            Node(461.85135f),
            1552.4479f,
            Node(1552.4479f),
            950.6953f,
            Node(950.6953f),
            862.6192f,
            Node(862.6192f),
            992.51624f,
            Node(992.51624f),
            900.9059f,
            Node(900.9059f),
            808.5153f,
            Node(808.5153f),
            716.3565f,
            Node(716.3565f),
            876.47845f,
            Node(876.47845f),
            610.04565f,
            Node(610.04565f),
            598.95935f,
            Node(598.95935f),
            487.93192f,
            Node(487.93192f),
            864.04517f,
            Node(864.04517f),
            852.66907f,
            Node(852.66907f),
            879.2704f,
            Node(879.2704f),
            867.3523f,
            Node(867.3523f),
            894.9335f,
            Node(894.9335f),
            884.0505f,
            Node(884.0505f),
            548.7671f,
            Node(548.7671f),
            1437.1154f,
            Node(1437.1154f),
            1934.038f,
            Node(1934.038f),
            2401.7002f,
            Node(2401.7002f),
            973.9379f,
            Node(973.9379f),
            903.2409f,
            Node(903.2409f),
            1547.9089f,
            Node(1547.9089f),
            1481.2589f,
            Node(1481.2589f),
            1430.7216f,
            Node(1430.7216f)
        ) // @on
        val m = HashMap<Float, Node>(values.size)
        var i = 0
        val n = values.size
        while (i < n) {
            m[values[i] as Float] = values[i + 1] as Node
            i += 2
        }
        val h = BinaryHeap<Node>()
        h.add(m[44.683983f]!!)
        if (h.pop()?.value != 44.683983f) throw RuntimeException("Should be 44.683983")
        h.add(m[160.47682f]!!)
        h.add(m[95.038086f]!!)
        h.add(m[396.49918f]!!)
        h.add(m[835.0006f]!!)
        h.add(m[439.67096f]!!)
        h.add(m[377.55692f]!!)
        h.add(m[373.29028f]!!)
        if (h.pop()?.value != 95.038086f) throw RuntimeException("Should be 95.038086")
        h.add(m[926.524f]!!)
        if (h.pop()?.value != 160.47682f) throw RuntimeException("Should be 160.47682")
        h.add(m[189.30789f]!!)
        h.remove(m[926.524f]!!)
        h.add(m[924.88995f]!!)
        h.add(m[700.856f]!!)
        h.add(m[342.5846f]!!)
        h.add(m[313.3819f]!!)
        if (h.pop()?.value != 189.30789f) throw RuntimeException("Should be 189.30789")
        h.add(m[407.9829f]!!)
        h.add(m[1482.5394f]!!)
        h.add(m[1135.7894f]!!)
        h.add(m[362.44937f]!!)
        if (h.pop()?.value != 313.3819f) throw RuntimeException("Should be 313.3819")
        h.add(m[725.86615f]!!)
        h.add(m[1656.2006f]!!)
        h.add(m[490.8201f]!!)
        if (h.pop()?.value != 342.5846f) throw RuntimeException("Should be 342.5846")
        h.remove(m[725.86615f]!!)
        h.add(m[723.18396f]!!)
        h.add(m[716.36115f]!!)
        h.remove(m[490.8201f]!!)
        h.add(m[474.9852f]!!)
        h.add(m[379.61304f]!!)
        if (h.pop()?.value != 362.44937f) throw RuntimeException("Should be 362.44937")
        h.add(m[465.81775f]!!)
        h.add(m[440.83838f]!!)
        h.add(m[1690.9901f]!!)
        h.add(m[1711.5605f]!!)
        h.add(m[1795.7483f]!!)
        h.add(m[388.60376f]!!)
        h.add(m[2119.6921f]!!)
        if (h.pop()?.value != 373.29028f) throw RuntimeException("Should be 373.29028")
        h.add(m[1040.5143f]!!)
        h.add(m[1018.3097f]!!)
        h.add(m[1039.8417f]!!)
        h.add(m[1142.326f]!!)
        h.add(m[1045.692f]!!)
        h.add(m[820.3383f]!!)
        h.remove(m[474.9852f]!!)
        h.add(m[430.27383f]!!)
        h.add(m[506.89728f]!!)
        if (h.pop()?.value != 377.55692f) throw RuntimeException("Should be 377.55692")
        h.add(m[973.9379f]!!)
        h.remove(m[723.18396f]!!)
        h.add(m[619.83624f]!!)
        h.remove(m[1656.2006f]!!)
        h.add(m[1547.9089f]!!)
        if (h.pop()?.value != 379.61304f) throw RuntimeException("Should be 379.61304")
        h.remove(m[1018.3097f]!!)
        h.add(m[930.3666f]!!)
        h.remove(m[1039.8417f]!!)
        h.add(m[950.749f]!!)
        h.remove(m[1142.326f]!!)
        h.add(m[1055.636f]!!)
        h.remove(m[1045.692f]!!)
        h.add(m[958.5852f]!!)
        h.remove(m[820.3383f]!!)
        h.add(m[771.37115f]!!)
        h.remove(m[506.89728f]!!)
        h.add(m[417.02042f]!!)
        if (h.pop()?.value != 388.60376f) throw RuntimeException("Should be 388.60376")
        h.remove(m[930.3666f]!!)
        h.add(m[864.04517f]!!)
        h.remove(m[950.749f]!!)
        h.add(m[879.2704f]!!)
        h.remove(m[958.5852f]!!)
        h.add(m[894.9335f]!!)
        h.add(m[1534.2864f]!!)
        if (h.pop()?.value != 396.49918f) throw RuntimeException("Should be 396.49918")
        h.remove(m[619.83624f]!!)
        h.add(m[548.92786f]!!)
        h.remove(m[924.88995f]!!)
        h.add(m[905.3478f]!!)
        if (h.pop()?.value != 407.9829f) throw RuntimeException("Should be 407.9829")
        h.remove(m[440.83838f]!!)
        h.add(m[436.48087f]!!)
        if (h.pop()?.value != 417.02042f) throw RuntimeException("Should be 417.02042")
        h.remove(m[1040.5143f]!!)
        h.add(m[950.6953f]!!)
        h.add(m[992.51624f]!!)
        h.add(m[808.5153f]!!)
        if (h.pop()?.value != 430.27383f) throw RuntimeException("Should be 430.27383")
        h.add(m[876.47845f]!!)
        h.add(m[472.963f]!!)
        if (h.pop()?.value != 436.48087f) throw RuntimeException("Should be 436.48087")
        h.remove(m[465.81775f]!!)
        h.add(m[461.85135f]!!)
        h.add(m[1552.4479f]!!)
        if (h.pop()?.value != 439.67096f) throw RuntimeException("Should be 439.67096")
        if (h.pop()?.value != 461.85135f) throw RuntimeException("Should be 461.85135")
        h.remove(m[950.6953f]!!)
        h.add(m[862.6192f]!!)
        h.remove(m[992.51624f]!!)
        h.add(m[900.9059f]!!)
        h.remove(m[808.5153f]!!)
        h.add(m[716.3565f]!!)
        h.remove(m[876.47845f]!!)
        h.add(m[610.04565f]!!)
        h.add(m[598.95935f]!!)
        h.add(m[487.93192f]!!)
        h.remove(m[864.04517f]!!)
        h.add(m[852.66907f]!!)
        h.remove(m[879.2704f]!!)
        h.add(m[867.3523f]!!)
        h.remove(m[894.9335f]!!)
        h.add(m[884.0505f]!!)
        if (h.pop()?.value != 472.963f) throw RuntimeException("Should be 472.963")
        if (h.pop()?.value != 487.93192f) throw RuntimeException("Should be 487.93192")
        h.add(m[548.7671f]!!)
        if (h.pop()?.value != 548.7671f) throw RuntimeException("Should be 548.7671")
        h.add(m[1437.1154f]!!)
        h.add(m[1934.038f]!!)
        h.add(m[2401.7002f]!!)
        if (h.pop()?.value != 548.92786f) throw RuntimeException("Should be 548.92786")
        h.remove(m[973.9379f]!!)
        h.add(m[903.2409f]!!)
        h.remove(m[1547.9089f]!!)
        h.add(m[1481.2589f]!!)
        if (h.pop()?.value != 598.95935f) throw RuntimeException("Should be 598.95935")
        h.add(m[1430.7216f]!!)

        // at this point in a debugger, you can tell that 610.04565 is in position 1, while 700.856 is in position 0.
        // this is incorrect, but I'm not sure at what point in the test it became incorrect.
        val popped = h.pop()?.value
        if (popped != 610.04565f) throw RuntimeException("Should be 610.04565, but is $popped")
    }

    @Test
    fun testObjectMap() {
        testMap(ObjectMap::class.java, values, valuesWithNulls)
    }

    @Test
    fun testOrderedMap() {
        testMap(OrderedMap::class.java, values, valuesWithNulls)
    }

    @Test
    fun testIdentityMap() {
        testMap(IdentityMap::class.java, values, valuesWithNulls)
    }

    @Test
    fun testArrayMap() {
        testMap(ArrayMap::class.java, values, valuesWithNulls)
    }

    @Test
    fun testObjectFloatMap() {
        testMap(ObjectFloatMap::class.java, values, floatValues as Array<Any?>)
    }

    @Test
    fun testObjectIntMap() {
        testMap(ObjectIntMap::class.java, values, intValues as Array<Any?>)
    }

    @Test
    fun testIntFloatMap() {
        testMap(IntFloatMap::class.java, intValues, floatValues as Array<Any?>)
    }

    @Test
    fun testIntIntMap() {
        testMap(IntIntMap::class.java, intValues, intValues as Array<Any?>)
    }

    @Test
    fun testIntMap() {
        testMap(IntMap::class.java, intValues, valuesWithNulls)
    }

    @Test
    fun testLongMap() {
        testMap(LongMap::class.java, longValues, valuesWithNulls)
    }

    @Test
    fun testExpandingArray() {
        // we don't test the other default array types, because those are already valid (from kotlin).
        // Only the expanding "Array" type
        testExpandingArray(valuesWithNulls)
    }

    @Test
    fun testObjectSet() {
        testSet(ObjectSet::class.java, values)
    }

    @Test
    fun testOrderedSet() {
        testSet(OrderedSet::class.java, problemValues)
    }

    @Test
    fun testIntSet() {
        testSet(IntSet::class.java, intValues)
    }

    @Test
    fun testObjectSet2() {
        testSet(ObjectSet::class.java, problemValues)
    }

    @Test
    fun testOrderedSet2() {
        testSet(OrderedSet::class.java, problemValues)
    }
}
