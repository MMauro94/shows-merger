package com.github.mmauro94.shows_merger.subtitles

import com.github.mmauro94.shows_merger.util.DurationSpan

abstract class SubtitleLine<TEXT>(val time: DurationSpan, val text: TEXT)