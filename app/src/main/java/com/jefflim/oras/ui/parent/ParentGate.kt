package com.jefflim.oras.ui.parent

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jefflim.oras.data.Pin
import com.jefflim.oras.ui.components.CodeDots
import com.jefflim.oras.ui.components.CodePad
import com.jefflim.oras.ui.components.GhostButton
import com.jefflim.oras.ui.components.Kicker
import com.jefflim.oras.ui.theme.Ink
import com.jefflim.oras.ui.theme.Muted
import com.jefflim.oras.ui.theme.Red
import com.jefflim.oras.ui.theme.T
import kotlinx.coroutines.launch

/**
 * Changing the parent code. Old code first, then the new one twice, because a code that
 * cannot be recovered is one worth confirming.
 */
@Composable
fun ChangeParentCode(
    currentHash: String,
    onSave: (String) -> Unit,
    onCancel: () -> Unit,
) {
    var stage by remember { mutableStateOf(0) }
    var fresh by remember { mutableStateOf("") }
    var again by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var current by remember { mutableStateOf("") }

    LaunchedEffect(current) {
        if (stage == 0 && current.length == 6) {
            if (Pin.verify(current, currentHash)) {
                stage = 1
                error = null
            } else {
                error = "That is not your current code"
                current = ""
            }
        }
    }
    LaunchedEffect(fresh) {
        if (stage == 1 && fresh.length == 6) {
            stage = 2
            error = null
        }
    }
    LaunchedEffect(again) {
        if (stage == 2 && again.length == 6) {
            if (again == fresh) {
                onSave(fresh)
            } else {
                error = "Those did not match"
                again = ""
                fresh = ""
                stage = 1
            }
        }
    }

    val (title, hint, value, setter) = when (stage) {
        0 -> Quad("Your current code", "Enter the six digits you use now.", current) { s: String -> current = s }
        1 -> Quad("New code", "Six digits. Not one they have watched you type.", fresh) { s: String -> fresh = s }
        else -> Quad("Once more", "Enter the new six digits again.", again) { s: String -> again = s }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Kicker("Parent code")
        Spacer(Modifier.height(16.dp))
        Text(title, style = T.h2, color = Ink, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(hint, style = T.body, color = Muted, textAlign = TextAlign.Center)

        Spacer(Modifier.height(32.dp))
        CodeDots(value.length, 6)

        Spacer(Modifier.height(14.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(20.dp),
            contentAlignment = Alignment.Center
        ) {
            error?.let { Text(it, style = T.micro, color = Red) }
        }

        Spacer(Modifier.height(18.dp))
        CodePad(value, 6, setter)
        Spacer(Modifier.height(24.dp))
        GhostButton("Cancel", onCancel)
        Spacer(Modifier.height(40.dp))
    }
}

private data class Quad(
    val a: String,
    val b: String,
    val c: String,
    val d: (String) -> Unit,
)

/**
 * The app itself is behind the parent code. Without this, a child who can open Oras can
 * simply raise their own daily limit, and every other rule here is decoration.
 */
@Composable
fun ParentGate(parentPinHash: String, onPass: () -> Unit) {
    var code by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    val shake = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current

    LaunchedEffect(code) {
        if (code.length == 6) {
            if (Pin.verify(code, parentPinHash)) {
                onPass()
            } else {
                wrong = true
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                scope.launch {
                    shake.animateTo(1f, tween(60))
                    shake.animateTo(-1f, tween(60))
                    shake.animateTo(1f, tween(60))
                    shake.animateTo(0f, tween(60))
                }
                code = ""
            }
        } else if (code.isNotEmpty()) {
            wrong = false
        }
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Kicker("Oras")
            Spacer(Modifier.height(16.dp))
            Text("Your code", style = T.h1, color = Ink, textAlign = TextAlign.Center)
            Spacer(Modifier.height(10.dp))
            Text(
                "Six digits to open the settings.",
                style = T.body,
                color = Muted,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(36.dp))
            Box(Modifier.graphicsLayer { translationX = shake.value * 14f }) {
                CodeDots(code.length, 6)
            }

            Spacer(Modifier.height(14.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(20.dp),
                contentAlignment = Alignment.Center
            ) {
                if (wrong) {
                    Text("That code did not match", style = T.micro, color = Red)
                }
            }

            Spacer(Modifier.height(18.dp))
            CodePad(code, 6, { code = it })
            Spacer(Modifier.height(28.dp))
        }
    }
}
