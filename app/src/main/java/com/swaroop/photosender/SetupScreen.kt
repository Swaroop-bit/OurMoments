package com.swaroop.photosender

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val PinkPrimary = Color(0xFFE8275A)
val PinkLight = Color(0xFFFFF0F3)
val PinkMedium = Color(0xFFFFD6E0)
val PinkSoft = Color(0xFFFAF0F2)

data class SetupQuestion(
    val icon: String,
    val title: String,
    val subtitle: String,
    val placeholder: String,
    val hint: String,
    val correctAnswer: String,
    val isChoice: Boolean = false
)

val questions = listOf(
    SetupQuestion(
        icon = "🐾",
        title = "What does your partner call you?",
        subtitle = "Every couple has their own special name 💕",
        placeholder = "",
        hint = "Hint: It might be an animal 🐶or ghost in hindi",
        correctAnswer = ""  // no validation — free answer
    ),
    SetupQuestion(
        icon = "📅",
        title = "When was your first date?",
        subtitle = "The day everything changed 🌹",
        placeholder = "ddmmyyyy",
        hint = "Hint: It was in January... Republic Day week 🇮🇳",
        correctAnswer = "25012026"
    ),
    SetupQuestion(
        icon = "💋",
        title = "When was your first kiss?",
        subtitle = "The most special moment 🥰",
        placeholder = "ddcmmyyyy",
        hint = "Hint: Valentine's Day... the most romantic day 💝",
        correctAnswer = "14022026"
    ),
    SetupQuestion(
        icon = "👤",
        title = "Who are you?",
        subtitle = "So we know whose phone this is 🥰",
        placeholder = "",
        hint = "",
        correctAnswer = "",
        isChoice = true
    )
)

