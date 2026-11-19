package com.jefflim.oras.ui.parent

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.jefflim.oras.data.Kid
import com.jefflim.oras.ui.components.Card
import com.jefflim.oras.ui.components.CodeDots
import com.jefflim.oras.ui.components.CodePad
import com.jefflim.oras.ui.components.GhostButton
import com.jefflim.oras.ui.components.Hairline
import com.jefflim.oras.ui.components.KidBadge
import com.jefflim.oras.ui.components.Kicker
import com.jefflim.oras.ui.components.PrimaryButton
import com.jefflim.oras.ui.components.SectionHead
import com.jefflim.oras.ui.components.StatusTag
import com.jefflim.oras.ui.components.ToggleRow
import com.jefflim.oras.ui.components.tap
import com.jefflim.oras.ui.theme.Accent
import com.jefflim.oras.ui.theme.Faint
import com.jefflim.oras.ui.theme.Green
import com.jefflim.oras.ui.theme.GreenSoft
import com.jefflim.oras.ui.theme.Ink
import com.jefflim.oras.ui.theme.KidHues
import com.jefflim.oras.ui.theme.Line
import com.jefflim.oras.ui.theme.Muted
import com.jefflim.oras.ui.theme.Paper
import com.jefflim.oras.ui.theme.Red
import com.jefflim.oras.ui.theme.T
import com.jefflim.oras.ui.theme.hueOf

@Composable
fun KidsScreen(
    kids: List<Kid>,
    onEdit: (Kid) -> Unit,
    onAdd: () -> Unit,
    onMove: (Kid, Int) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        SectionHead(
            kicker = "Children",
            title = "Who shares the phone",
            description = "This is the turn order. After a turn ends, the next one down is up."
        )
        Spacer(Modifier.height(24.dp))

        if (kids.isEmpty()) {
            Card {
                Text("No children yet", style = T.title, color = Ink)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Each child gets a four digit code. That is the only thing they need " +
                        "to remember.",
                    style = T.body,
                    color = Muted
                )
            }
        } else {
            Card(padding = PaddingValues(0.dp)) {
                kids.forEachIndexed { i, kid ->
                    if (i > 0) Hairline()
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .tap { onEdit(kid) }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        KidBadge(kid.name, kid.hue, size = 38)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    kid.name,
                                    style = T.bodyStrong,
                                    color = if (kid.enabled) Ink else Faint
                                )
                                if (!kid.enabled) {
                                    Spacer(Modifier.width(8.dp))
                                    StatusTag("Paused", Muted, Line)
                                }
                            }
                            Spacer(Modifier.height(3.dp))
                            Text(
                                if (kid.hasAppLimit) "${kid.allowedApps.size} apps"
                                else "Every app",
                                style = T.micro,
                                color = Muted
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            MoveKey("UP", i > 0) { onMove(kid, -1) }
                            Spacer(Modifier.height(5.dp))
                            MoveKey("DN", i < kids.lastIndex) { onMove(kid, 1) }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        PrimaryButton("Add a child", onAdd)
    }
}

@Composable
private fun MoveKey(glyph: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(38.dp, 26.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Paper)
            .tap(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(glyph, style = T.label, color = if (enabled) Muted else Line)
    }
}

/**
 * One child: a name, a colour, a code, and their apps. Time is not here on purpose, it
 * belongs to the whole family and lives in Rules.
 */
