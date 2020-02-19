package com.github.mmauro94.media_merger.strategy

enum class CutsAdjustmentStrategy(val description : String) {
    /**
     * No cuts will be done
     */
    NONE("No cuts"),

    /**
     * The tracks will have an offset calculated using the first scene black fragments of the videos
     */
    FIRST_BLACK_SEGMENT_OFFSET("Adjust offset using first black segment"),

    /**
     * The tracks will have an offset calculated using the matched first scene of the videos, detected using black frames
     */
    FIRST_SCENE_OFFSET("Adjust offset using first matching scene"),

    /**
     * The scenes will be detected using black frames, cutting the tracks appropriately.
     */
    SCENES("Cut by matching scenes")
}