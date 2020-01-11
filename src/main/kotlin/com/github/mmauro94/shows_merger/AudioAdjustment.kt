package com.github.mmauro94.shows_merger

import net.bramp.ffmpeg.FFmpeg
import net.bramp.ffmpeg.FFmpegExecutor
import net.bramp.ffmpeg.FFmpegUtils
import net.bramp.ffmpeg.FFprobe
import net.bramp.ffmpeg.builder.FFmpegBuilder
import java.io.File
import java.math.BigDecimal
import java.util.concurrent.TimeUnit

class AudioAdjustment(
    val track: Track,
    val adjustment: Adjustment
) {
    val outputExtension = track.mkvTrack.codec.toLowerCase().let { c ->
        when {
            c.contains("ac-3") -> "ac3"
            c.contains("aac") -> "aac"
            c.contains("mp3") -> "mp3"
            c.contains("flac") -> "flac"
            else -> "mkv"
        }
    }

    val outputFile = File(
        track.file.parentFile,
        track.file.nameWithoutExtension +
                "_" + track.id +
                "_" + adjustment.stretchFactor.ratio.toString().replace('.', '_') +
                "_" + track.language.iso639_2 +
                ".$outputExtension"
    )

    /**
     * Example command to cut audio (no blanks yet):
     * ffmpeg `
    -vn -sn -i file.mkv `
    -f lavfi -t 5 -i anullsrc `
    -filter_complex `
    "[0:a]atrim=duration=30[aa];`
    [0:a]atrim=start=40:end=50,asetpts=PTS-STARTPTS[ba];`
    [aa][1][ba]concat=n=3:v=0:a=1[outa]" `
    -map [outa] audio.ac3
     */

    private class Filter(val filter: String, val out: String) {
        override fun toString() = "$filter[$out]"
    }

    private fun buildCutFilters(input: String): List<Filter> {
        if (adjustment.cuts.optOffset() != null) {
            return emptyList()
        }

        val pieces = adjustment.cuts.getSilenceOrCuts()
        val ret = mutableListOf<Filter>()
        val toConcat = mutableListOf<String>()

        for ((i, piece) in pieces.withIndex()) {
            when (piece) {
                is SilenceOrCut.Cut -> {
                    val f = Filter(
                        "[$input]atrim=start=" + piece.startCut.toTotalSeconds() + ":end=" + piece.endCut.toTotalSeconds() + ",asetpts=PTS-STARTPTS",
                        "part$i"
                    )
                    ret.add(f)
                    toConcat.add(f.out)
                }
                is SilenceOrCut.Silence -> {
                    toConcat.add(pieces.filterIsInstance<SilenceOrCut.Silence>().indexOf(piece).toString())
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

    fun adjust(progress: String): Boolean {
        val silenceOrCuts = adjustment.cuts.getSilenceOrCuts()
        val inputTrack = silenceOrCuts.filterIsInstance<SilenceOrCut.Silence>().size.toString() + ":${track.id}"

        val ratio = adjustment.stretchFactor.ratio
        val filters = sequence {
            val stretchFilter = if (ratio.compareTo(BigDecimal.ONE) != 0) {
                Filter("[$inputTrack]atempo=$ratio", "stretched")
            } else null
            val input = stretchFilter?.out ?: inputTrack
            yieldAll(buildCutFilters(input))
        }.toList()

        return if (filters.isNotEmpty()) {
            val filtersStr = filters.joinToString("; ")
            val builder = FFmpegBuilder()
                .setInput(track.file.absolutePath).apply {
                    silenceOrCuts.forEach {
                        if (it is SilenceOrCut.Silence) {
                            addExtraArgs("-f", "lavfi", "-t", it.duration.toTotalSeconds(), "-i", "anullsrc")
                        }
                    }
                }
                .setComplexFilter("\"$filtersStr\"")
                .addOutput(outputFile.absolutePath)
                .addExtraArgs("-map", "[" + filters.last().out + "]")
                .done()

            println(builder.build().joinToString(" "))
            FFmpegExecutor(FFmpeg(), FFprobe()).apply {
                createJob(builder) { prg ->
                    //val percentage = prg.out_time_ns / adjustment.targetDuration.toNanos().toDouble()
                    val percentage = 0 //TODO

                    println(
                        String.format(
                            "[%s, %.0f%%] %s, speed:%.2fx",
                            progress,
                            percentage * 100.0,
                            FFmpegUtils.toTimecode(prg.out_time_ns, TimeUnit.NANOSECONDS),
                            prg.speed
                        )
                    )
                }.run()
            }
            true
        } else false
    }
}