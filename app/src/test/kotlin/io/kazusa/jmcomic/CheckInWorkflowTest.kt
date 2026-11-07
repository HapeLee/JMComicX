package io.kazusa.jmcomic

import io.kazusa.jmcomic.core.api.ActionResult
import io.kazusa.jmcomic.core.api.DailyCheckInfo
import io.kazusa.jmcomic.core.api.DailyRecord
import io.kazusa.jmcomic.core.result.JmxError
import io.kazusa.jmcomic.core.result.JmxResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CheckInWorkflowTest {
    private fun action(msg: String, status: String? = null) = ActionResult(status, msg, null, emptyMap())

    private fun info(signed: Boolean) = DailyCheckInfo(
        7,
        null,
        null,
        listOf(DailyRecord(todayDate(), signed, false)),
        raw = emptyMap(),
    )

    @Test
    fun checkInClassifiesMessagesWithoutDefaultingToSuccess() {
        assertEquals(CheckInAcceptance.SUBMITTED, action("Jcoin:10 EXP:10").checkInAcceptance())
        assertEquals(CheckInAcceptance.ALREADY_SIGNED, action("今天已经签到过了").checkInAcceptance())
        assertEquals(CheckInAcceptance.ALREADY_SIGNED, action("今天已經簽到過了").checkInAcceptance())
        assertEquals(CheckInAcceptance.REJECTED, action("账号异常").checkInAcceptance())
        assertEquals(CheckInAcceptance.REJECTED, action("签到未成功").checkInAcceptance())
        assertEquals(CheckInAcceptance.REJECTED, action("").checkInAcceptance())
    }

    @Test
    fun acceptedSubmissionNeedsFreshRecord() = runBlocking {
        var reads = 0
        var writes = 0
        val result = confirmedCheckIn({ JmxResult.Success(info(++reads == 2)) }) {
            writes++
            JmxResult.Success(action("Jcoin:10 EXP:10"))
        }
        assertTrue(result is JmxResult.Success)
        assertEquals(2, reads)
        assertEquals(1, writes)
    }

    @Test
    fun acceptedButUnconfirmedDoesNotComplete() = runBlocking {
        val result = confirmedCheckIn({ JmxResult.Success(info(false)) }) {
            JmxResult.Success(action("签到成功"))
        }
        assertTrue(result is JmxResult.Failure)
    }

    @Test
    fun alreadyRecordedNeverPosts() = runBlocking {
        var writes = 0
        val result = confirmedCheckIn({ JmxResult.Success(info(true)) }) {
            writes++
            JmxResult.Success(action("签到成功"))
        }
        assertTrue(result is JmxResult.Success)
        assertEquals(0, writes)
    }

    @Test
    fun ambiguousWriteIsReadBackAndNeverRepeated() = runBlocking {
        var reads = 0
        var writes = 0
        val result = confirmedCheckIn({ JmxResult.Success(info(++reads == 2)) }) {
            writes++
            JmxResult.Failure(JmxError.EmptyData("ambiguous"))
        }
        assertTrue(result is JmxResult.Success)
        assertEquals(1, writes)
    }

    @Test
    fun emptyActivityDoesNotSubmit() = runBlocking {
        var writes = 0
        val result = confirmedCheckIn({ JmxResult.Failure(JmxError.EmptyData("ambiguous")) }) {
            writes++
            JmxResult.Success(action("签到成功"))
        }
        assertTrue(result is JmxResult.Failure)
        assertEquals(0, writes)
    }

    @Test
    fun explicitRejectionIsNotRetriedOrVerified() = runBlocking {
        var reads = 0
        var writes = 0
        val result = confirmedCheckIn({ JmxResult.Success(info(reads++ == 1)) }) {
            writes++
            JmxResult.Success(action("账号异常"))
        }
        assertTrue(result is JmxResult.Failure)
        assertEquals(1, reads)
        assertEquals(1, writes)
    }
}
