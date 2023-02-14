/*
 * Copyright 2010 dorkbox, llc
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

import java.util.*

object Collections {
    /**
     * Gets the version number.
     */
    const val version = "1.5"

    init {
        // Add this project to the updates system, which verifies this class + UUID + version information
        dorkbox.updates.Updates.add(java.util.Collections::class.java, "7a4be173d7fd48e4a09543cc572eb903", version)
    }

    internal val random = Random()

    /** Returns a random number between 0 (inclusive) and the specified value (inclusive).  */
    fun random(range: Int): Int {
        return random.nextInt(range + 1)
    }

    /** Returns a random number between start (inclusive) and end (inclusive).  */
    @JvmStatic
    fun random(start: Int, end: Int): Int {
        return start + random.nextInt(end - start + 1)
    }

    /**
     * Returns the next power of two. Returns the specified value if the value is already a power of two.
     */
    @JvmStatic
    fun nextPowerOfTwo(value: Int): Int {
        return 1 shl 32 - Integer.numberOfLeadingZeros(value - 1)
    }
}
