package io.kazusa.jmcomic

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector

@Immutable
internal data class NavigationTab(
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
) {
    fun iconFor(selected: Boolean): ImageVector = if (selected) selectedIcon else icon
}
