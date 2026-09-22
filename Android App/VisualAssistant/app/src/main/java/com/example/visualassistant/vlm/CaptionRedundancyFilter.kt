package com.example.visualassistant.vlm

import android.util.Log

/**
 * Filters out redundant captions by comparing meaningful word overlap.
 * Removes common stop words and VLM filler words before comparison.
 */
class CaptionRedundancyFilter(
    /** Number of common meaningful words to trigger redundancy drop. */
    var overlapThreshold: Int = 3
) {
    private var lastMeaningfulWords: Set<String> = emptySet()
    private var lastFullCaption: String = ""

    /**
     * Checks if the current caption is semantically redundant compared to the last one.
     * Updates the internal state if the caption is NOT redundant.
     */
    fun isRedundant(caption: String): Boolean {
        if (caption.isBlank()) return true

        val currentWords = preprocess(caption)
        Log.v(TAG, "Preprocessing: \"$caption\" -> filtered words: $currentWords")

        if (lastMeaningfulWords.isEmpty()) {
            lastMeaningfulWords = currentWords
            lastFullCaption = caption
            return false
        }

        // Calculate overlap
        val intersection = currentWords.intersect(lastMeaningfulWords)
        val overlapCount = intersection.size

        return if (overlapCount >= overlapThreshold) {
            Log.d(TAG, "Caption DROPPED (redundant)")
            Log.d(TAG, "  - Previous: \"$lastFullCaption\"")
            Log.d(TAG, "  - Current:  \"$caption\"")
            Log.d(TAG, "  - Overlap count: $overlapCount (Common words: $intersection)")
            true
        } else {
            lastMeaningfulWords = currentWords
            lastFullCaption = caption
            false
        }
    }

    fun reset() {
        lastMeaningfulWords = emptySet()
        lastFullCaption = ""
        Log.i(TAG, "Caption filter reset.")
    }

    private fun preprocess(text: String): Set<String> {
        return text.lowercase()
            .split(Regex("\\W+")) // Tokenize by non-word characters
            .filter { it.length > 2 } // Ignore very short fragments
            .filter { it !in STOP_WORDS }
            .toSet()
    }

    companion object {
        private const val TAG = "CaptionFilter"

        private val STOP_WORDS = setOf(
            // Articles, Conjunctions, and Prepositions
            "the", "and", "for", "with", "from", "this", "that", "these", "those",
            "but", "not", "its", "into", "onto", "upon", "under", "over", "above", "below",
            "some", "such", "than", "then", "there", "when", "where", "while", "both",
            "each", "either", "neither", "only", "other", "some", "such", "than", "then",
            "about", "against", "between", "through", "during", "before", "after",
            "here", "there", "when", "where", "why", "how", "all", "any", "both",

            // Auxiliary Verbs and common non-descriptive verbs
            "been", "being", "have", "has", "had", "was", "were", "are", "you", "your",
            "can", "will", "would", "should", "could", "does", "done", "doing",
            "appears", "seems", "stays", "remains", "looks",

            // VLM & Photography Meta-Fillers (describing the image, not the content)
            "image", "depicts", "captures", "shows", "view", "scene", "photo",
            "picture", "looks", "like", "moment", "perspective", "frame", "description",
            "photograph", "snapshot", "illustration", "display", "presenting", "contains",
            "features", "consists", "includes", "observes", "background", "foreground",
            "center", "middle", "left", "right", "side", "positioned", "located", "placed",
            "captured", "visible", "showing", "represented", "depicted"
        )
    }
}
