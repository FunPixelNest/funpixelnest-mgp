package com.funpixelnest.mgp.ui.components

import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified

/**
 * The largest font size (from [style]'s size down to [minScale] of it) at which [text] fits in [maxWidthPx]:
 * at most [maxLines] lines and no word split across lines. Narrow phones and large system font sizes make
 * fixed-size labels overflow or break words ("Tou-rnaments"); this shrinks them just enough instead.
 */
fun fitStyle(measurer: TextMeasurer, text: String, style: TextStyle, maxLines: Int, maxWidthPx: Int, minScale: Float): TextStyle {
    val base = style.fontSize
    if (!base.isSpecified || maxWidthPx <= 0 || maxWidthPx == Constraints.Infinity || text.isEmpty()) return style
    val words = text.split(' ', '\n').filter { it.isNotEmpty() }
    var scale = 1f
    while (true) {
        val s = style.copy(
            fontSize = base * scale,
            lineHeight = if (style.lineHeight.isSpecified) style.lineHeight * scale else style.lineHeight,
        )
        val widest = words.maxOf { w -> measurer.measure(w, s, softWrap = false, maxLines = 1).size.width }
        val full = measurer.measure(text, s, maxLines = maxLines, constraints = Constraints(maxWidth = maxWidthPx))
        if ((widest <= maxWidthPx && !full.hasVisualOverflow) || scale <= minScale) return s
        scale = (scale - 0.05f).coerceAtLeast(minScale)
    }
}

/** One painted pass of the text (e.g. a white outline under the fill). */
class TextLayer(val color: Color, val drawStyle: DrawStyle? = null, val shadow: Shadow? = null)

/**
 * Text that shrinks to fit its width instead of being cut or breaking words. Measures and paints the text
 * itself (no subcomposition), so it also works inside rows that ask for intrinsic sizes (equal-height cards).
 * With [fillWidth] (or a [TextAlign] other than Start) it takes the full width so alignment applies.
 */
@Composable
fun FitTextLayout(
    text: String,
    style: TextStyle,
    layers: List<TextLayer>,
    modifier: Modifier = Modifier,
    maxLines: Int = 1,
    minScale: Float = 0.6f,
    fillWidth: Boolean = false,
) {
    val measurer = rememberTextMeasurer(cacheSize = 24)
    val holder = remember { arrayOfNulls<TextLayoutResult>(1) }
    val policy = remember(text, style, maxLines, minScale, fillWidth) {
        object : MeasurePolicy {
            fun layoutFor(maxWidth: Int): TextLayoutResult {
                val s = fitStyle(measurer, text, style, maxLines, maxWidth, minScale)
                val c = if (maxWidth == Constraints.Infinity) Constraints() else Constraints(maxWidth = maxWidth)
                return measurer.measure(text, s, overflow = TextOverflow.Ellipsis, maxLines = maxLines, constraints = c)
            }

            override fun MeasureScope.measure(measurables: List<Measurable>, constraints: Constraints): MeasureResult {
                val r = layoutFor(constraints.maxWidth)
                holder[0] = r
                val w = if (fillWidth && constraints.hasBoundedWidth) constraints.maxWidth else r.size.width
                return layout(w.coerceIn(constraints.minWidth, constraints.maxWidth), r.size.height.coerceIn(constraints.minHeight, constraints.maxHeight)) {}
            }

            override fun IntrinsicMeasureScope.maxIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int) =
                measurer.measure(text, style, maxLines = maxLines).size.width
            override fun IntrinsicMeasureScope.minIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int) =
                measurer.measure(text, style.copy(fontSize = style.fontSize * minScale), maxLines = maxLines).size.width.coerceAtMost(
                    measurer.measure(text, style, maxLines = maxLines).size.width,
                )
            override fun IntrinsicMeasureScope.minIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int) = layoutFor(width).size.height
            override fun IntrinsicMeasureScope.maxIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int) = layoutFor(width).size.height
        }
    }
    Layout(
        content = {},
        modifier = modifier
            .semantics { this.text = AnnotatedString(text) }
            .drawBehind {
                val r = holder[0] ?: return@drawBehind
                // Center / end alignment inside a wider box when the text was laid out narrower.
                val dx = when (style.textAlign) {
                    TextAlign.Center -> (size.width - r.size.width) / 2f
                    TextAlign.End, TextAlign.Right -> size.width - r.size.width
                    else -> 0f
                }.coerceAtLeast(0f)
                layers.forEach { l ->
                    drawText(r, color = l.color, topLeft = androidx.compose.ui.geometry.Offset(dx, 0f), shadow = l.shadow, drawStyle = l.drawStyle)
                }
            },
        measurePolicy = policy,
    )
}

/** Plain shrink-to-fit text (labels, card titles). */
@Composable
fun FitText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit,
    fontWeight: FontWeight? = null,
    maxLines: Int = 1,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    style: TextStyle = LocalTextStyle.current,
    minScale: Float = 0.6f,
) {
    val full = style.copy(
        fontSize = fontSize,
        fontWeight = fontWeight ?: style.fontWeight,
        textAlign = textAlign ?: style.textAlign,
        lineHeight = lineHeight,
    )
    val c = if (color != Color.Unspecified) color else full.color.takeIf { it != Color.Unspecified } ?: Color.Black
    FitTextLayout(
        text, full, listOf(TextLayer(c)), modifier, maxLines, minScale,
        fillWidth = textAlign == TextAlign.Center || textAlign == TextAlign.End,
    )
}
