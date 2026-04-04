package com.fynnix.app.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun HomeScreen() {
    ScreenContent(title = "Home Screen")
}

@Composable
fun TransactionsScreen() {
    ScreenContent(title = "Transactions Screen")
}

@Composable
fun InsightsScreen() {
    ScreenContent(title = "Insights Screen")
}

@Composable
fun SubscriptionsScreen() {
    ScreenContent(title = "Subscriptions Screen")
}

@Composable
fun SettingsScreen() {
    ScreenContent(title = "Settings Screen")
}

@Composable
private fun ScreenContent(title: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(text = title, style = MaterialTheme.typography.headlineMedium)
    }
}