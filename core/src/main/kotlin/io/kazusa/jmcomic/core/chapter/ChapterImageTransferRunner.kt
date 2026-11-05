package io.kazusa.jmcomic.core.chapter

import io.kazusa.jmcomic.core.download.DownloadObserver
import io.kazusa.jmcomic.core.image.ImageRestoreBatchResult
import io.kazusa.jmcomic.core.image.ImageRestoreBatchRunner
import io.kazusa.jmcomic.core.image.ImageStoreBatchResult
import io.kazusa.jmcomic.core.image.ImageStoreBatchRunner
import io.kazusa.jmcomic.core.result.JmxResult

data class ChapterImageTransferOptions(

    val headers: Map<String, String>? = null,
    val acceptedContentTypes: Set<String> = setOf("image/*"),
    val maxBytes: Long? = null,
    val observerFactory: (index: Int, url: String) -> DownloadObserver = { _, _ -> DownloadObserver.None }
)

data class ChapterImageTransferReport(
    val restoreResults: List<ImageRestoreBatchResult>,
    val storeResults: List<ImageStoreBatchResult>
) {
    val totalCount: Int = restoreResults.size
    val restoredOrOriginalCount: Int = restoreResults.count { it.result is JmxResult.Success }
    val storedCount: Int = storeResults.count { it.result is JmxResult.Success }
    val failedCount: Int = restoreResults.count { it.result is JmxResult.Failure } +
        storeResults.count { it.result is JmxResult.Failure }
}

class ChapterImageTransferRunner(
    private val restoreBatchRunner: ImageRestoreBatchRunner,
    private val storeBatchRunner: ImageStoreBatchRunner
) {
    suspend fun transfer(
        template: ChapterTemplate,
        options: ChapterImageTransferOptions = ChapterImageTransferOptions()
    ): ChapterImageTransferReport {
        val restoreResults = restoreBatchRunner.downloadAndRestore(
            template.toImageDownloadRequests(
                headers = options.headers
                    ?: io.kazusa.jmcomic.core.download.ImageHttpHeaders.default(
                        refererHost = template.imageHost
                    ),
                acceptedContentTypes = options.acceptedContentTypes,
                maxBytes = options.maxBytes,
                observerFactory = options.observerFactory
            )
        )
        val storeResults = storeBatchRunner.save(restoreResults)
        return ChapterImageTransferReport(
            restoreResults = restoreResults,
            storeResults = storeResults
        )
    }
}
