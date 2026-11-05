package io.kazusa.jmcomic.effect.liquid

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** 分组分页器向主悬浮层提供 UI 状态，让按钮跨页面切换时保留完整的进入/退出动画。 */
@Stable
internal class FloatingCategoryMenuState {
    var items by mutableStateOf<List<String>>(emptyList())
        private set
    var selectedIndex by mutableStateOf(0)
        private set
    var available by mutableStateOf(false)
        private set
    private var onSelect: (Int) -> Unit = {}

    fun update(items: List<String>, selectedIndex: Int, available: Boolean, onSelect: (Int) -> Unit) {
        this.items = items
        this.selectedIndex = selectedIndex
        this.available = available
        this.onSelect = onSelect
    }

    fun select(index: Int) {
        if (available && index in items.indices) onSelect(index)
    }
}
