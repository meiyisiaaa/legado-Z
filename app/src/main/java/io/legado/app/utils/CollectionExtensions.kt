package io.legado.app.utils

/**
 * Moves one uniquely identified row relative to another in the complete current list.
 * Items between a filtered view's visible rows keep their relative order.
 */
internal fun <T, K> moveRelativeTo(
    allItems: List<T>,
    movedKey: K,
    targetKey: K,
    after: Boolean,
    keySelector: (T) -> K,
): List<T> {
    if (movedKey == targetKey) return allItems
    val from = allItems.indexOfFirst { keySelector(it) == movedKey }
    if (from < 0) return allItems
    val withoutMoved = allItems.toMutableList().apply { removeAt(from) }
    val target = withoutMoved.indexOfFirst { keySelector(it) == targetKey }
    if (target < 0) return allItems
    return withoutMoved.apply { add(target + if (after) 1 else 0, allItems[from]) }
}

fun List<Float>.fastSum(): Float {
    var sum = 0f
    for (i in indices) {
        sum += this[i]
    }
    return sum
}

inline fun <T> List<T>.fastBinarySearch(
    fromIndex: Int = 0,
    toIndex: Int = size,
    comparison: (T) -> Int
): Int {
    when {
        fromIndex > toIndex ->
            throw IllegalArgumentException(
                "fromIndex ($fromIndex) is greater than toIndex ($toIndex)."
            )

        fromIndex < 0 ->
            throw IndexOutOfBoundsException("fromIndex ($fromIndex) is less than zero.")

        toIndex > size ->
            throw IndexOutOfBoundsException("toIndex ($toIndex) is greater than size ($size).")
    }

    var low = fromIndex
    var high = toIndex - 1

    while (low <= high) {
        val mid = (low + high).ushr(1) // safe from overflows
        val midVal = get(mid)
        val cmp = comparison(midVal)

        if (cmp < 0)
            low = mid + 1
        else if (cmp > 0)
            high = mid - 1
        else
            return mid // key found
    }
    return -(low + 1)  // key not found
}

inline fun <T, K : Comparable<K>> List<T>.fastBinarySearchBy(
    key: K?,
    fromIndex: Int = 0,
    toIndex: Int = size,
    crossinline selector: (T) -> K?
): Int = fastBinarySearch(fromIndex, toIndex) { compareValues(selector(it), key) }

fun <T> MutableList<T>.removeLastElement(): T {
    return if (isEmpty()) {
        throw NoSuchElementException("List is empty.")
    } else {
        removeAt(lastIndex)
    }
}
