package io.kazusa.jmcomic

import androidx.compose.runtime.Composable
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.TextButtonColors
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 破坏性确认统一一套配色；确认完成等正向动作仍走主色。 */
@Composable
internal fun destructiveTextButtonColors(): TextButtonColors = ButtonDefaults.textButtonColorsPrimary(
    color = MiuixTheme.colorScheme.error,
    textColor = MiuixTheme.colorScheme.onError,
    disabledColor = MiuixTheme.colorScheme.disabledSecondaryVariant,
    disabledTextColor = MiuixTheme.colorScheme.disabledOnSecondaryVariant,
)
