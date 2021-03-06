package com.github.mmauro94.media_merger.adjustment.audio

import com.github.mmauro94.media_merger.Track
import com.github.mmauro94.media_merger.adjustment.Adjustment
import com.github.mmauro94.media_merger.cuts.Cut
import com.github.mmauro94.media_merger.cuts.Cuts
import com.github.mmauro94.media_merger.cuts.Empty
import com.github.mmauro94.media_merger.util.toTotalSeconds
import net.bramp.ffmpeg.builder.FFmpegBuilder
import net.bramp.ffmpeg.builder.FFmpegOutputBuilder
import java.io.File
import java.time.Duration

class CutsAudioAdjuster(
    track: Track,
    adjustment: Adjustment<Cuts>,
    outputFile: File
) : AudioAdjuster<Cuts>(track, adjustment, outputFile) {

    private val cutParts = data.getCutParts()

    override val targetDuration: Duration = cutParts.fold(Duration.ZERO) { acc, it -> acc + it.duration }

    private var filters: List<Filter>

    init {
        val input = cutParts.filterIsInstance<Empty>().size.toString() + ":${track.id}"
        val cutsCount = cutParts.count { it is Cut }
        filters = listOf(
            listOf(
                Filter(
                    "[$input]asplit=$cutsCount",
                    *Array(cutsCount) { i -> "in$i" })
            ),
            buildCutFilters()
        ).flatten()
    }


    private class Filter(val filter: String, vararg val outs: String) {
        override fun toString() = filter + outs.joinToString(separator = "") { "[$it]" }
    }

    private fun buildCutFilters(): List<Filter> {
        val ret = mutableListOf<Filter>()
        val toConcat = mutableListOf<String>()

        var inIndex = 0
        for ((i, piece) in cutParts.withIndex()) {
            when (piece) {
                is Cut -> {
                    val part = "part$i"
                    val f =
                        Filter(
                            "[in${inIndex++}]atrim=start=" + piece.time.start.toTotalSeconds() + ":end=" + piece.time.end.toTotalSeconds() + ",asetpts=PTS-STARTPTS",
                            part
                        )
                    ret.add(f)
                    toConcat.add(part)
                }
                is Empty -> {
                    toConcat.add(cutParts.filterIsInstance<Empty>().indexOf(piece).toString())
                }
            }
        }

        if (toConcat.isNotEmpty()) {
            ret.add(Filter(
                toConcat.joinToString(separator = "") { "[$it]" } + "concat=n=${toConcat.size}:v=0:a=1",
                "outa"
            ))
        }
        return ret
    }


    override fun FFmpegBuilder.fillBuilder() {
        cutParts.forEach {
            if (it is Empty) {
                addExtraArgs("-f", "lavfi", "-t", it.duration.toTotalSeconds(), "-i", "anullsrc")
            }
        }

        filters.joinToString("; ").let {
            setComplexFilter("\"$it\"")
        }
    }

    override fun FFmpegOutputBuilder.fillOutputBuilder() {
        addExtraArgs("-map", "[" + filters.last().outs.single() + "]")
    }
}