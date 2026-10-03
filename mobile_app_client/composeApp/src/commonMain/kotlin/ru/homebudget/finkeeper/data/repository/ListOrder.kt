package ru.homebudget.finkeeper.data.repository

/**
 * Полный порядок списка после перестановки его части: записи из [reorderedIds] занимают
 * позиции, которые занимали до этого, в новом порядке; остальные остаются на своих местах.
 * Записи из [reorderedIds], которых нет в [currentOrder], добавляются в конец.
 */
internal fun mergeReorderedSubset(
    currentOrder: List<Long>,
    reorderedIds: List<Long>,
): List<Long> {
    val reorderedSet = reorderedIds.toSet()
    val queue = reorderedIds.filter { it in currentOrder }.iterator()
    val merged = currentOrder.map { id -> if (id in reorderedSet && queue.hasNext()) queue.next() else id }
    return merged + reorderedIds.filter { it !in currentOrder }
}
