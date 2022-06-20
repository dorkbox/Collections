package dorkbox.collections.ahoCorasick

import java.util.*

/**
 * Creates a Finite State Machine for very fast string matching.
 *
 * This is a wrapper for DoubleArrayTrie, since that class is awkward to use
 */
class FiniteStateMachine<V>(private val trie: DoubleArrayTrie<V>) {
    companion object {
        fun <V> build(map: Map<String, V>): FiniteStateMachine<V> {
            return FiniteStateMachine(DoubleArrayTrie(map))
        }

        fun build(strings: List<String>): FiniteStateMachine<Boolean> {
            val map = TreeMap<String, Boolean>()
            for (key in strings) {
                map[key] = java.lang.Boolean.TRUE
            }

            return build(map)
        }

        fun build(vararg strings: String): FiniteStateMachine<Boolean> {
            val map = TreeMap<String, Boolean>()
            for (key in strings) {
                map[key] = java.lang.Boolean.TRUE
            }

            return build(map)
        }
    }

    /**
     * @return true if this string is exactly contained. False otherwise
     */
    fun matches(text: String): Boolean {
        return (trie.exactMatchSearch(text) > -1)
    }

    /**
     * Parses text and finds PARTIALLY matching results. For exact matches only it is better to use `matches`
     *
     * @return a list of outputs that contain matches or partial matches. The returned list will specify HOW MUCH of the text matches (A full match would be from 0 (the start), to N (the length of the text).
     */
    fun partialMatch(text: String): List<DoubleArrayTrie.Hit<V>> {
        return trie.parseText(text)
    }

    /**
     * Parses text and returns true if there are PARTIALLY matching results. For exact matches only it is better to use `matches`
     *
     * @return true if there is a match or partial match. "fun.reddit.com" will partially match to "reddit.com"
     */
    fun hasPartialMatch(text: String): Boolean {
        return trie.parseText(text).isNotEmpty()
    }

    /**
     * Returns the backing keywords IN THEIR NATURAL ORDER, in the case that you need access to the original FSM data.
     *
     * @return for example, if the FSM was populated with [reddit.com, cnn.com], this will return [cnn.com, reddit.com]
     */
    fun getKeywords(): Array<V> {
        return trie.v
    }
}