@Composable
fun KidEditor(
    original: Kid,
    isNew: Boolean,
    existingNames: List<String>,
    enforcementOn: Boolean,
    onSave: (Kid, newCode: String?) -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
    onPickApps: (Kid) -> Unit,
) {
    var draft by remember { mutableStateOf(original) }
    var code by remember { mutableStateOf("") }
    var settingCode by remember { mutableStateOf(isNew) }

    val nameTaken = draft.name.isNotBlank() &&
        existingNames.any { it.equals(draft.name.trim(), ignoreCase = true) }
    val canSave = draft.name.isNotBlank() && !nameTaken &&
        (!settingCode || code.length == 4) && (!isNew || code.length == 4)

    Column(Modifier.fillMaxWidth()) {
        SectionHead(
            kicker = if (isNew) "New child" else "Edit",
            title = if (isNew) "Add a child" else draft.name.ifBlank { "This child" }
        )
        Spacer(Modifier.height(24.dp))

        Card {
            Kicker("Name", color = Muted)
            Spacer(Modifier.height(10.dp))
            NameField(draft.name) { draft = draft.copy(name = it) }
            if (nameTaken) {
                Spacer(Modifier.height(8.dp))
                Text("Another child already uses that name", style = T.micro, color = Red)
            }

            Spacer(Modifier.height(24.dp))
            Kicker("Colour", color = Muted)
            Spacer(Modifier.height(6.dp))
            Text(
                "How they are marked on the lock screen and in history.",
                style = T.micro,
                color = Muted
            )
            Spacer(Modifier.height(14.dp))
            HuePicker(draft.hue) { draft = draft.copy(hue = it) }
        }

        Spacer(Modifier.height(16.dp))

        Card {
            if (isNew) {
                // Picking apps leaves this screen, which would throw away the code being
                // typed here. It is offered once the child exists and nothing is at risk.
                Kicker("Apps", color = Muted)
                Spacer(Modifier.height(8.dp))
                Text("Every app, for now", style = T.bodyStrong, color = Ink)
                Spacer(Modifier.height(3.dp))
                Text(
                    "Once ${draft.name.trim().ifBlank { "they" }} is added, you can narrow " +
                        "this to just the apps you are happy with.",
                    style = T.micro, color = Muted
                )
            } else {
                AppLimitSummary(draft.allowedApps.size, enforcementOn) {
                    onPickApps(draft.copy(name = draft.name.trim()))
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Card {
            Kicker("Code", color = Muted)
            Spacer(Modifier.height(10.dp))

            if (!settingCode) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Four digits, already set", style = T.bodyStrong, color = Ink)
                        Spacer(Modifier.height(3.dp))
                        Text(
                            "Codes are stored scrambled, so nobody can read one back, " +
                                "not even out of the phone's own files.",
                            style = T.micro, color = Muted
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    GhostButton("Change", { settingCode = true; code = "" })
                }
            } else {
                Text(
                    if (isNew) "Pick four digits they will remember"
                    else "Enter the new four digits",
                    style = T.body, color = Muted
                )
                Spacer(Modifier.height(18.dp))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CodeDots(code.length, 4, color = hueOf(draft.hue).tint)
                }
                Spacer(Modifier.height(20.dp))
                CodePad(code, 4, { code = it })
                if (!isNew) {
                    Spacer(Modifier.height(12.dp))
                    GhostButton("Keep the old code", { settingCode = false; code = "" })
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Card {
            ToggleRow(
                "Can use the phone",
                "Turn this off to pause a child without deleting their history.",
                draft.enabled
            ) { draft = draft.copy(enabled = it) }
        }

        Spacer(Modifier.height(24.dp))

        PrimaryButton(
            if (isNew) "Add ${draft.name.trim().ifBlank { "child" }}" else "Save changes",
            { onSave(draft.copy(name = draft.name.trim()), code.takeIf { it.length == 4 }) },
            enabled = canSave
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GhostButton("Cancel", onCancel, Modifier.weight(1f))
            if (!isNew) {
                GhostButton("Remove", onDelete, Modifier.weight(1f), color = Red)
            }
        }
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun NameField(value: String, onChange: (String) -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Paper)
            .border(BorderStroke(1.dp, Line), RoundedCornerShape(8.dp))
            .padding(horizontal = 14.dp, vertical = 14.dp)
    ) {
        if (value.isEmpty()) Text("Their name", style = T.body, color = Faint)
        BasicTextField(
            value = value,
            onValueChange = { if (it.length <= 18) onChange(it) },
            singleLine = true,
            textStyle = T.body.copy(color = Ink),
            cursorBrush = SolidColor(Accent),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Done
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun HuePicker(selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        KidHues.forEachIndexed { i, hue ->
            val isOn = i == selected
            Box(
                Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(hue.soft)
                    .border(
                        BorderStroke(if (isOn) 2.dp else 1.dp, if (isOn) hue.deep else Line),
                        RoundedCornerShape(8.dp)
                    )
                    .tap { onSelect(i) },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier
                        .size(if (isOn) 16.dp else 11.dp)
                        .clip(CircleShape)
                        .background(hue.tint)
                )
            }
        }
    }
}

/** Shown once after a code is set, so a parent can pass it on correctly. */
@Composable
fun CodeSetConfirmation(name: String, code: String, onDone: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        SectionHead(
            kicker = "Saved",
            title = "$name's code is set",
            description = "Show it to them once. This is the last time it can be read back, " +
                "because from here it is stored scrambled."
        )
        Spacer(Modifier.height(28.dp))
        Card {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(code, style = T.clock, color = Ink)
                    Spacer(Modifier.height(10.dp))
                    StatusTag("Write it down", Green, GreenSoft)
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        PrimaryButton("Done", onDone)
    }
}
