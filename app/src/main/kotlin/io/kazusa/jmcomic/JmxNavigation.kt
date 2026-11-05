package io.kazusa.jmcomic

import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.nav.core.NavKey

// 构造器是 private 的（只允许经下方工厂建路由），但 data class 生成的 copy() 默认是 public，
// 等于给外部留了一条绕过工厂的后门。Kotlin 2.4 会为此告警，这里显式把 copy() 对齐到构造器可见性。
@ConsistentCopyVisibility
internal data class JmxRoute private constructor(
    val destination: Destination,
) : NavKey {
    val title: String
        get() = destination.title

    internal enum class Destination(val title: String) {
        MAIN(""),
        FAVORITES("漫画收藏"),
        UPDATES("更新列表"),
        HISTORY("观看历史"),
        DOWNLOADS("离线下载"),
        DAILY("每日签到"),
        ABOUT("关于"),
        THIRD_PARTY("第三方开源库"),
        SETTINGS("设置"),
        GROUP_ORDER("分组序列"),
    }

    companion object {
        val MAIN = JmxRoute(Destination.MAIN)
        val FAVORITES = JmxRoute(Destination.FAVORITES)
        val UPDATES = JmxRoute(Destination.UPDATES)
        val HISTORY = JmxRoute(Destination.HISTORY)
        val DOWNLOADS = JmxRoute(Destination.DOWNLOADS)
        val DAILY = JmxRoute(Destination.DAILY)
        val ABOUT = JmxRoute(Destination.ABOUT)
        val THIRD_PARTY = JmxRoute(Destination.THIRD_PARTY)
        val SETTINGS = JmxRoute(Destination.SETTINGS)
        val GROUP_ORDER = JmxRoute(Destination.GROUP_ORDER)
    }
}

@Stable
internal class JmxMainPagerState(
    val pagerState: PagerState,
    private val coroutineScope: CoroutineScope,
) {
    var selectedPage by mutableIntStateOf(pagerState.currentPage)
        private set

    private var navigating by mutableStateOf(false)
    private var navigationJob: Job? = null

    fun animateToPage(targetPage: Int) {
        if (targetPage == selectedPage) return
        navigationJob?.cancel()
        selectedPage = targetPage
        navigating = true
        navigationJob = coroutineScope.launch {
            val currentJob = coroutineContext.job
            try {
                // 官方动画可被手势立即打断，也会处理远距离跳页，不再逐像素手写滚动后补跳。
                pagerState.animateScrollToPage(targetPage)
            } finally {
                if (navigationJob == currentJob) {
                    navigating = false
                    if (pagerState.currentPage != targetPage) {
                        selectedPage = pagerState.currentPage
                    }
                }
            }
        }
    }

    fun syncPage() {
        if (!navigating && selectedPage != pagerState.currentPage) {
            selectedPage = pagerState.currentPage
        }
    }
}

@Composable
internal fun rememberJmxMainPagerState(pageCount: Int): JmxMainPagerState {
    val pagerState = rememberPagerState(pageCount = { pageCount })
    val coroutineScope = rememberCoroutineScope()
    return remember(pagerState, coroutineScope) {
        JmxMainPagerState(pagerState, coroutineScope)
    }
}
