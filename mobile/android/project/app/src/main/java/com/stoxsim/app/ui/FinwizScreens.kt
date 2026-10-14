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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.stoxsim.app.AppState
import com.stoxsim.app.data.*

@Composable internal fun FinwizHub(state: AppState, lesson: (String) -> Unit, newChat: (String, String, String) -> Unit,
    openChat: (String) -> Unit, deleteChat: (String) -> Unit, navigate: (String) -> Unit,
    startChat: (String, String) -> Unit = { _, _ -> }) {
    var tab by rememberSaveable { mutableStateOf("Modules") }
    var selected by rememberSaveable { mutableStateOf("LEARN") }
    var draft by remember { mutableStateOf("") }
    var configure by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<String?>(null) }
    val module = learningModules.first { it.id == selected }
    val focus = LocalFocusManager.current
    val inlineChat = state.activeChat?.takeIf { it.region == state.region && it.topic == selected && it.symbol.isBlank() }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(androidx.compose.ui.res.painterResource(com.stoxsim.app.R.drawable.ic_nav_finwiz), null, Modifier.size(30.dp))
        Column(Modifier.weight(1f)) { Text("Finwiz AI", style = MaterialTheme.typography.headlineMedium); Muted("Knowledge is your edge.") }
        TextButton({ configure = true }, enabled = !state.working) { Text("+ New chat") }
    }
    ChoiceRow(listOf("Modules" to "Modules", "Chats" to "Chats"), tab) { tab = it }
    if (tab == "Modules") {
        Panel {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Kicker("THE KNOWLEDGE REACTOR")
                Muted("${state.readModules.size}/${learningModules.size} read")
            }
            FinwizReactor(selected, state.readModules, { selected = it }, !state.working)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text(module.summary, style = MaterialTheme.typography.bodyMedium)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Muted("${module.lessons.size} lessons${if (module.id in state.readModules) " · Read" else " · Start here"}")
                Spacer(Modifier.weight(1f))
                FilledTonalButton({ lesson(module.id) }, enabled = !state.working,
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest, contentColor = MaterialTheme.colorScheme.onSurface)) { Text("Read module →") }
            }
        }
        inlineChat?.let { InlineFinwizConversation(it, state.working, { draft = it.take(2000) }) { openChat(inlineChat.id) } }
        Panel {
            Text("Ask. Understand. Practise.", style = MaterialTheme.typography.titleLarge)
            Muted("Ask Finwiz about ${module.title.lowercase()}.")
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(draft, { draft = it.take(2000) }, placeholder = { Text("What would you like to learn?") },
                    modifier = Modifier.weight(1f).semantics { contentDescription = "Finwiz question" }, minLines = 1, maxLines = 4,
                    enabled = !state.working, shape = RoundedCornerShape(18.dp), textStyle = MaterialTheme.typography.bodyMedium,
                    colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant))
                FilledIconButton({ val question = draft.trim(); draft = ""; focus.clearFocus(); startChat(selected, question) },
                    enabled = draft.isNotBlank() && !state.working && (inlineChat?.messages?.size ?: 0) < 40, modifier = Modifier.size(48.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.onSurface, contentColor = MaterialTheme.colorScheme.surface)) {
                    Icon(Icons.Default.Send, "Send Finwiz question")
                }
            }
            Muted("Educational AI · Verify answers")
            if ((inlineChat?.messages?.size ?: 0) >= 40) Muted("This conversation is full. Use New chat to continue.")
        }
        TextButton({ navigate("Learning path") }, Modifier.fillMaxWidth()) { Text("Your challenges & achievements →") }
    } else {
        Text("Your conversations", style = MaterialTheme.typography.titleLarge)
        Muted("Conversations stay in memory for this sign-in session.")
        val chats = state.chats.filter { it.region == state.region }
        if (chats.isEmpty()) Panel(title = "Start a conversation") {
            Text("Ask about a concept, a stock or a lesson you just read.")
            Button({ configure = true }, enabled = !state.working) { Text("Ask Finwiz") }
        }
        chats.forEach { chat ->
            OutlinedCard(onClick = { openChat(chat.id) }, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Row(Modifier.padding(start = 18.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
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

@Composable private fun InlineFinwizConversation(chat: FinwizConversation, working: Boolean, suggestion: (String) -> Unit, open: () -> Unit) {
    val response = remember(chat.id) { BringIntoViewRequester() }
    LaunchedEffect(chat.id, chat.messages.size) { if (chat.messages.isNotEmpty()) response.bringIntoView() }
    Column(Modifier.fillMaxWidth().bringIntoViewRequester(response), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        chat.messages.takeLast(4).forEach { message ->
            Surface(Modifier.fillMaxWidth(), color = when (message.role) {
                "user" -> MaterialTheme.colorScheme.surfaceContainerHighest
                "error" -> MaterialTheme.colorScheme.errorContainer
                else -> MaterialTheme.colorScheme.surface
            }, shape = MaterialTheme.shapes.medium, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Kicker(if (message.role == "user") "You" else if (message.role == "error") "Could not get an answer" else "Finwiz")
                    SelectionContainer { Text(message.text) }
                    if (message.role == "assistant") {
                        if (message.disclaimer.isNotBlank()) Muted(message.disclaimer)
                        message.suggestions.take(2).forEach { TextButton({ suggestion(it) }, enabled = !working) { Text(it) } }
                    }
                }
            }
        }
        if (working) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Muted("Finwiz is thinking…")
        }
        TextButton(open, enabled = !working) { Text("View full conversation →") }
    }
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
            Icon(androidx.compose.ui.res.painterResource(com.stoxsim.app.R.drawable.ic_nav_finwiz), null, Modifier.padding(end = 10.dp).size(26.dp))
            Column(Modifier.weight(1f)) { Text("Finwiz", style = MaterialTheme.typography.titleLarge); Muted("${module?.title} · ${regionLabel(chat.region)}${if (chat.symbol.isNotBlank()) " · ${chat.symbol}" else ""}") }
            TextButton(newChat, enabled = !state.working) { Text("New chat") }
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
                        color = when (message.role) { "user" -> MaterialTheme.colorScheme.surfaceContainerHighest; "error" -> MaterialTheme.colorScheme.errorContainer; else -> MaterialTheme.colorScheme.surface },
                        shape = RoundedCornerShape(22.dp), border = if (message.role == "assistant") BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null) {
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
