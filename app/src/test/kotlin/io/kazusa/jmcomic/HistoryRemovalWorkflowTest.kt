package io.kazusa.jmcomic

import io.kazusa.jmcomic.core.result.JmxError
import io.kazusa.jmcomic.core.result.JmxResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class HistoryRemovalWorkflowTest {
    @Test
    fun clearHistoryRequiresEmptyReadEvenAtExactBudget() = runBlocking {
        val items = mutableListOf("1", "2")
        val result = clearHistoryVerified(
            { JmxResult.Success(items.take(1)) },
            { id -> items.remove(id); JmxResult.Success(Unit) },
            maxBatches = 2,
        )
        assertTrue(result is JmxResult.Success)
        assertTrue(items.isEmpty())
    }

    @Test
    fun successfulWriteWithUnchangedHistoryMustFail() = runBlocking {
        val result = clearHistoryVerified({ JmxResult.Success(listOf("1")) }, { JmxResult.Success(Unit) })
        assertTrue(result is JmxResult.Failure)
    }

    @Test
    fun ambiguousDeleteMayCompleteOnlyAfterReadBack() = runBlocking {
        var exists = true
        val result = clearHistoryVerified(
            { JmxResult.Success(if (exists) listOf("1") else emptyList()) },
            {
                exists = false
                JmxResult.Failure(JmxError.EmptyData("ambiguous"))
            },
        )
        assertTrue(result is JmxResult.Success)
    }

    @Test
    fun historyCancellationPropagates() = runBlocking {
        try {
            clearHistoryVerified({ JmxResult.Success(listOf("1")) }, { throw CancellationException("stop") })
            fail("cancellation swallowed")
        } catch (_: CancellationException) {
        }
    }

    @Test
    fun clearBudgetLeavesRemainingRecordsAndDoesNotClaimCompletion() = runBlocking {
        val items = mutableListOf("1", "2")
        val result = clearHistoryVerified(
            { JmxResult.Success(items.take(1)) },
            { items.remove(it); JmxResult.Success(Unit) },
            maxBatches = 1,
        )
        assertTrue(result is JmxResult.Failure)
        assertEquals(listOf("2"), items)
    }

    @Test
    fun selectedDeletesPreservePartialFailureAndRequireReadBack() = runBlocking {
        val result = deleteHistoryVerified(
            ids = linkedSetOf("1", "2"),
            remove = {
                if (it == "1") JmxResult.Success(Unit)
                else JmxResult.Failure(JmxError.Api(400, "synthetic rejection"))
            },
            loadPage = { JmxResult.Success(AlbumHistoryIds(if (it == 1) listOf("2") else emptyList())) },
        )
        assertEquals(setOf("1"), result.succeededIds)
        assertEquals(setOf("2"), result.failures.keys)
    }

    @Test
    fun selectedDeletesStopOnAccountSwitchAndDoNotReadAnotherAccount() = runBlocking {
        var current = true
        var reads = 0
        var writes = 0
        val result = deleteHistoryVerified(
            ids = linkedSetOf("1", "2"),
            remove = {
                writes++
                current = false
                JmxResult.Success(Unit)
            },
            loadPage = {
                reads++
                JmxResult.Success(AlbumHistoryIds(emptyList()))
            },
            isCurrent = { current },
        )
        assertTrue(result.succeededIds.isEmpty())
        assertEquals(2, result.failures.size)
        assertEquals(1, writes)
        assertEquals(0, reads)
    }

    @Test
    fun repeatedVerificationPageCannotProveAbsence() = runBlocking {
        val result = deleteHistoryVerified(
            setOf("1"),
            { JmxResult.Success(Unit) },
            { JmxResult.Success(AlbumHistoryIds(listOf("3"))) },
        )
        assertTrue(result.succeededIds.isEmpty())
        assertEquals(setOf("1"), result.failures.keys)
    }

    @Test
    fun verificationFailureDoesNotBecomeDeleteSuccess() = runBlocking {
        val result = deleteHistoryVerified(
            setOf("1"),
            { JmxResult.Success(Unit) },
            { JmxResult.Failure(JmxError.Schema("synthetic malformed read")) },
        )
        assertTrue(result.succeededIds.isEmpty())
        assertTrue(result.failures.getValue("1") is JmxError.Schema)
    }

    @Test
    fun nextBatchSkipsAlreadyDeletedAndStopsWhenNothingMoves() {
        assertEquals(listOf("2"), nextHistoryClearBatch(setOf("1"), listOf("1", "2")))
        assertEquals(emptyList<String>(), nextHistoryClearBatch(emptySet(), emptyList()))
        assertNull(nextHistoryClearBatch(setOf("1", "2"), listOf("1", "2")))
    }
}
