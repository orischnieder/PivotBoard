package com.ori.pivotboard_project.utilities

/** Static, non-user data. Nothing here talks to the network. */
object DataManager {

    /** Options for the Create Post setup-type spinner. The first entry is the default. */
    val setupTypes: List<String> = listOf(
        "Episodic Pivot",
        "Breakout",
        "Pullback",
        "Flag",
        "Reversal",
        "Gap Up",
        "Other"
    )

    val defaultSetupType: String get() = setupTypes.first()
}
