package com.proudvocab.android

import com.proudvocab.android.core.srs.DueItem
import com.proudvocab.android.core.srs.IntervalUnit
import com.proudvocab.android.core.srs.Rating
import com.proudvocab.android.core.srs.SrsAlgorithm
import com.proudvocab.android.core.srs.SrsScheduler
import com.proudvocab.android.core.srs.SrsState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The scheduling rules are ported from the desktop extension, so they are
 * pinned down here: a regression in spacing would silently wreck every
 * learner's review queue.
 */
class SrsSchedulerTest {

    private val now = 1_700_000_000_000L
    private val day = 86_400_000L

    @Test
    fun `again resets the streak and schedules ten minutes`() {
        val next = SrsScheduler.next(SrsState(intervalDays = 6.0, streak = 4), Rating.AGAIN, SrsAlgorithm.CLASSIC, now)
        assertEquals(0, next.streak)
        assertEquals(0.007, next.intervalDays, 0.0001)
        assertEquals(now + (0.007 * day).toLong(), next.nextReview)
    }

    @Test
    fun `good grows the interval and keeps the streak`() {
        val start = SrsState(intervalDays = 1.0, streak = 2, reviewCount = 2)
        val next = SrsScheduler.next(start, Rating.GOOD, SrsAlgorithm.CLASSIC, now)
        assertEquals(3, next.streak)
        assertEquals(start.reviewCount + 1, next.reviewCount)
        assertTrue(next.intervalDays > start.intervalDays)
    }

    @Test
    fun `ease factor never drops below the floor`() {
        var state = SrsState()
        repeat(20) { state = SrsScheduler.next(state, Rating.AGAIN, SrsAlgorithm.CLASSIC, now) }
        assertEquals(1.3, state.easeFactor, 0.0001)
    }

    @Test
    fun `ease factor never exceeds the ceiling`() {
        var state = SrsState()
        repeat(20) { state = SrsScheduler.next(state, Rating.EASY, SrsAlgorithm.CLASSIC, now) }
        assertTrue(state.easeFactor <= 3.0 + 0.0001)
    }

    @Test
    fun `sm2 keeps a card in the same day after a lapse`() {
        val next = SrsScheduler.next(SrsState(intervalDays = 10.0), Rating.AGAIN, SrsAlgorithm.SM2, now)
        assertEquals(1, next.reviewCount)
        assertEquals(0.007, next.intervalDays, 0.0001)
    }

    @Test
    fun `sm2 and classic both move a card forward on good`() {
        val start = SrsState(intervalDays = 1.0, streak = 1, reviewCount = 1)
        val classic = SrsScheduler.next(start, Rating.GOOD, SrsAlgorithm.CLASSIC, now)
        val sm2 = SrsScheduler.next(start, Rating.GOOD, SrsAlgorithm.SM2, now)
        assertTrue(classic.intervalDays > 1.0)
        assertTrue(sm2.intervalDays > 1.0)
    }

    @Test
    fun `preview covers every rating`() {
        val preview = SrsScheduler.previewIntervals(SrsState(), SrsAlgorithm.CLASSIC, now)
        assertEquals(Rating.entries.toSet(), preview.keys)
    }

    @Test
    fun `intervals are formatted with the right unit`() {
        assertEquals(IntervalUnit.MINUTE, SrsScheduler.formatInterval(0.007).unit)
        assertEquals(IntervalUnit.HOUR, SrsScheduler.formatInterval(0.5).unit)
        assertEquals(IntervalUnit.DAY, SrsScheduler.formatInterval(3.0).unit)
        assertEquals(IntervalUnit.MONTH, SrsScheduler.formatInterval(60.0).unit)
        assertEquals(IntervalUnit.YEAR, SrsScheduler.formatInterval(400.0).unit)
    }

    @Test
    fun `due and new queues never overlap`() {
        val items = listOf(
            DueItem("a", learned = false, reviewCount = 0, nextReview = 0L),
            DueItem("b", learned = false, reviewCount = 3, nextReview = now - day),
            DueItem("c", learned = true, reviewCount = 3, nextReview = now - day),
            DueItem("d", learned = false, reviewCount = 3, nextReview = now + day)
        )
        val due = SrsScheduler.dueItems(items).map { it.word }
        val fresh = SrsScheduler.newItems(items, 10).map { it.word }
        assertEquals(listOf("b"), due)
        assertEquals(listOf("a"), fresh)
    }

    @Test
    fun `a fresh card is due immediately`() {
        assertTrue(SrsScheduler.initial().isDue)
    }
}
