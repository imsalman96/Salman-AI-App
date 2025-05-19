package com.example.myaiapplication

import android.Manifest
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.SimpleDateFormat
import java.util.*

data class Message(
    val content: String,
    val isFromUser: Boolean,
    val isTyping: Boolean = false,
    val timestamp: Long = System.currentTimeMillis(),
    val id: Long = System.currentTimeMillis(),
    val isSystemMessage: Boolean = false
)

@RequiresPermission(Manifest.permission.VIBRATE)
fun Context.vibrateDevice() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        val vibrator = vibratorManager.defaultVibrator
        val vibrationEffect = VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE)
        vibrator.vibrate(vibrationEffect)
    } else {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(20, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(20)
        }
    }
}

@RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
@Composable
fun BakingScreen(
    bakingViewModel: BakingViewModel = viewModel()
) {
    val uiState by bakingViewModel.uiState.collectAsState()
    var userInput by rememberSaveable { mutableStateOf("") }
    val messages = remember { mutableStateListOf<Message>() }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    // Add initial system message if messages is empty
    LaunchedEffect(Unit) {
        if (messages.isEmpty()) {
            messages.add(
                Message(
                    content = "Hello! I'm Salman's AI assistant. How can I help you today?",
                    isFromUser = false,
                    isSystemMessage = true
                )
            )
        }
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // Messages list
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 80.dp),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(
                items = messages,
                key = { it.id }
            ) { message ->
                AnimatedEntry {
                    MessageBubble(
                        message = message,
                        onCopy = @RequiresPermission(Manifest.permission.VIBRATE) {
                            clipboardManager.setText(AnnotatedString(message.content))
                            context.vibrateDevice()
                            coroutineScope.launch {
                                // Show snackbar or toast here
                            }
                        }
                    )
                }
            }
            
            item {
                if (uiState is UiState.Loading) {
                    TypingIndicator()
                }
            }
        }

        // Input area with shadow
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            shadowElevation = 8.dp,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column {
                Divider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextField(
                        value = userInput,
                        onValueChange = { userInput = it },
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                            .clip(RoundedCornerShape(24.dp)),
                        placeholder = { Text("Message AI assistant...") },
                        colors = TextFieldDefaults.colors(
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        maxLines = 5
                    )

                    FloatingActionButton(
                        onClick = {
                            if (userInput.isNotEmpty()) {
                                messages.add(Message(userInput, true))
                                bakingViewModel.sendPrompt(null, userInput)
                                userInput = ""
                                coroutineScope.launch {
                                    listState.animateScrollToItem(messages.size - 1)
                                }
                            }
                        },
                        modifier = Modifier.size(48.dp),
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ) {
                        Icon(Icons.Default.Send, "Send")
                    }
                }
            }
        }
    }

    // Handle response from ViewModel with vibration
    LaunchedEffect(uiState) {
        when (uiState) {
            is UiState.Success -> {
                val response = (uiState as UiState.Success).outputText
                messages.add(Message(response, false, true))
                delay(300)
                messages.removeLast()
                messages.add(Message(response, false))
                context.vibrateDevice()
                listState.animateScrollToItem(messages.size - 1)
            }
            is UiState.Error -> {
                messages.add(Message((uiState as UiState.Error).errorMessage, false))
                context.vibrateDevice()
                listState.animateScrollToItem(messages.size - 1)
            }
            else -> {}
        }
    }
}

@Composable
fun MessageBubble(
    message: Message,
    onCopy: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalAlignment = if (message.isFromUser) Alignment.End else Alignment.Start
    ) {
        // Timestamp
        Text(
            text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp)),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )

        Surface(
            shape = RoundedCornerShape(
                topStart = if (message.isFromUser) 12.dp else 4.dp,
                topEnd = if (message.isFromUser) 4.dp else 12.dp,
                bottomStart = 12.dp,
                bottomEnd = 12.dp
            ),
            color = when {
                message.isSystemMessage -> MaterialTheme.colorScheme.surfaceVariant
                message.isFromUser -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.secondaryContainer
            },
            modifier = Modifier.widthIn(max = 340.dp)
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = message.content,
                    color = when {
                        message.isSystemMessage -> MaterialTheme.colorScheme.onSurfaceVariant
                        message.isFromUser -> MaterialTheme.colorScheme.onPrimary
                        else -> MaterialTheme.colorScheme.onSecondaryContainer
                    },
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge
                )
                
                if (!message.isFromUser && !message.isSystemMessage) {
                    IconButton(
                        onClick = onCopy,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Check,
                            contentDescription = "Copy message",
                            tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AnimatedEntry(content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = true,
        enter = fadeIn(
            animationSpec = tween(200)
        ) + expandVertically(
            expandFrom = Alignment.Top,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        )
    ) {
        content()
    }
}

@Composable
fun TypingIndicator() {
    val dots = remember { Animatable(0f) }
    
    LaunchedEffect(dots) {
        dots.animateTo(
            targetValue = 3f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000),
                repeatMode = RepeatMode.Reverse
            )
        )
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.widthIn(max = 100.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            repeat(3) { index ->
                val alpha = if (index + 1 <= dots.value) 1f else 0.3f
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = alpha))
                )
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
@Preview(showSystemUi = true)
@Composable
fun BakingScreenPreview() {
    BakingScreen()
}