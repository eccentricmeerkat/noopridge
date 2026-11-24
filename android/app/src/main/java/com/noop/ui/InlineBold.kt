package com.noop.ui

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

/**
 * Renders the one piece of Markdown the release notes use: `**bold**` pairs. The asterisks are dropped and the text
 * between them is set in semibold; an unpaired `**` is kept as typed, so nothing is lost. The changelog is generated
 * from the repository's release notes, which mark each entry's lead sentence this way.
 */
internal fun inlineBold(text: String): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        val open = text.indexOf("**", i)
        val close = if (open >= 0) text.indexOf("**", open + 2) else -1
        if (open < 0 || close < 0) {
            append(text.substring(i))
            break
        }
        append(text.substring(i, open))
        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(text.substring(open + 2, close)) }
        i = close + 2
    }
}
