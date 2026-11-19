package com.jefflim.oras.ui.kid

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jefflim.oras.core.TimeRules
import com.jefflim.oras.data.AppState
import com.jefflim.oras.system.AppEntry
import com.jefflim.oras.system.InstalledApps
import com.jefflim.oras.ui.components.KidBadge
import com.jefflim.oras.ui.components.tap
import com.jefflim.oras.ui.theme.Accent
import com.jefflim.oras.ui.theme.InkDeep
import com.jefflim.oras.ui.theme.InkRaised
import com.jefflim.oras.ui.theme.OnInk
import com.jefflim.oras.ui.theme.OnInkFaint
import com.jefflim.oras.ui.theme.OnInkMuted
import com.jefflim.oras.ui.theme.T
import com.jefflim.oras.ui.theme.hueOf

/**
 * The child's home screen during their turn. Their name, their time, their apps.
 *
 * Pressing Home lands here rather than on the full launcher, so this is the whole phone
 * as far as they are concerned. The countdown is always visible: a child who can see the
 * time going is not ambushed when it runs out.
 */
@Composable
fun KidHome(state: AppState, now: Long, onOpen: (String) -> Unit) {
    val context = LocalContext.current
    val session = state.session
    val kid = state.kid(session?.kidId)

    val apps: List<AppEntry> = remember(kid?.allowedApps) {
        val all = InstalledApps.load(context)
        val set = kid?.allowedApps ?: emptySet()
        if (set.isEmpty()) all else all.filter { it.packageName in set }
    }

    val remaining = session?.remainingAt(now) ?: 0
    val low = remaining in 1..300

    Box(
        Modifier
            .fillMaxSize()
            .background(InkDeep)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(18.dp))

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (kid != null) {
                    KidBadge(kid.name, kid.hue, size = 40, onDark = true)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(kid.name, style = T.title, color = OnInk)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            if (session?.paused == true) "Paused" else "Your turn",
                            style = T.micro,
                            color = OnInkMuted
                        )
                    }
                }
                Box(
                    Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (low) Accent else InkRaised)
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Text(
                        TimeRules.formatDuration(remaining),
                        style = T.clockSmall.copy(fontSize = T.title.fontSize),
                        color = if (low) InkDeep else OnInk
                    )
                }
            }

            Spacer(Modifier.height(28.dp))

            if (apps.isEmpty()) {
                Text("No apps chosen yet", style = T.h2, color = OnInk)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Ask a parent to pick which apps you can open.",
                    style = T.body,
                    color = OnInkFaint
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(apps, key = { it.packageName }) { app ->
                        AppTile(app) { onOpen(app.packageName) }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppTile(app: AppEntry, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .tap { onClick() }
            .padding(vertical = 8.dp)
    ) {
        Box(
            Modifier
                .size(58.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(InkRaised),
            contentAlignment = Alignment.Center
        ) {
            app.icon?.let {
                Image(
                    painter = BitmapPainter(it, filterQuality = FilterQuality.Medium),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(44.dp)
                )
            } ?: Text(app.label.take(1).uppercase(), style = T.title, color = Accent)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            app.label,
            style = T.micro,
            color = OnInkMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}
