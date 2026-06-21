package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.ChatMessage
import com.example.ui.theme.*
import com.example.ui.viewmodel.ChatViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberLazyListState()

    // ViewModel collected states
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val currentSessionId by viewModel.currentSessionId.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val isGenerating by viewModel.isGenerating.collectAsStateWithLifecycle()

    val serverUrl by viewModel.serverUrl.collectAsStateWithLifecycle()
    val systemPrompt by viewModel.systemPrompt.collectAsStateWithLifecycle()
    val temperature by viewModel.temperature.collectAsStateWithLifecycle()
    val maxTokens by viewModel.maxTokens.collectAsStateWithLifecycle()
    val useFallbackGemini by viewModel.useFallbackGemini.collectAsStateWithLifecycle()
    val geminiApiKey by viewModel.geminiApiKey.collectAsStateWithLifecycle()

    val serverHealthStatus by viewModel.serverHealthStatus.collectAsStateWithLifecycle()
    val isCheckingHealth by viewModel.isCheckingHealth.collectAsStateWithLifecycle()

    // Compose local UI state
    var inputText by remember { mutableStateOf("") }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showSessionsDrawer by remember { mutableStateOf(false) }

    // Floating scroll to bottom indicator
    val showScrollToBottomButton by remember {
        derivedStateOf {
            scrollState.firstVisibleItemIndex > 1
        }
    }

    // Auto-scroll to bottom of the chat list on new message
    LaunchedEffect(messages.size, isGenerating) {
        if (messages.isNotEmpty()) {
            scrollState.animateScrollToItem(messages.size - 1)
        }
    }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val currentSession = sessions.find { it.id == currentSessionId }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = true,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = CyberSurface,
                modifier = Modifier.width(310.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Riwayat Obrolan",
                        color = NeonCyan,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    Button(
                        onClick = {
                            viewModel.createSession()
                            coroutineScope.launch { drawerState.close() }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BrightCyan,
                            contentColor = Color.Black
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                            .testTag("new_chat_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "New Chat")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "+ Mulai Diskusi Baru", fontWeight = FontWeight.SemiBold)
                    }

                    HorizontalDivider(color = CyberGray.copy(alpha = 0.2f), modifier = Modifier.padding(bottom = 12.dp))

                    if (sessions.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Belum ada riwayat diskusi.",
                                color = CyberGray,
                                fontSize = 14.sp
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f)
                        ) {
                            items(sessions, key = { it.id }) { s ->
                                val isSelected = s.id == currentSessionId
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) BrightCyan.copy(alpha = 0.15f) else Color.Transparent)
                                        .border(
                                            width = 1.dp,
                                            color = if (isSelected) BrightCyan.copy(alpha = 0.5f) else Color.Transparent,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .clickable {
                                            viewModel.selectSession(s.id)
                                            coroutineScope.launch { drawerState.close() }
                                        }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Create,
                                        contentDescription = "Session",
                                        tint = if (isSelected) NeonCyan else CyberGray,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = s.title,
                                        color = if (isSelected) NeonCyan else CyberText,
                                        fontSize = 14.sp,
                                        maxLines = 1,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = { viewModel.deleteSession(s.id) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete Thread",
                                            tint = ErrorRed.copy(alpha = 0.7f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(CyberCard)
                            .clickable { showSettingsSheet = true }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.Settings, contentDescription = "Settings", tint = NeonCyan)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(text = "Pengaturan Server", color = CyberText, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text(text = if (useFallbackGemini) "Fallback: Gemini Active" else "Local Llama backend", color = CyberGray, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    ) {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            containerColor = CyberDark,
            topBar = {
                Column {
                    TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = CyberDark,
                            titleContentColor = CyberText
                        ),
                        navigationIcon = {
                            IconButton(onClick = { coroutineScope.launch { drawerState.open() } }) {
                                Icon(imageVector = Icons.Default.Menu, contentDescription = "Menu", tint = NeonCyan)
                            }
                        },
                        title = {
                            Column {
                                Text(
                                    text = currentSession?.title ?: "Local AI Chat",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                // Connected status indicator
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.clickable { viewModel.checkServerHealth() }
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(
                                                when {
                                                    useFallbackGemini -> SuccessGreen
                                                    serverHealthStatus == true -> SuccessGreen
                                                    serverHealthStatus == false -> ErrorRed
                                                    else -> CyberGray
                                                }
                                            )
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = when {
                                            useFallbackGemini -> "Gemini API Fallback"
                                            serverHealthStatus == true -> "llama.cpp Online"
                                            serverHealthStatus == false -> "llama.cpp Terputus"
                                            else -> "Mengecek koneksi..."
                                        },
                                        fontSize = 11.sp,
                                        color = if (serverHealthStatus == false && !useFallbackGemini) ErrorRed else CyberGray
                                    )
                                    if (isCheckingHealth) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        CircularProgressIndicator(
                                            color = NeonCyan,
                                            modifier = Modifier.size(10.dp),
                                            strokeWidth = 1.dp
                                        )
                                    } else {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = "Refresh",
                                            tint = CyberGray,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }
                            }
                        },
                        actions = {
                            if (messages.isNotEmpty()) {
                                IconButton(onClick = { viewModel.clearActiveSessionMessages() }) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Hapus Obrolan",
                                        tint = ErrorRed
                                    )
                                }
                            }
                            IconButton(onClick = { showSettingsSheet = true }) {
                                Icon(imageVector = Icons.Default.Settings, contentDescription = "Pengaturan", tint = NeonCyan)
                            }
                        }
                    )
                    HorizontalDivider(color = SophisticatedBorder, thickness = 1.dp)
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (messages.isEmpty()) {
                        // Empty Chat Screen Welcome banner and helper tips
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                // Dynamic image generated loaded
                                Card(
                                    modifier = Modifier
                                        .size(180.dp)
                                        .padding(8.dp)
                                        .border(2.dp, NeonCyan, RoundedCornerShape(16.dp)),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = CyberCard)
                                ) {
                                    Image(
                                        painter = painterResource(id = R.drawable.img_welcome_llama),
                                        contentDescription = "Futuristic Cybernetic Llama",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                Text(
                                    text = "Chat Server llama.cpp",
                                    color = NeonCyan,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = "Kirim pesan untuk berkomunikasi dengan model AI lokal Anda. Sistem akan menyimpan riwayat diskusi Anda secara lokal di memori HP.",
                                    color = CyberGray,
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )

                                Spacer(modifier = Modifier.height(24.dp))

                                // Suggestion Prompts
                                Text(
                                    text = "Rekomendasi Prompt:",
                                    color = CyberText,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.align(Alignment.Start).padding(start = 12.dp, bottom = 8.dp)
                                )

                                val promptSuggestions = listOf(
                                    "Bantu buatkan snippet function Kotlin pencari bilangan prima.",
                                    "Tulis puisi pendek tentang seekor llama di masa depan cyberpunk.",
                                    "Terjemahkan ke Bahasa Inggris: 'Saya ingin melatih model AI di laptop saya.'",
                                    "Berikan saya 3 ide nama unik untuk kedai kopi bernuansa sci-fi."
                                )

                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    promptSuggestions.forEach { prompt ->
                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    inputText = prompt
                                                },
                                            shape = RoundedCornerShape(8.dp),
                                            colors = CardDefaults.cardColors(containerColor = CyberSurface),
                                            border = CardDefaults.outlinedCardBorder().copy(
                                                brush = SolidColor(BrightCyan.copy(alpha = 0.3f))
                                            )
                                        ) {
                                            Text(
                                                text = prompt,
                                                color = CyberText,
                                                fontSize = 12.sp,
                                                modifier = Modifier.padding(12.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // Messages display column
                        LazyColumn(
                            state = scrollState,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp),
                            contentPadding = PaddingValues(top = 12.dp, bottom = 12.dp)
                        ) {
                            items(messages) { message ->
                                MessageBubble(
                                    message = message,
                                    onCopy = {
                                        clipboardManager.setText(AnnotatedString(message.content))
                                        Toast.makeText(context, "Pesan disalin ke papan klip!", Toast.LENGTH_SHORT).show()
                                    }
                                )
                            }

                            if (isGenerating) {
                                item {
                                    LoadingAssistantBubble()
                                }
                            }
                        }
                    }

                    // Bottom chat inputs
                    Surface(
                        color = CyberDark,
                        tonalElevation = 0.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                                .navigationBarsPadding(),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Bottom
                            ) {
                                val keyboardController = LocalSoftwareKeyboardController.current

                                OutlinedTextField(
                                    value = inputText,
                                    onValueChange = { inputText = it },
                                    placeholder = { Text("Ask Llama locally...", color = SophisticatedMuted) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("chat_input_field"),
                                    shape = RoundedCornerShape(24.dp),
                                    isError = false,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = SophisticatedBorder,
                                        unfocusedBorderColor = SophisticatedBorder,
                                        focusedTextColor = CyberText,
                                        unfocusedTextColor = CyberText,
                                        cursorColor = SophisticatedAccent,
                                        focusedContainerColor = SophisticatedSurface,
                                        unfocusedContainerColor = SophisticatedSurface
                                    ),
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Text,
                                        imeAction = ImeAction.Send
                                    ),
                                    keyboardActions = KeyboardActions(
                                        onSend = {
                                            if (inputText.isNotBlank() && !isGenerating) {
                                                if (currentSessionId == null) {
                                                    viewModel.createSession()
                                                }
                                                viewModel.sendMessage(inputText)
                                                inputText = ""
                                                keyboardController?.hide()
                                            }
                                        }
                                    ),
                                    maxLines = 4
                                )

                                Spacer(modifier = Modifier.width(10.dp))

                                ExtendedIconButton(
                                    onClick = {
                                        if (inputText.isNotBlank() && !isGenerating) {
                                            if (currentSessionId == null) {
                                                viewModel.createSession()
                                            }
                                            viewModel.sendMessage(inputText)
                                            inputText = ""
                                            keyboardController?.hide()
                                        }
                                    },
                                    enabled = inputText.isNotBlank() && !isGenerating,
                                    modifier = Modifier.testTag("send_button")
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "llama.cpp server: http://127.0.0.1:8080",
                                color = SophisticatedMuted,
                                fontSize = 10.sp,
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            )
                        }
                    }
                }

            }
        }
    }

    // Modal Settings Configuration Bottom Sheet
    if (showSettingsSheet) {
        var tempUrl by remember { mutableStateOf(serverUrl) }
        var tempPrompt by remember { mutableStateOf(systemPrompt) }
        var tempTemp by remember { mutableStateOf(temperature) }
        var tempTokens by remember { mutableStateOf(maxTokens) }
        var tempFallback by remember { mutableStateOf(useFallbackGemini) }
        var tempApiKey by remember { mutableStateOf(geminiApiKey) }

        AlertDialog(
            onDismissRequest = { showSettingsSheet = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.saveSettings(
                            url = tempUrl,
                            prompt = tempPrompt,
                            temp = tempTemp,
                            tokens = tempTokens,
                            fallback = tempFallback,
                            apiKey = tempApiKey
                        )
                        showSettingsSheet = false
                        Toast.makeText(context, "Pengaturan disimpan!", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Simpan", color = NeonCyan, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSettingsSheet = false }) {
                    Text("Batal", color = CyberGray)
                }
            },
            containerColor = CyberSurface,
            titleContentColor = NeonCyan,
            textContentColor = CyberText,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Settings, contentDescription = "Config")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Konfigurasi Mesin AI", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Surface(color = Color.Transparent) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(androidx.compose.foundation.rememberScrollState())
                    ) {
                        // Use Fallback Gemini AI Switch
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Gunakan Fallback Gemini AI", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Gunakan model cloud Gemini jika server lokal llama.cpp Anda sedang tidak aktif.", color = CyberGray, fontSize = 11.sp)
                            }
                            Switch(
                                checked = tempFallback,
                                onCheckedChange = { tempFallback = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.Black,
                                    checkedTrackColor = NeonCyan,
                                    uncheckedThumbColor = CyberGray,
                                    uncheckedTrackColor = CyberDark
                                )
                            )
                        }

                        HorizontalDivider(color = CyberGray.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 12.dp))

                        if (tempFallback) {
                            // Gemini Config Input
                            Text(text = "API Key Gemini (Opsional jika sudah diisi di panel Rahasia)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = NeonCyan)
                            OutlinedTextField(
                                value = tempApiKey,
                                onValueChange = { tempApiKey = it },
                                placeholder = { Text("AIzaSy...", color = CyberGray.copy(alpha = 0.5f)) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = NeonCyan,
                                    unfocusedBorderColor = CyberGray.copy(alpha = 0.4f),
                                    focusedTextColor = CyberText,
                                    unfocusedTextColor = CyberText
                                ),
                                singleLine = true
                            )
                        } else {
                            // Llama CPP Service Config Input
                            Text(text = "Alamat IP Server llama.cpp", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = NeonCyan)
                            OutlinedTextField(
                                value = tempUrl,
                                onValueChange = { tempUrl = it },
                                placeholder = { Text("http://10.0.2.2:8080", color = CyberGray) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = NeonCyan,
                                    unfocusedBorderColor = CyberGray.copy(alpha = 0.4f),
                                    focusedTextColor = CyberText,
                                    unfocusedTextColor = CyberText
                                ),
                                singleLine = true
                            )
                            Text(
                                text = "Gunakan http://10.0.2.2:8080 untuk localhost PC host Anda jika menggunakan emulator internal.",
                                color = CyberGray,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }

                        // Common Configuration parameters
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = "Instruksi Sistem (System Prompt)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = NeonCyan)
                        OutlinedTextField(
                            value = tempPrompt,
                            onValueChange = { tempPrompt = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(90.dp)
                                .padding(vertical = 6.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NeonCyan,
                                unfocusedBorderColor = CyberGray.copy(alpha = 0.4f),
                                focusedTextColor = CyberText,
                                unfocusedTextColor = CyberText
                            ),
                            maxLines = 4
                        )

                        Spacer(modifier = Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text(text = "Temperatur ($tempTemp)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text(text = "Kreativitas", color = CyberGray, fontSize = 11.sp)
                        }
                        Slider(
                            value = tempTemp,
                            onValueChange = { tempTemp = (Math.round(it * 10f) / 10f) },
                            valueRange = 0.1f..1.5f,
                            colors = SliderDefaults.colors(
                                thumbColor = NeonCyan,
                                activeTrackColor = NeonCyan,
                                inactiveTrackColor = CyberDark
                            )
                        )

                        Spacer(modifier = Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text(text = "Maksimum Token ($tempTokens)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text(text = "Panjang Response", color = CyberGray, fontSize = 11.sp)
                        }
                        Slider(
                            value = tempTokens.toFloat(),
                            onValueChange = { tempTokens = it.toInt() },
                            valueRange = 128f..2048f,
                            steps = 15,
                            colors = SliderDefaults.colors(
                                thumbColor = NeonCyan,
                                activeTrackColor = NeonCyan,
                                inactiveTrackColor = CyberDark
                            )
                        )
                    }
                }
            }
        )
    }
}

