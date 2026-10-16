package com.funpixelnest.mgp.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.funpixelnest.mgp.ui.Tab
import com.funpixelnest.mgp.ui.theme.MgpColors

/**
 * Bottom navigation where the selected tab grows, lifts and gets a glowing circle,
 * with a bouncy spring so the change is easy to notice.
 */
@Composable
fun AnimatedBottomBar(
    selected: Tab,
    onSelect: (Tab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .shadow(12.dp)
            .background(MgpColors.Surface)
            .navigationBarsPadding()
            .padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.Bottom,
    ) {
        Tab.entries.forEach { tab ->
            BottomBarItem(
                tab = tab,
                isSelected = tab == selected,
                onClick = { onSelect(tab) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun BottomBarItem(
    tab: Tab,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bouncy = spring<Float>(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
    val scale by animateFloatAsState(if (isSelected) 1.35f else 1f, bouncy, label = "tabScale")
    val glowAlpha by animateFloatAsState(if (isSelected) 1f else 0f, label = "tabGlow")
    val lift by animateDpAsState(
        targetValue = if (isSelected) (-6).dp else 0.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "tabLift",
    )
    val labelColor by animateColorAsState(
        if (isSelected) MgpColors.Primary else MgpColors.TextMuted,
        label = "tabLabelColor",
    )

    Column(
        modifier = modifier
            .selectable(
                selected = isSelected,
                onClick = onClick,
                role = Role.Tab,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            )
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .offset(y = lift)
                .size(44.dp),
            contentAlignment = Alignment.Center,
        ) {
            // Glow circle behind the selected icon.
            Box(
                Modifier
                    .size(44.dp)
                    .graphicsLayer {
                        alpha = glowAlpha
                        scaleX = 0.6f + 0.4f * glowAlpha
                        scaleY = 0.6f + 0.4f * glowAlpha
                    }
                    .background(MgpColors.Sunshine.copy(alpha = 0.45f), CircleShape),
            )
            Icon(
                imageVector = if (isSelected) tab.selectedIcon else tab.icon,
                contentDescription = null, // the label below names the tab
                tint = labelColor,
                modifier = Modifier
                    .size(24.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    },
            )
        }
        Text(
            tab.label,
            color = labelColor,
            fontSize = if (isSelected) 12.sp else 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
        )
    }
}
