package dev.jmx.client

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel

/** 保留正在阅读的入口，旋转/分屏重建 Activity 后继续由阅读器恢复叶页进度。 */
internal class ReaderSession : ViewModel() {
    val request = mutableStateOf<ReaderLaunchRequest?>(null)
}
