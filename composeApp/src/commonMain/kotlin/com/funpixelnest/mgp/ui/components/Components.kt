package com.funpixelnest.mgp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.funpixelnest.mgp.ui.theme.MgpColors

@Composable
fun CoinBadge(coins: Long, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(MgpColors.SurfaceRaised)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("🪙")
        Text(
            text = formatCoins(coins),
            color = MgpColors.Primary,
            fontWeight = FontWeight.Bold,
        )
        if (onClick != null) Text("+", color = MgpColors.Primary, fontWeight = FontWeight.Bold)
    }
}

/** Section heading used inside tab screens. */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = modifier.padding(top = 8.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MgpTopBar(
    title: String,
    coins: Long?,
    onBack: (() -> Unit)? = null,
    onCoinsClick: (() -> Unit)? = null,
) {
    TopAppBar(
        title = { Text(title, fontWeight = FontWeight.Bold) },
        navigationIcon = {
            if (onBack != null) {
                TextButton(onClick = onBack) {
                    Text("←", style = MaterialTheme.typography.titleLarge, color = MgpColors.TextPrimary)
                }
            }
        },
        actions = {
            if (coins != null) CoinBadge(coins, Modifier.padding(end = 12.dp), onClick = onCoinsClick)
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MgpColors.Background,
            titleContentColor = MgpColors.TextPrimary,
        ),
    )
}

/** 12345 → "12,345". Kept simple and platform-independent. */
fun formatCoins(value: Long): String {
    val negative = value < 0
    val digits = (if (negative) -value else value).toString()
    val grouped = digits.reversed().chunked(3).joinToString(",").reversed()
    return if (negative) "-$grouped" else grouped
}
