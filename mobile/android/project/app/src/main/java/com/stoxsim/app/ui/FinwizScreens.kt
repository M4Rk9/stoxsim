package com.stoxsim.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stoxsim.app.AppState
import com.stoxsim.app.data.*

@Composable internal fun FinwizHub(state: AppState, lesson: (String) -> Unit, newChat: (String, String, String) -> Unit,
    openChat: (String) -> Unit, deleteChat: (String) -> Unit, navigate: (String) -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var configure by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<String?>(null) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text("Finwiz AI", style = MaterialTheme.typography.headlineLarge); Muted("Your investing companion") }
        FilledTonalButton({ configure = true }, enabled = !state.working) { Text("+ New chat") }
    }
    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.fillMaxWidth().padding(18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("✦", fontSize = 34.sp, color = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) { Text("Learn. Ask. Practise.", style = MaterialTheme.typography.titleLarge); Text("Explore a module or ask Finwiz a question.", style = MaterialTheme.typography.bodyMedium) }
        }
    }
    TabRow(tab, containerColor = MaterialTheme.colorScheme.background) {
        Tab(tab == 0, { tab = 0 }, text = { Text("Learn") })
        Tab(tab == 1, { tab = 1 }, text = { Text("Chats") })
    }
    if (tab == 0) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column { Text("Learning modules", style = MaterialTheme.typography.titleLarge); Muted("${state.readModules.size} of ${learningModules.size} read · On this device") }
        }
        learningModules.chunked(2).forEachIndexed { row, pair ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEachIndexed { column, module ->
                    OutlinedCard(onClick = { lesson(module.id) }, modifier = Modifier.weight(1f).fillMaxHeight(),
                        shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(Modifier.padding(14.dp).heightIn(min = 166.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                                Text("${row * 2 + column + 1}".padStart(2, '0'), Modifier.padding(10.dp), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                            Text(module.title, style = MaterialTheme.typography.titleMedium)
                            Muted(module.summary)
                            Spacer(Modifier.weight(1f))
                            Text(if (module.id in state.readModules) "✓ Read · Review →" else "3 lessons · Start →", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
        OutlinedButton({ navigate("Learning path") }, Modifier.fillMaxWidth()) { Text("Your challenges & achievements →") }
    } else {
        Text("Your conversations", style = MaterialTheme.typography.titleLarge)
        Muted("Chats stay in memory for this sign-in session. Recent messages are included when they fit the question limit; restate older context when needed.")
        val chats = state.chats.filter { it.region == state.region }
        if (chats.isEmpty()) Panel(title = "Start a conversation") {
            Text("Ask about a concept, a stock or a lesson you just read.")
            Button({ configure = true }, enabled = !state.working) { Text("Ask Finwiz") }
        }
        chats.forEach { chat ->
            OutlinedCard(onClick = { openChat(chat.id) }, modifier = Modifier.fillMaxWidth(), colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Row(Modifier.padding(start = 14.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text(chat.title, style = MaterialTheme.typography.titleMedium, maxLines = 2); Muted("${regionLabel(chat.region)} · ${learningModules.find { it.id == chat.topic }?.title} · ${chat.messages.count { it.role == "user" }} questions") }
                    IconButton({ deleting = chat.id }, enabled = !state.working) { Icon(Icons.Default.Delete, "Delete conversation ${chat.title}") }
                }
            }
        }
    }
    if (configure) NewFinwizChat({ configure = false }) { topic, symbol, experience ->
        configure = false; newChat(topic, symbol, experience)
    }
    deleting?.let { id -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text("Delete this conversation?") },
        text = { Text("Its messages will be removed from this app session.") },
        confirmButton = { TextButton({ deleting = null; deleteChat(id) }, enabled = !state.working) { Text("Delete") } },
        dismissButton = { TextButton({ deleting = null }) { Text("Keep chat") } }) }
}

@Composable internal fun FinwizLesson(state: AppState, read: (String) -> Unit, chat: (String) -> Unit) {
    val module = learningModules.find { it.id == state.learningModule } ?: return
    val uri = LocalUriHandler.current
    Text(module.title, style = MaterialTheme.typography.headlineLarge)
    Text(module.summary, color = MaterialTheme.colorScheme.onSurfaceVariant)
    module.lessons.forEachIndexed { index, (title, body) -> Panel("LESSON ${index + 1} OF ${module.lessons.size}", title) { Text(body) } }
    Button({ read(module.id) }, enabled = module.id !in state.readModules, modifier = Modifier.fillMaxWidth()) { Text(if (module.id in state.readModules) "✓ Module read" else "Mark module as read") }
    OutlinedButton({ chat(module.id) }, enabled = !state.working, modifier = Modifier.fillMaxWidth()) { Text("Ask Finwiz about this module") }
    TextButton({ uri.openUri(module.reference) }) { Text("Investor education reference ↗") }
    Muted("Educational concepts and hypothetical examples. Read status is local and does not award server XP.")
}

@Composable internal fun FinwizChatScreen(state: AppState, send: (String) -> Unit, newChat: () -> Unit) {
    val chat = state.activeChat ?: return
    var draft by remember(chat.id) { mutableStateOf("") }
    val list = rememberLazyListState()
    val module = learningModules.find { it.id == chat.topic }
    LaunchedEffect(chat.id, chat.messages.size, state.working) {
        if (chat.messages.isNotEmpty()) list.animateScrollToItem(chat.messages.lastIndex)
    }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text("Finwiz", style = MaterialTheme.typography.titleLarge); Muted("${module?.title} · ${regionLabel(chat.region)}${if (chat.symbol.isNotBlank()) " · ${chat.symbol}" else ""}") }
            TextButton({ configure = true }, enabled = !state.working) { Text("New chat") }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list, verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
            if (chat.messages.isEmpty()) item {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("What would you like to learn?", style = MaterialTheme.typography.headlineMedium)
                    Text("Ask a question or try a starter below.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    listOfNotNull(module?.prompt, "What is the difference between stocks and ETFs?", "How do I practise safely in StoxSim?").distinct().forEach {
                        OutlinedButton({ draft = it }, modifier = Modifier.fillMaxWidth(), enabled = !state.working) { Text(it) }
                    }
                }
            }
            items(chat.messages, key = { it.id }) { message ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (message.role == "user") Arrangement.End else Arrangement.Start) {
                    Surface(Modifier.widthIn(max = 520.dp).fillMaxWidth(if (message.role == "user") 0.88f else 1f),
                        color = when (message.role) { "user" -> MaterialTheme.colorScheme.primaryContainer; "error" -> MaterialTheme.colorScheme.errorContainer; else -> MaterialTheme.colorScheme.surface },
                        shape = RoundedCornerShape(16.dp), border = if (message.role == "assistant") BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Kicker(when (message.role) { "user" -> "You"; "error" -> "Could not get an answer"; else -> "Finwiz" })
                            SelectionContainer { Text(message.text) }
                            if (message.role == "assistant") {
                                if (message.provider.isNotBlank()) Muted("${message.provider} · ${time(message.generatedAt)}")
                                if (message.disclaimer.isNotBlank()) Muted(message.disclaimer)
                                message.suggestions.take(3).forEach { suggestion -> TextButton({ draft = suggestion.take(2000) }, enabled = !state.working) { Text(suggestion) } }
                            }
                        }
                    }
                }
            }
            if (state.working) item { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Muted("Finwiz is thinking…") } }
        }
        if (chat.messages.size >= 40) Muted("This conversation is full. Start a new chat to continue.")
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(draft, { draft = it.take(2000) }, label = { Text("Ask Finwiz") }, placeholder = { Text("Type your question…") },
                modifier = Modifier.weight(1f), minLines = 1, maxLines = 4, shape = RoundedCornerShape(18.dp), enabled = !state.working,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default))
            FilledIconButton({ val question = draft.trim(); draft = ""; send(question) }, enabled = draft.isNotBlank() && !state.working && chat.messages.size < 40,
                modifier = Modifier.padding(bottom = 6.dp).size(48.dp)) { Icon(Icons.Default.Send, "Send question") }
        }
        Muted("Educational AI · Verify answers · Recent context is limited")
    }
}

@Composable private fun NewFinwizChat(dismiss: () -> Unit, create: (String, String, String) -> Unit) {
    var topic by remember { mutableStateOf("LEARN") }
    var symbol by remember { mutableStateOf("") }
    var experience by remember { mutableStateOf("BEGINNER") }
    AlertDialog(onDismissRequest = dismiss, title = { Text("Start a Finwiz chat") }, text = {
        Column(Modifier.heightIn(max = 340.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Choose a topic")
            ChoiceRow(learningModules.map { it.id to it.title }, topic) { topic = it }
            ChoiceRow(listOf("BEGINNER" to "Beginner", "INTERMEDIATE" to "Intermediate"), experience) { experience = it }
            OutlinedTextField(symbol, { symbol = it.take(32) }, label = { Text("Stock symbol (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Muted("Uses your selected market and existing Finwiz access.")
        }
    }, confirmButton = { TextButton({ create(topic, symbol, experience) }) { Text("Start chat") } },
        dismissButton = { TextButton(dismiss) { Text("Cancel") } })
}
