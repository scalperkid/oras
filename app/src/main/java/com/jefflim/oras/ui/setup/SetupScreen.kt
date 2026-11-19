package com.jefflim.oras.ui.setup

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jefflim.oras.ui.components.Callout
import com.jefflim.oras.ui.components.Card
import com.jefflim.oras.ui.components.CodeDots
import com.jefflim.oras.ui.components.CodePad
import com.jefflim.oras.ui.components.GhostButton
import com.jefflim.oras.ui.components.Kicker
import com.jefflim.oras.ui.components.PrimaryButton
import com.jefflim.oras.ui.components.SectionHead
import com.jefflim.oras.ui.theme.Accent
import com.jefflim.oras.ui.theme.Green
import com.jefflim.oras.ui.theme.Ink
import com.jefflim.oras.ui.theme.Line
import com.jefflim.oras.ui.theme.Muted
import com.jefflim.oras.ui.theme.Red
import com.jefflim.oras.ui.theme.T

enum class SetupStep { Welcome, Code, ConfirmCode, Ready }

/**
 * First run, kept to three things: what this does, your code, and then Oras asks for
 * every permission it needs in one pass rather than sending a parent hunting.
 */
@Composable
fun SetupScreen(
    grantsDone: Int,
    grantsTotal: Int,
    allGrantsDone: Boolean,
    currentGrantTitle: String?,
    currentGrantWhy: String?,
    onRunGrants: () -> Unit,
    onFinish: (parentCode: String) -> Unit,
) {
    var step by remember { mutableStateOf(SetupStep.Welcome) }
    var code by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var mismatch by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth()) {
        StepDots(step)
        Spacer(Modifier.height(28.dp))

        AnimatedContent(
            targetState = step,
            transitionSpec = {
                (fadeIn(tween(250)) + slideInVertically(tween(350)) { it / 8 })
                    .togetherWith(fadeOut(tween(150)))
            },
            label = "setup"
        ) { current ->
            when (current) {
                SetupStep.Welcome -> Column {
                    SectionHead(
                        kicker = "Oras",
                        title = "One phone, taking turns",
                        description = "Each child has their own code and their own home " +
                            "screen. They get a turn, then the phone passes to the next one."
                    )
                    Spacer(Modifier.height(24.dp))
                    Card {
                        Point("They start their own turn", "No handing the phone back to unlock it.")
                        Spacer(Modifier.height(18.dp))
                        Point("Only the apps you choose", "Their home screen shows nothing else.")
                        Spacer(Modifier.height(18.dp))
                        Point("Turns rotate by themselves", "Finish a turn, go to the back of the queue.")
                        Spacer(Modifier.height(18.dp))
                        Point("You can always get in", "Your six digit code clears any lock.")
                    }
                    Spacer(Modifier.height(24.dp))
                    PrimaryButton("Start", { step = SetupStep.Code })
                }

                SetupStep.Code -> CodeStep(
                    title = "Your code",
                    description = "Six digits, yours alone. It opens this app and clears any " +
                        "lock. Not one the children have watched you type.",
                    value = code,
                    onChange = { code = it },
                    onNext = { step = SetupStep.ConfirmCode },
                    onBack = { step = SetupStep.Welcome }
                )

                SetupStep.ConfirmCode -> CodeStep(
                    title = "Once more",
                    description = "Enter the same six digits again. There is no way to " +
                        "recover this later.",
                    value = confirm,
                    error = if (mismatch) "Those did not match. Try again." else null,
                    onChange = { confirm = it; mismatch = false },
                    onNext = {
                        if (confirm == code) step = SetupStep.Ready
                        else { mismatch = true; confirm = "" }
                    },
                    onBack = { confirm = ""; mismatch = false; step = SetupStep.Code }
                )

                SetupStep.Ready -> Column {
                    SectionHead(
                        kicker = "Last step",
                        title = "Let Oras set itself up",
                        description = "Android asks for these one at a time. Tap once and " +
                            "Oras will walk you through all of them."
                    )
                    Spacer(Modifier.height(24.dp))

                    Card {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    if (allGrantsDone) "All set" else "$grantsDone of $grantsTotal done",
                                    style = T.title,
                                    color = if (allGrantsDone) Green else Ink
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    if (allGrantsDone) {
                                        "Everything Oras needs is in place."
                                    } else {
                                        currentGrantWhy ?: "Tap to continue."
                                    },
                                    style = T.micro,
                                    color = Muted
                                )
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        GrantTrack(grantsDone, grantsTotal)
                    }

                    Spacer(Modifier.height(20.dp))

                    if (!allGrantsDone) {
                        PrimaryButton(
                            currentGrantTitle?.let { "Allow: $it" } ?: "Continue setup",
                            onRunGrants
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Android will open its own screen for each one. Come back here " +
                                "afterwards and Oras carries on from where it stopped.",
                            style = T.micro,
                            color = Muted
                        )
                        Spacer(Modifier.height(20.dp))
                    }

                    Callout(
                        title = "One habit worth starting with",
                        body = "Set the daily total lower than you think you need. Adding " +
                            "ten minutes as a reward lands far better than taking ten away."
                    )
                    Spacer(Modifier.height(16.dp))
                    PrimaryButton(
                        "Add my children",
                        { onFinish(code) },
                        enabled = allGrantsDone
                    )
                    if (!allGrantsDone) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Finish the steps above first.",
                            style = T.micro,
                            color = Muted,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    }
                    Spacer(Modifier.height(40.dp))
                }
            }
        }
    }
}

@Composable
private fun GrantTrack(done: Int, total: Int) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(total) { i ->
            Box(
                Modifier
                    .weight(1f)
                    .height(6.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (i < done) Green else Line)
            )
        }
    }
}

@Composable
private fun Point(title: String, body: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            Modifier
                .padding(top = 7.dp)
                .size(6.dp)
                .clip(CircleShape)
                .background(Accent)
        )
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, style = T.bodyStrong, color = Ink)
            Spacer(Modifier.height(3.dp))
            Text(body, style = T.micro, color = Muted)
        }
    }
}

@Composable
private fun CodeStep(
    title: String,
    description: String,
    value: String,
    onChange: (String) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
    error: String? = null,
) {
    Column {
        SectionHead(kicker = "Setting up", title = title, description = description)
        Spacer(Modifier.height(32.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            CodeDots(value.length, 6)
        }
        Spacer(Modifier.height(14.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(20.dp),
            contentAlignment = Alignment.Center
        ) {
            error?.let { Text(it, style = T.micro, color = Red, textAlign = TextAlign.Center) }
        }
        Spacer(Modifier.height(14.dp))
        CodePad(value, 6, onChange)
        Spacer(Modifier.height(24.dp))
        PrimaryButton("Continue", onNext, enabled = value.length == 6)
        Spacer(Modifier.height(10.dp))
        GhostButton("Back", onBack)
    }
}

@Composable
private fun StepDots(step: SetupStep) {
    val order = SetupStep.entries
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        order.forEach { s ->
            Box(
                Modifier
                    .height(3.dp)
                    .width(if (s == step) 26.dp else 14.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (s.ordinal <= step.ordinal) Accent else Line)
            )
        }
    }
}
