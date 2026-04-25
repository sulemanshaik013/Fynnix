package com.fynnix.app.features.settings.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private data class HelpItem(val q: String, val a: String)

private val FAQ = listOf(
    HelpItem(
        "How does Fynnix detect transactions?",
        "Fynnix reads bank/wallet SMS on this device only, runs them through a deterministic pipeline (filters → parser → confidence score), and stores accepted transactions locally. Nothing leaves your phone."
    ),
    HelpItem(
        "Why didn't a transaction show up?",
        "Either the SMS sender isn't recognised as a bank (see Privacy Dashboard), the message lacked a clear amount + verb, or it scored below the confidence threshold. Try Hot Reload from the Home screen after enabling more banks."
    ),
    HelpItem(
        "Why are some transactions duplicated?",
        "If you re-imported a CSV that overlaps your SMS history you may see duplicates. Use Settings → Data Management → Import only with non-overlapping data."
    ),
    HelpItem(
        "Can I edit a parsed transaction?",
        "Tap any transaction to open the detail sheet. Editing categories and merchant names is on the roadmap — for now you can manually add corrections."
    ),
    HelpItem(
        "How do subscriptions get detected?",
        "Three or more debits to the same merchant, ~30 day spacing, similar amount (±15%). Newer recurring payments take a few cycles to surface."
    ),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Help", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            FAQ.forEach { item ->
                FaqCard(item)
            }
        }
    }
}

@Composable
private fun FaqCard(item: HelpItem) {
    var expanded by remember { mutableStateOf(false) }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.clickable { expanded = !expanded }.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.q,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White,
                )
                Icon(Icons.Default.ExpandMore, contentDescription = null, tint = Color.Gray)
            }
            if (expanded) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(item.a, style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
            }
        }
    }
}
