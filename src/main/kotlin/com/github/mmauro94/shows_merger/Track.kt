package com.github.mmauro94.shows_merger

import com.github.mmauro94.mkvtoolnix_wrapper.MkvToolnix
import com.github.mmauro94.mkvtoolnix_wrapper.MkvToolnixLanguage
import com.github.mmauro94.mkvtoolnix_wrapper.MkvToolnixTrack
import com.github.mmauro94.mkvtoolnix_wrapper.MkvToolnixTrackType
import net.bramp.ffmpeg.probe.FFmpegStream
import java.io.File
import java.util.*

class Track(
    val inputFile: InputFile,
    val mkvTrack: MkvToolnixTrack,
    val ffprobeStream: FFmpegStream,
    val language: MkvToolnixLanguage
) {

    val id = mkvTrack.id

    val file = inputFile.file

    val duration = ffprobeStream.duration.asSecondsDuration()

    val durationOrFileDuration = duration ?: inputFile.duration

    val isOnItsFile by lazy { inputFile.tracks.size == 1 }

    val extension = mkvTrack.properties?.codecId.let { c ->
        when (c) {
            null -> "unknown"
            "A_DTS" -> "dts"
            "A_AC3", " A_EAC3" -> "ac3"
            "A_FLAC" -> "flac"
            "A_MPEG/L2" -> "mp2"
            "A_MPEG/L3" -> "mp3"
            "A_OPUS" -> "opus"
            "A_PCM/INT/LIT", "A_PCM/INT/BIG" -> "wav"
            "A_VORBIS" -> "ogg"
            "S_TEXT/UTF8", "S_TEXT/ASCII" -> "srt"
            else -> when {
                c.startsWith("A_AAC") -> "aac"
                else -> "unknown"
            }
        }
    }

    val isForced by lazy {
        mkvTrack.isForced() == true ||
                mkvTrack.properties?.trackName?.contains("forced", ignoreCase = true) == true ||
                (isOnItsFile && inputFile.file.find {
                    it.name.contains("forced", ignoreCase = true)
                } ?: false) ||
                (isOnItsFile && inputFile.file.length() in 1..(1024 * 10)) //< 10KiB
    }

    fun isAudioTrack() =
        mkvTrack.type == MkvToolnixTrackType.audio && ffprobeStream.codec_type == FFmpegStream.CodecType.AUDIO

    fun isVideoTrack() =
        mkvTrack.type == MkvToolnixTrackType.video && ffprobeStream.codec_type == FFmpegStream.CodecType.VIDEO

    fun isSubtitlesTrack() = mkvTrack.type == MkvToolnixTrackType.subtitles

    fun fileOrExtracted(): File {
        return if (isOnItsFile) inputFile.file
        else {
            val extractedFile =
                File(inputFile.file.parentFile, inputFile.file.nameWithoutExtension + "@extracted@track$id.$extension")
            if (!extractedFile.exists()) {
                print("Extracting track...")
                MkvToolnix.merge(extractedFile).addTrack(mkvTrack).executeAndPrint(true)
                println("OK")
            }
            extractedFile
        }
    }

    override fun equals(other: Any?) = other is Track && other.inputFile == inputFile && other.id == id
    override fun hashCode() = Objects.hash(inputFile, id)

    override fun toString() = "Track $id of file ${file.name}"

    companion object {
        fun from(inputFile: InputFile, mkvTrack: MkvToolnixTrack, ffprobeStream: FFmpegStream): Track? {
            val file = mkvTrack.fileIdentification.fileName

            var language = mkvTrack.properties?.language
            if ((language == null || language.isUndefined()) && mkvTrack.fileIdentification.tracks.first { it.type == mkvTrack.type } == mkvTrack) {
                language = file.findLanguage()
            }
            return if (language == null) {
                System.err.println("Track ${mkvTrack.id} of file ${file.name} skipped because of no language")
                null
            } else {
                Track(inputFile, mkvTrack, ffprobeStream, language)
            }
        }

        private fun File.findLanguage(): MkvToolnixLanguage? {
            return find { f ->
                val map = f.name.split(Regex("(\\s+|_|\\.)")).asSequence()
                    .filter { it.length in 2..3 }
                    .groupingBy { s ->
                        MkvToolnixLanguage.all[s.toLowerCase()]
                            ?: MkvToolnixLanguage.all.values.singleOrNull { it.iso639_1 == s.toLowerCase() }
                    }
                    .eachCount()
                val max = map.values.max()
                map.entries.singleOrNull { it.value == max }?.key
            }
        }

        private fun <T> File.find(finder: (File) -> T?): T? {
            val found = finder(this)
            val parent = absoluteFile.parentFile?.absoluteFile
            return if (found == null && parent != null && parent != Main.workingDir) {
                parent.find(finder)
            } else found
        }
    }
}

fun sameFile(track1: Track, track2: Track) =
    track1.file.absolutePath == track2.file.absolutePath