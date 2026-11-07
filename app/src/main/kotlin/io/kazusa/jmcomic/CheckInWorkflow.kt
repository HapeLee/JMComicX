package io.kazusa.jmcomic

import io.kazusa.jmcomic.core.api.ActionResult
import io.kazusa.jmcomic.core.api.DailyCheckInfo
import io.kazusa.jmcomic.core.result.JmxError
import io.kazusa.jmcomic.core.result.JmxResult
import java.util.Locale

internal enum class CheckInAcceptance {
    /** 响应明确接受本次提交。 */
    SUBMITTED,

    /** 响应明确表示今天早就签过了。 */
    ALREADY_SIGNED,

    /** 响应明确失败（status 为 fail，或文案带失败词），权威到可以直接判死。 */
    REJECTED,

    /**
     * 响应既不像成功也不像失败：文案不在白名单里。
     *
     * 不能当成 [REJECTED]——签到成功文案会随活动变体（"签到成功，获得 10 Jcoin"），
     * 而白名单外的成功响应一旦被判死，用户的积分已经到账却看到失败，当天也没有第二次机会。
     * 这种情况只能回读当日记录来定论。
     */
    UNCONFIRMED,
}

/** 服务端 status/message 不是 Python 客户端的归一化结果码，只有明确文案才算成功。 */
internal fun ActionResult.checkInAcceptance(): CheckInAcceptance {
    val text = message.orEmpty().trim().lowercase(Locale.ROOT)
    val state = status.orEmpty().trim().lowercase(Locale.ROOT)
    if (listOf(
            "今天已经签到过了", "今天已經簽到過了", "已经签到", "已經簽到", "已签到", "已簽到",
            "already checked in", "already signed",
        ).any { it in text }
    ) {
        return CheckInAcceptance.ALREADY_SIGNED
    }
    if (state in setOf("fail", "failed", "error", "false", "0") ||
        listOf("失败", "失敗", "异常", "異常", "拒绝", "拒絕", "未成功", "不成功", "无权限", "無權限").any { it in text }
    ) {
        return CheckInAcceptance.REJECTED
    }
    return if (state in setOf("ok", "success", "true", "1") ||
        text == "签到成功" || text == "簽到成功" ||
        Regex("(?i)^(?:Jcoin|EXP):\\s*\\d+(?:\\s+(?:Jcoin|EXP):\\s*\\d+)*$").matches(text)
    ) {
        CheckInAcceptance.SUBMITTED
    } else {
        CheckInAcceptance.UNCONFIRMED
    }
}

/**
 * 只提交一次，然后重新读取当日记录；歧义响应绝不盲目重放。
 *
 * [knownInfo] 用于调用方"刚读过当日状态"的场景（自动签到），省掉一次 `/daily`；
 * 传 null 时按常规先读一遍，手动签到走的就是这条路——页面状态可能已经放了很久。
 */
internal suspend fun confirmedCheckIn(
    loadInfo: suspend () -> JmxResult<DailyCheckInfo?>,
    knownInfo: DailyCheckInfo? = null,
    submit: suspend (Int) -> JmxResult<ActionResult>,
): JmxResult<ActionResult> {
    val initial = knownInfo ?: when (val result = loadInfo()) {
        is JmxResult.Failure -> return result
        is JmxResult.Success -> result.value
            ?: return JmxResult.Failure(JmxError.EmptyData("暂时无法确认签到活动，请稍后重试"))
    }
    if (initial.isSignedToday()) return confirmedCheckInAction("今日已签到")
    val eventId = initial.dailyId?.takeIf { it > 0 }
        ?: return JmxResult.Failure(JmxError.Schema("签到活动编号缺失", field = "dailyId"))
    val response = submit(eventId)
    // 只有 [CheckInAcceptance.REJECTED]（status 为 fail 或文案带失败词）才跳过回读：
    // 文案白名单覆盖不到的成功响应必须交给当日记录裁决，否则会把"已到账"报成失败。
    if (response is JmxResult.Success && response.value.checkInAcceptance() == CheckInAcceptance.REJECTED) {
        return JmxResult.Failure(
            JmxError.Api(
                code = REJECTED_RESPONSE_CODE,
                message = response.value.message ?: "签到未得到明确确认",
            )
        )
    }
    if (response is JmxResult.Failure && response.error !is JmxError.EmptyData &&
        response.error !is JmxError.Network && response.error !is JmxError.Schema
    ) {
        return response
    }
    return when (val verified = loadInfo()) {
        is JmxResult.Failure -> verified
        is JmxResult.Success -> if (verified.value?.isSignedToday() == true) {
            confirmedCheckInAction(
                if (response is JmxResult.Success &&
                    response.value.checkInAcceptance() == CheckInAcceptance.ALREADY_SIGNED
                ) {
                    "今日已签到"
                } else {
                    "签到成功"
                }
            )
        } else {
            JmxResult.Failure(JmxError.EmptyData("服务端尚未确认今日签到，未重复提交，请稍后刷新"))
        }
    }
}

private fun confirmedCheckInAction(message: String): JmxResult<ActionResult> =
    JmxResult.Success(ActionResult(status = "ok", message = message, type = null, raw = emptyMap()))

/**
 * 业务拒绝用的是 [JmxError.Api] 这个载体，`code` 不是 HTTP 状态码（接口会回 200 + 失败文案）。
 * 取 401 以外的值即可：401 会被 `requiresSessionRecovery()` 当成掉登录；
 * 界面层也不再拿它拼"接口返回错误"前缀（见 `toCheckInUiMessage`）。
 */
private const val REJECTED_RESPONSE_CODE = 400
