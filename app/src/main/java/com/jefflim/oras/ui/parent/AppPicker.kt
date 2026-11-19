package com.jefflim.oras.ui.parent

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import com.jefflim.oras.system.AppEntry
import com.jefflim.oras.system.InstalledApps
import com.jefflim.oras.ui.components.Card
import com.jefflim.oras.ui.components.GhostButton
import com.jefflim.oras.ui.components.Hairline
import com.jefflim.oras.ui.components.Kicker
import com.jefflim.oras.ui.components.NoticeBlock
import com.jefflim.oras.ui.components.PrimaryButton
import com.jefflim.oras.ui.components.SectionHead
import com.jefflim.oras.ui.components.tap
import com.jefflim.oras.ui.theme.Accent
import com.jefflim.oras.ui.theme.AccentSoft
import com.jefflim.oras.ui.theme.Amber
import com.jefflim.oras.ui.theme.AmberSoft
import com.jefflim.oras.ui.theme.Ink
import com.jefflim.oras.ui.theme.Line
import com.jefflim.oras.ui.theme.Muted
import com.jefflim.oras.ui.theme.Paper
import com.jefflim.oras.ui.theme.T

/**
 * Parent-only. Reached from a child's card, which itself sits behind the parent code,
 * so a child can neither see this list nor change it.
 */
@Composable
fun AppPicker(
    kidName: String,
    selected: Set<String>,
    enforcementOn: Boolean,
    onEnableEnforcement: () -> Unit,
    onDone: (Set<String>) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val apps = remember { InstalledApps.load(context) }
    var picked by remember { mutableStateOf(selected) }

    Column(Modifier.fillMaxWidth()) {
        SectionHead(
            kicker = "Apps",
            title = "What ${kidName.ifBlank { "they" }} can open",
            description = "Pick nothing and every app stays open to them. Pick even one and " +
                "everything else is blocked during their turn."
        )
        Spacer(Modifier.height(20.dp))

        // Blocking is carried out by the accessibility service. Without it this list is
        // saved and simply never enforced, which is the worst possible failure: a parent
        // believing a limit is in place when it is not.
        if (!enforcementOn) {
            NoticeBlock(
                "This list will not be enforced yet",
                "Blocking apps needs Strong lock turned on in Android's accessibility " +
                    "settings. Until then Oras can save your choice but cannot act on it.",
                Amber, AmberSoft
            )
            Spacer(Modifier.height(10.dp))
            GhostButton("Turn on Strong lock", onEnableEnforcement)
            Spacer(Modifier.height(20.dp))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GhostButton("Select none", { picked = emptySet() }, Modifier.weight(1f))
            GhostButton(
                "Select all",
                { picked = apps.map { it.packageName }.toSet() },
                Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(8.dp))
        Text(
            if (picked.isEmpty()) "No limit set, they can open anything"
            else "${picked.size} allowed, everything else blocked",
            style = T.micro,
            color = if (picked.isEmpty()) Muted else Accent
        )

        Spacer(Modifier.height(16.dp))

        Card(padding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
            apps.forEachIndexed { i, app ->
                if (i > 0) Hairline()
                AppRow(app, app.packageName in picked) {
                    picked = if (app.packageName in picked) picked - app.packageName
                    else picked + app.packageName
                }
            }
            if (apps.isEmpty()) {
                Text(
                    "No other apps found on this phone.",
                    style = T.body,
                    color = Muted,
                    modifier = Modifier.padding(20.dp)
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        PrimaryButton("Save", { onDone(picked) })
        Spacer(Modifier.height(10.dp))
        GhostButton("Cancel", onCancel)
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun AppRow(app: AppEntry, checked: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (checked) AccentSoft.copy(alpha = 0.4f) else Paper)
            .tap { onToggle() }
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(Paper),
            contentAlignment = Alignment.Center
        ) {
            app.icon?.let {
                Image(
                    painter = BitmapPainter(it, filterQuality = FilterQuality.Medium),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(app.label, style = T.bodyStrong, color = Ink, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(if (checked) Accent else Paper)
                .border(
                    BorderStroke(1.dp, if (checked) Accent else Line),
                    RoundedCornerShape(7.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (checked) Text("OK", style = T.label.copy(fontSize = T.label.fontSize), color = Paper)
        }
    }
}

/** Summary row shown on the child's card, so the setting is never hidden from a parent. */
@Composable
fun AppLimitSummary(count: Int, enforcementOn: Boolean, onEdit: () -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Kicker("Apps", color = Muted)
                Spacer(Modifier.height(8.dp))
                Text(
                    if (count == 0) "Every app" else "$count apps",
                    style = T.bodyStrong,
                    color = Ink
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    if (count == 0) {
                        "No limit. They can open anything on the phone during their turn."
                    } else {
                        "Anything else is blocked while their turn is running."
                    },
                    style = T.micro,
                    color = Muted
                )
            }
            Spacer(Modifier.width(12.dp))
            GhostButton(if (count == 0) "Choose" else "Change", onEdit)
        }
        if (count > 0 && !enforcementOn) {
            Spacer(Modifier.height(14.dp))
            NoticeBlock(
                "Not being enforced",
                "Strong lock is off, so this list is saved but nothing is actually blocked. " +
                    "Turn it on in Rules.",
                Amber, AmberSoft
            )
        }
    }
}
