package `in`.txnsense.app.parser.induction

/**
 * Groups skeletons that look like instances of the same layout.
 *
 * Alignment only works on messages that really share a layout: mixing two layouts erodes the spine
 * until the pattern is too loose to be safe. Clustering is deliberately crude — same bank, same
 * movement words, and a large enough overlap in the rest of the vocabulary — because a cluster that
 * is too narrow merely defers learning until more messages arrive, while one that is too wide
 * produces a bad pattern.
 *
 * The movement words are an exact partition rather than part of the overlap score, because a bank's
 * incoming and outgoing layouts are usually the same sentence with one verb changed. Those two differ
 * in the one thing it is least acceptable to get wrong, yet they overlap far too much for a
 * similarity score to separate them, and merging them yields a spine with both verbs in it that no
 * labeller can read either way.
 */
object SkeletonClusterer {

    /** Share of vocabulary words two skeletons must have in common to be treated as one layout. */
    const val DEFAULT_MIN_OVERLAP = 0.6

    fun cluster(skeletons: List<Skeleton>, minOverlap: Double = DEFAULT_MIN_OVERLAP): List<List<Skeleton>> =
        skeletons.groupBy { it.bankId to movement(it) }.values.flatMap { byBank ->
            val clusters = mutableListOf<MutableList<Skeleton>>()
            byBank.forEach { skeleton ->
                val home = clusters.firstOrNull { overlap(it.first(), skeleton) >= minOverlap }
                if (home != null) home += skeleton else clusters += mutableListOf(skeleton)
            }
            clusters
        }

    /** The movement verbs in a skeleton's spine, which a true layout repeats in every instance. */
    private fun movement(skeleton: Skeleton): Set<String> =
        skeleton.words.filterTo(mutableSetOf()) { it in BankVocabulary.movement }

    /** Jaccard overlap of the two skeletons' vocabulary words. */
    private fun overlap(left: Skeleton, right: Skeleton): Double {
        val a = left.words.toSet()
        val b = right.words.toSet()
        if (a.isEmpty() && b.isEmpty()) return 1.0
        val union = (a + b).size
        return if (union == 0) 0.0 else a.intersect(b).size.toDouble() / union
    }
}
