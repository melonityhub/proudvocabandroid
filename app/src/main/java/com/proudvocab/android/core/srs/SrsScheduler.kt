package com.proudvocab.android.core.srs

import kotlin.math.roundToInt
import kotlin.math.roundToLong

enum class Rating(val id: Int) { AGAIN(0), HARD(1), GOOD(2), EASY(3) }

enum class SrsAlgorithm(val key: String) { CLASSIC("classic"), SM2("sm2") }

/** Review state of a single word. */
data class SrsState(
    val intervalDays: Double = 0.0,
    val easeFactor: Double = 2.5,
    val nextReview: Long = 0L,
    val reviewCount: Int = 0,
    val streak: Int = 0
) {
    val isDue: Boolean get() = nextReview <= System.currentTimeMillis()
}

object SrsScheduler {

    private const val DAY_MS = 86_400_000L
    private const val TEN_MINUTES_DAYS = 0.007

    fun initial(): SrsState = SrsState()

    /**
     * @param rating how the learner scored the card
     * @param algorithm which spacing rule to use (both are selectable in the
     *                  settings; `CLASSIC` reproduces the ProudVocab schedule)
     */
    fun next(
        state: SrsState,
        rating: Rating,
        algorithm: SrsAlgorithm = SrsAlgorithm.CLASSIC,
        now: Long = System.currentTimeMillis()
    ): SrsState {
        return when (algorithm) {
            SrsAlgorithm.CLASSIC -> nextClassic(state, rating, now)
            SrsAlgorithm.SM2 -> nextSm2(state, rating, now)
        }
    }

    private fun nextClassic(state: SrsState, rating: Rating, now: Long): SrsState {
        var interval = state.intervalDays
        var ease = state.easeFactor
        var streak = state.streak
        when (rating) {
            Rating.AGAIN -> {
                streak = 0
                interval = TEN_MINUTES_DAYS
                ease = (ease - 0.2).coerceAtLeast(1.3)
            }
            Rating.HARD -> {
                streak = 1
                interval = 1.0
                ease = (ease - 0.15).coerceAtLeast(1.3)
            }
            Rating.GOOD -> {
                streak = state.streak + 1
                interval = 3.0
            }
            Rating.EASY -> {
                streak = state.streak + 1
                interval = 7.0
                ease = (ease + 0.15).coerceAtMost(3.0)
            }
        }
        return SrsState(
            intervalDays = interval,
            easeFactor = ease,
            nextReview = now + (DAY_MS * interval).roundToLong(),
            reviewCount = state.reviewCount + 1,
            streak = streak
        )
    }

    /** Text-book SM-2 with a couple of extra learning steps. */
    private fun nextSm2(state: SrsState, rating: Rating, now: Long): SrsState {
        var ease = state.easeFactor
        var interval: Double
        var streak: Int
        when (rating) {
            Rating.AGAIN -> {
                streak = 0
                interval = TEN_MINUTES_DAYS
                ease = (ease - 0.20).coerceAtLeast(1.3)
            }
            Rating.HARD -> {
                streak = state.streak + 1
                interval = (state.intervalDays * 1.2).coerceAtLeast(1.0)
                ease = (ease - 0.15).coerceAtLeast(1.3)
            }
            Rating.GOOD -> {
                streak = state.streak + 1
                interval = when {
                    state.reviewCount == 0 -> 1.0
                    state.reviewCount == 1 -> 6.0
                    else -> state.intervalDays * ease
                }
            }
            Rating.EASY -> {
                streak = state.streak + 1
                interval = when {
                    state.reviewCount == 0 -> 4.0
                    else -> state.intervalDays * ease * 1.3
                }
                ease = (ease + 0.15).coerceAtMost(3.0)
            }
        }
        interval = interval.coerceIn(TEN_MINUTES_DAYS, 365.0 * 5)
        return SrsState(
            intervalDays = interval,
            easeFactor = ease,
            nextReview = now + (DAY_MS * interval).roundToLong(),
            reviewCount = state.reviewCount + 1,
            streak = streak
        )
    }

    /** Preview of the intervals shown under each rating button. */
    fun previewIntervals(
        state: SrsState,
        algorithm: SrsAlgorithm,
        now: Long = System.currentTimeMillis()
    ): Map<Rating, Double> = Rating.entries.associateWith {
        next(state, it, algorithm, now).intervalDays
    }

    /** Human readable interval, e.g. `10m`, `6h`, `3d`, `4mo`, `1.2y`. */
    fun formatInterval(days: Double): IntervalParts {
        return when {
            days < 0.1 -> IntervalParts(10, IntervalUnit.MINUTE)
            days < 1 -> IntervalParts((days * 24).roundToInt().coerceAtLeast(1), IntervalUnit.HOUR)
            days < 30 -> IntervalParts(days.roundToInt().coerceAtLeast(1), IntervalUnit.DAY)
            days < 365 -> IntervalParts((days / 30).roundToInt().coerceAtLeast(1), IntervalUnit.MONTH)
            else -> IntervalParts(days / 365.0, IntervalUnit.YEAR)
        }
    }

    fun dueItems(items: List<DueItem>): List<DueItem> =
        items.filter { !it.learned && it.reviewCount > 0 && it.nextReview <= System.currentTimeMillis() }

    fun newItems(items: List<DueItem>, limit: Int): List<DueItem> =
        items.filter { !it.learned && it.reviewCount == 0 }.take(limit)
}

enum class IntervalUnit { MINUTE, HOUR, DAY, MONTH, YEAR }

data class IntervalParts(val value: Number, val unit: IntervalUnit)

/** Minimal view of a saved word, enough to plan a session. */
data class DueItem(
    val word: String,
    val learned: Boolean,
    val reviewCount: Int,
    val nextReview: Long,
    val intervalDays: Double = 0.0
)
