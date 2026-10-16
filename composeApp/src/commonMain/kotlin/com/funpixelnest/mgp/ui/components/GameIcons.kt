package com.funpixelnest.mgp.ui.components

import org.jetbrains.compose.resources.DrawableResource

/**
 * Game id → bundled icon image (files in composeApp/src/commonMain/composeResources/drawable).
 * Games missing here fall back to their emoji.
 */
fun gameIconResource(gameId: String): DrawableResource? = gameIcons[gameId]

private val gameIcons: Map<String, DrawableResource> = emptyMap()
