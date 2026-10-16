package com.funpixelnest.mgp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.funpixelnest.mgp.data.PlatformRepository
import com.funpixelnest.mgp.data.PurchaseResult
import com.funpixelnest.mgp.domain.StorePack
import com.funpixelnest.mgp.ui.components.formatCoins
import com.funpixelnest.mgp.ui.theme.MgpColors
import kotlinx.coroutines.launch

@Composable
fun StoreScreen(
    repository: PlatformRepository,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var notice by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    var buyingId by remember { mutableStateOf<String?>(null) }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 160.dp),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Coin packs", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "Use coins to enter tournaments. Demo mode: packs are added free, no payment is taken.",
                    color = MgpColors.TextMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        notice?.let { (text, ok) ->
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text,
                    color = if (ok) MgpColors.Win else Color(0xFFFF8A80),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MgpColors.Surface, RoundedCornerShape(12.dp))
                        .padding(12.dp),
                )
            }
        }
        items(repository.storePacks(), key = { it.id }) { pack ->
            PackCard(
                pack = pack,
                isBuying = buyingId == pack.id,
                onBuy = {
                    buyingId = pack.id
                    scope.launch {
                        notice = when (val result = repository.purchasePack(pack.id)) {
                            is PurchaseResult.Success -> "+🪙 ${formatCoins(result.coinsAdded)} added" to true
                            is PurchaseResult.Error -> result.message to false
                        }
                        buyingId = null
                    }
                },
            )
        }
    }
}

@Composable
private fun PackCard(pack: StorePack, isBuying: Boolean, onBuy: () -> Unit) {
    val highlighted = pack.tag != null
    Column(
        modifier = Modifier
            .background(MgpColors.Surface, RoundedCornerShape(18.dp))
            .then(
                if (highlighted) Modifier.border(2.dp, MgpColors.Primary, RoundedCornerShape(18.dp)) else Modifier,
            )
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                pack.tag ?: " ",
                color = MgpColors.OnPrimary,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                modifier = if (highlighted) {
                    Modifier
                        .background(MgpColors.Primary, RoundedCornerShape(50))
                        .padding(horizontal = 10.dp, vertical = 2.dp)
                } else {
                    Modifier.padding(vertical = 2.dp)
                },
            )
        }
        Text("🪙", fontSize = 40.sp)
        Text(pack.name, color = MgpColors.TextMuted)
        Text(formatCoins(pack.coins), fontWeight = FontWeight.Bold, fontSize = 22.sp, color = MgpColors.Primary)
        Text(
            if (pack.bonusCoins > 0) "+${formatCoins(pack.bonusCoins)} bonus" else " ",
            color = MgpColors.Win,
            style = MaterialTheme.typography.bodySmall,
        )
        Button(onClick = onBuy, enabled = !isBuying, modifier = Modifier.fillMaxWidth()) {
            Text(if (isBuying) "Adding…" else pack.priceLabel)
        }
    }
}
