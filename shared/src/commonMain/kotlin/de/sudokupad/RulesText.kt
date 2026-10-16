package de.sudokupad

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal data class RuleLine(val text: String, val bullet: Boolean = false)

private val ruleUrl = Regex("\\b(?:https?://|www\\.)[^\\s<>\"]+", RegexOption.IGNORE_CASE)

internal fun linkedRuleText(text: String, linkColor: Color) = buildAnnotatedString {
    var offset = 0
    ruleUrl.findAll(text).forEach { match ->
        var url = match.value.trimEnd('.', ',', ';', ':', '!', '?', '\'')
        // Keep balanced parentheses in URLs, but leave enclosing punctuation outside the link.
        for ((open, close) in listOf('(' to ')', '[' to ']', '{' to '}')) {
            while (url.endsWith(close) && url.count { it == close } > url.count { it == open }) {
                url = url.dropLast(1)
            }
        }
        append(text.substring(offset, match.range.first))
        val target = if (url.startsWith("www.", ignoreCase = true)) "https://$url" else url
        withLink(LinkAnnotation.Url(target, TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)))) {
            append(url)
        }
        offset = match.range.first + url.length
    }
    append(text.substring(offset))
}

internal fun ruleLines(rules: List<String>): List<RuleLine> = rules.flatMap { rule ->
    rule.lines().map { line ->
        val trimmed = line.trim()
        val marker = Regex("^(?:[-*+]\\s+|[•●▪]\\s*)").find(trimmed)
        RuleLine(if (marker == null) trimmed else trimmed.substring(marker.value.length), marker != null)
    }
}

@Composable
internal fun RulesText(rules: List<String>) {
    Column {
        ruleLines(rules).forEach { line ->
            if (line.text.isBlank()) {
                Spacer(Modifier.height(10.dp))
            } else {
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                    if (line.bullet) Text("•", Modifier.width(20.dp), lineHeight = 20.sp)
                    Text(linkedRuleText(line.text, MaterialTheme.colorScheme.primary), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, lineHeight = 20.sp)
                }
            }
        }
    }
}
