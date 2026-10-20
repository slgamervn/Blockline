package com.blockline.app.ui

import com.blockline.app.R

internal data class CodeSnippet(
    val id: String,
    val titleRes: Int,
    val descriptionRes: Int,
    val languageRes: Int,
    val code: String
)

internal val builtInSnippets = listOf(
    CodeSnippet(
        id = "mcfunction.broadcast",
        titleRes = R.string.snippet_mcfunction_title,
        descriptionRes = R.string.snippet_mcfunction_description,
        languageRes = R.string.snippet_mcfunction_language,
        code = """tellraw @a {"rawtext":[{"text":"Hello from Blockline!"}]}"""
    ),
    CodeSnippet(
        id = "javascript.player-spawn",
        titleRes = R.string.snippet_javascript_title,
        descriptionRes = R.string.snippet_javascript_description,
        languageRes = R.string.snippet_javascript_language,
        code = """import { world } from "@minecraft/server";

world.afterEvents.playerSpawn.subscribe((event) => {
    if (!event.initialSpawn) return;
    event.player.sendMessage("Welcome to the world!");
});"""
    )
)
