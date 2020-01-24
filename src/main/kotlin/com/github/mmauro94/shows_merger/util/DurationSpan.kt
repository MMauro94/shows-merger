package com.github.mmauro94.shows_merger.util

import com.github.mmauro94.shows_merger.StretchFactor
import com.github.mmauro94.shows_merger.times
import java.time.Duration

/**
 * A time span (e.g. from 00:01.123 to 00:15.456)
 * @param start the start time, must be positive
 * @param end the end time, must be greater than [start]
 */
data class DurationSpan(val start: Duration, val end: Duration) {

    init {
        require(!start.isNegative)
        require(start < end)
    }

    /**
     * The duration of this span
     */
    val duration: Duration = end - start

    /**
     * Commodity property that is half of [duration]
     */
    val halfDuration: Duration = duration.dividedBy(2L)

    /**
     * Commodity property that is the middle of span
     */
    val middle: Duration = start + halfDuration

    /**
     * Returns a new [DurationSpan] of the first half of this span.
     */
    fun firstHalf() = DurationSpan(
        start = start,
        end = middle
    )

    /**
     * Returns a new [DurationSpan] of the second half of this span.
     */
    fun secondHalf() = DurationSpan(
        start = middle,
        end = end
    )

    /**
     * Returns whether this span and [other] intersect.
     */
    fun intersects(other: DurationSpan): Boolean {
        return if (start < other.start) end > other.start
        else start < other.end
    }

    /**
     * Returns a new [DurationSpan] restricted in the [other] span.
     * If the resulting span would be empty, returns `null`
     */
    fun restrictIn(other: DurationSpan): DurationSpan? {
        val newStart = maxOf(start, other.start)
        val newEnd = minOf(end, other.end)
        return if(newStart >= newEnd) {
            null
        } else DurationSpan(
            start = newStart,
            end = newEnd
        )
    }

    /**
     * Moves this span by the given [offset].
     */
    fun moveBy(offset: Duration): DurationSpan {
        return DurationSpan(
            start = start + offset,
            end = end + offset
        )
    }

    /**
     * Multiplies this span by the given [stretchFactor].
     */
    operator fun times(stretchFactor: StretchFactor): DurationSpan {
        return DurationSpan(
            start = start * stretchFactor,
            end = end * stretchFactor
        )
    }
}