@Composable
fun MessageBubble(
    message: ChatMessage,
    onCopy: () -> Unit
) {
    val isUser = message.role == "user"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            // Llama or Gemini Avatar icon
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(BrightCyan)
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (message.isError) Icons.Default.Info else Icons.Default.Face,
                    contentDescription = "Bot Avatar",
                    tint = Color.Black,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        Column(
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
            modifier = Modifier.weight(1f, fill = false)
        ) {
            // Sender name block
            Text(
                text = if (isUser) "Anda" else if (message.isError) "System Error" else "Asisten AI",
                color = if (isUser) NeonCyan else if (message.isError) ErrorRed else CyberGray,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 2.dp, start = 4.dp, end = 4.dp)
            )

            // Dynamic Message Bubble Card container
            Surface(
                color = if (isUser) SophisticatedAccent else if (message.isError) ErrorRed.copy(alpha = 0.15f) else SophisticatedSurface,
                modifier = Modifier
                    .clip(
                        RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 16.dp,
                            bottomStart = if (isUser) 16.dp else 2.dp,
                            bottomEnd = if (isUser) 2.dp else 16.dp
                        )
                    )
                    .clickable { onCopy() }
                    .border(
                        width = 1.dp,
                        color = if (isUser) Color.Transparent else if (message.isError) ErrorRed.copy(alpha = 0.4f) else SophisticatedBorder,
                        shape = RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 16.dp,
                            bottomStart = if (isUser) 16.dp else 2.dp,
                            bottomEnd = if (isUser) 2.dp else 16.dp
                        )
                    )
                    .padding(12.dp)
            ) {
                Column {
                    Text(
                        text = message.content,
                        color = if (message.isError) ErrorRed else if (isUser) SophisticatedUserText else SophisticatedText,
                        fontSize = 15.sp,
                        fontFamily = if (message.content.startsWith("fun ") || message.content.contains("```")) FontFamily.Monospace else FontFamily.Default,
                        lineHeight = 22.sp
                    )
                    // Copy button inside the bubble helper
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Copy message",
                            tint = CyberGray.copy(alpha = 0.4f),
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
        }

        if (isUser) {
            Spacer(modifier = Modifier.width(8.dp))
            // User Avatar Icon
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(CyberSurface)
                    .border(1.dp, NeonCyan, CircleShape)
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "User Avatar",
                    tint = NeonCyan,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun LoadingAssistantBubble() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(BrightCyan)
                .padding(4.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                color = Color.Black,
                strokeWidth = 2.dp,
                modifier = Modifier.size(16.dp)
            )
        }
        Spacer(modifier = Modifier.width(8.dp))

        Column {
            Text(
                text = "Asisten AI",
                color = CyberGray,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 2.dp, start = 4.dp)
            )
            Surface(
                color = CyberCard,
                modifier = Modifier
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 2.dp, bottomEnd = 16.dp))
                    .padding(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Model sedang merumuskan jawaban...",
                        color = CyberGray,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

@Composable
fun ExtendedIconButton(
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (enabled) SophisticatedAccent else SophisticatedSurface)
            .size(56.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Send,
            contentDescription = "Kirim",
            tint = if (enabled) SophisticatedUserText else CyberGray.copy(alpha = 0.4f),
            modifier = Modifier.size(24.dp)
        )
    }
}