@Composable
fun SetupScreen(onDone: (String) -> Unit) {
    var step by remember { mutableStateOf(0) }
    var answers by remember { mutableStateOf(listOf("", "", "", "")) }
    var showHint by remember { mutableStateOf(false) }
    var wrongAnswer by remember { mutableStateOf(false) }
    var whoAmI by remember { mutableStateOf("") }

    // Shake animation for wrong answer
    val shakeAnim = remember { Animatable(0f) }
    LaunchedEffect(wrongAnswer) {
        if (wrongAnswer) {
            repeat(4) {
                shakeAnim.animateTo(10f, tween(50))
                shakeAnim.animateTo(-10f, tween(50))
            }
            shakeAnim.animateTo(0f, tween(50))
            wrongAnswer = false
        }
    }

    val q = questions[step]

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PinkSoft)
    ) {
        FloatingHeartsBackground()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(52.dp))

            // App name
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("♡", fontSize = 18.sp, color = PinkPrimary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("OurMoments", fontSize = 18.sp,
                    fontWeight = FontWeight.Bold, color = PinkPrimary)
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Big animated icon
            val iconScale = remember { Animatable(0.8f) }
            LaunchedEffect(step) {
                showHint = false
                iconScale.animateTo(1f,
                    spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow))
            }

            Box(
                modifier = Modifier
                    .size(100.dp)
                    .scale(iconScale.value)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(PinkPrimary.copy(alpha = 0.8f), PinkPrimary)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(q.icon, fontSize = 46.sp)
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Title + subtitle
            AnimatedContent(targetState = step, transitionSpec = {
                fadeIn(tween(300)) + slideInHorizontally { it / 2 } togetherWith
                        fadeOut(tween(200)) + slideOutHorizontally { -it / 2 }
            }, label = "title") { s ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        questions[s].title,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF1A1A1A),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        questions[s].subtitle,
                        fontSize = 14.sp,
                        color = Color(0xFF888888),
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Input area
            AnimatedContent(targetState = step, transitionSpec = {
                fadeIn(tween(300)) togetherWith fadeOut(tween(200))
            }, label = "input") { s ->
                if (questions[s].isChoice) {
                    // Who am I cards
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        WhoAmICard(
                            emoji = "💙",
                            name = "Swaroop",
                            selected = whoAmI == "swaroop",
                            modifier = Modifier.weight(1f),
                            onClick = { whoAmI = "swaroop" }
                        )
                        WhoAmICard(
                            emoji = "🩷",
                            name = "Vidhi",
                            selected = whoAmI == "vidhi",
                            modifier = Modifier.weight(1f),
                            onClick = { whoAmI = "vidhi" }
                        )
                    }
                } else {
                    // Text input with shake
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .offset(x = shakeAnim.value.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (wrongAnswer) Color(0xFFFFEEEE)
                                else Color.White
                            ),
                            elevation = CardDefaults.cardElevation(3.dp)
                        ) {
                            OutlinedTextField(
                                value = answers[s],
                                onValueChange = { newVal ->
                                    answers = answers.toMutableList()
                                        .also { it[s] = newVal }
                                },
                                placeholder = {
                                    Text(questions[s].placeholder,
                                        color = Color(0xFFCCCCCC))
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(4.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = PinkPrimary,
                                    unfocusedBorderColor = Color.Transparent,
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent
                                ),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    capitalization = KeyboardCapitalization.Words,
                                    keyboardType = if (s == 0) KeyboardType.Text
                                    else KeyboardType.Number
                                )
                            )
                        }

                        // Hint section
                        if (questions[s].hint.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(12.dp))

                            if (!showHint) {
                                // Hint button
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(PinkMedium.copy(alpha = 0.5f))
                                        .clickable { showHint = true }
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text("💡", fontSize = 14.sp)
                                    Text("Take a Hint",
                                        fontSize = 13.sp,
                                        color = PinkPrimary,
                                        fontWeight = FontWeight.SemiBold)
                                }
                            } else {
                                // Show hint
                                AnimatedVisibility(visible = showHint,
                                    enter = fadeIn() + expandVertically()) {
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(14.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = Color(0xFFFFF8DC)
                                        ),
                                        border = BorderStroke(1.dp,
                                            Color(0xFFFFD700).copy(alpha = 0.5f))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Text("💡", fontSize = 20.sp)
                                            Text(
                                                questions[s].hint,
                                                fontSize = 14.sp,
                                                color = Color(0xFF886600),
                                                fontWeight = FontWeight.Medium,
                                                lineHeight = 20.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Continue button
            Button(
                onClick = {
                    val current = questions[step]
                    when {
                        // Choice step
                        current.isChoice -> {
                            if (whoAmI.isNotEmpty()) onDone(whoAmI)
                        }
                        // Free answer (nickname) — no validation
                        current.correctAnswer.isEmpty() -> {
                            if (answers[step].isNotEmpty()) {
                                if (step < questions.size - 1) step++
                            }
                        }
                        // Validated answer
                        else -> {
                            val userAns = answers[step].trim()
                            val correct = current.correctAnswer.trim()
                            if (userAns.equals(correct, ignoreCase = true)) {
                                if (step < questions.size - 1) step++
                            } else {
                                wrongAnswer = true
                                showHint = true  // auto show hint on wrong
                            }
                        }
                    }
                },
                enabled = when {
                    questions[step].isChoice -> whoAmI.isNotEmpty()
                    else -> answers[step].isNotEmpty()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PinkPrimary,
                    disabledContainerColor = PinkMedium
                )
            ) {
                Text(
                    if (step < questions.size - 1) "Continue →" else "Let's Go! 💕",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Step dots
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                repeat(questions.size) { i ->
                    Box(
                        modifier = Modifier
                            .size(if (i == step) 24.dp else 8.dp, 8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (i == step) PinkPrimary else PinkMedium)
                    )
                }
            }

            Text("Step ${step + 1} of ${questions.size}",
                fontSize = 12.sp, color = Color(0xFFAAAAAA))

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun SetupInputCard(
    icon: String,
    label: String,
    placeholder: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(icon, fontSize = 16.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(label, fontSize = 14.sp, color = Color(0xFF555555),
                    fontWeight = FontWeight.Medium)
            }
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = { Text(placeholder, color = Color(0xFFCCCCCC)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PinkPrimary,
                    unfocusedBorderColor = PinkMedium
                ),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words
                )
            )
        }
    }
}

@Composable
fun WhoAmICard(
    emoji: String,
    name: String,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = modifier
            .height(100.dp)
            .border(
                width = if (selected) 2.dp else 0.dp,
                color = if (selected) PinkPrimary else Color.Transparent,
                shape = RoundedCornerShape(16.dp)
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) PinkLight else Color.White
        ),
        elevation = CardDefaults.cardElevation(if (selected) 4.dp else 1.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(emoji, fontSize = 32.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(name, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                color = if (selected) PinkPrimary else Color(0xFF333333))
        }
    }
}

@Composable
fun FloatingHeartsBackground() {
    Box(modifier = Modifier.fillMaxSize()) {
        val hearts = listOf(
            Triple("💕", 0.08f, 0.05f),
            Triple("♡", 0.85f, 0.08f),
            Triple("💗", 0.92f, 0.25f),
            Triple("✦", 0.05f, 0.35f),
            Triple("💕", 0.88f, 0.55f),
            Triple("♡", 0.10f, 0.70f),
            Triple("✦", 0.80f, 0.80f),
            Triple("💗", 0.15f, 0.90f),
        )
        hearts.forEach { (emoji, x, y) ->
            Text(
                text = emoji,
                fontSize = 20.sp,
                color = PinkPrimary.copy(alpha = 0.15f),
                modifier = Modifier
                    .fillMaxSize()
                    .wrapContentSize(Alignment.TopStart)
                    .padding(start = (x * 350).dp, top = (y * 750).dp)
            )
        }
    }
}