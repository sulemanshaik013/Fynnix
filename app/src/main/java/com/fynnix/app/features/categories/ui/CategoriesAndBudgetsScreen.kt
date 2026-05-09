package com.fynnix.app.features.categories.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fynnix.app.core.ui.theme.Primary
import com.fynnix.app.features.budgets.ui.BudgetViewModel
import com.fynnix.app.features.budgets.ui.CategoryBudgetEditUiModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesAndBudgetsScreen(onBack: () -> Unit) {
    val viewModel: BudgetViewModel = hiltViewModel()
    val categories by viewModel.categoriesWithBudget.collectAsState()
    var showAdd by remember { mutableStateOf(false) }
    var newCategoryName by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Categories & Budgets", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            Text(
                text = "MONTHLY LIMITS · LIVE SPEND",
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 16.dp),
                letterSpacing = 1.sp
            )

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(categories, key = { it.id }) { category ->
                    var pendingDelete by remember { mutableStateOf(false) }
                    CategoryBudgetItem(
                        category = category,
                        onSave = { newBudget -> viewModel.updateBudget(category.id, newBudget) },
                        onDelete = { pendingDelete = true },
                    )
                    if (pendingDelete) {
                        AlertDialog(
                            onDismissRequest = { pendingDelete = false },
                            title = { Text("Remove ${category.name}?") },
                            text = { Text("Its budget will be removed too. Existing transactions keep their category.") },
                            confirmButton = {
                                TextButton(onClick = {
                                    viewModel.removeCategory(category.id)
                                    pendingDelete = false
                                }) { Text("Remove", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) }
                            },
                            dismissButton = {
                                TextButton(onClick = { pendingDelete = false }) { Text("Cancel") }
                            },
                        )
                    }
                }

                item {
                    OutlinedButton(
                        onClick = { showAdd = true },
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = Primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Add new category", color = Color.White)
                    }
                }
            }
        }
    }

    if (showAdd) {
        AlertDialog(
            onDismissRequest = { showAdd = false; newCategoryName = "" },
            title = { Text("New category") },
            text = {
                OutlinedTextField(
                    value = newCategoryName,
                    onValueChange = { newCategoryName = it },
                    singleLine = true,
                    label = { Text("Name") },
                )
            },
            confirmButton = {
                TextButton(
                    enabled = newCategoryName.isNotBlank(),
                    onClick = {
                        viewModel.addCategory(newCategoryName)
                        newCategoryName = ""
                        showAdd = false
                    },
                ) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { showAdd = false; newCategoryName = "" }) { Text("Cancel") }
            },
        )
    }
}

@Composable
fun CategoryBudgetItem(
    category: CategoryBudgetEditUiModel,
    onSave: (Double) -> Unit,
    onDelete: () -> Unit,
) {
    var budgetText by remember(category.id) { mutableStateOf(category.budgetLimit.toInt().toString()) }
    val pct = if (category.budgetLimit > 0)
        (category.spent / category.budgetLimit).coerceIn(0.0, 1.0).toFloat()
    else 0f

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(category.color.copy(alpha = 0.1f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = category.icon,
                        contentDescription = null,
                        tint = category.color,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = category.name,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                    Text(
                        text = "Spent ₹${"%,.0f".format(Locale.US, category.spent)} this month",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("₹", style = MaterialTheme.typography.titleMedium, color = Primary)
                    Spacer(modifier = Modifier.width(4.dp))
                    BasicTextField(
                        value = budgetText,
                        onValueChange = {
                            // Update the visible text on every keystroke, but only commit to
                            // the database on blur — writing per keystroke let unordered
                            // concurrent coroutines race and occasionally persist a stale
                            // intermediate value instead of the final typed amount.
                            if (it.all { c -> c.isDigit() }) budgetText = it
                        },
                        textStyle = MaterialTheme.typography.titleMedium.copy(
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.End
                        ),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        cursorBrush = SolidColor(Primary),
                        modifier = Modifier
                            .width(80.dp)
                            .onFocusChanged { focus ->
                                if (!focus.isFocused) onSave(budgetText.toDoubleOrNull() ?: 0.0)
                            },
                    )
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Default.DeleteOutline,
                            contentDescription = "Remove category",
                            tint = Color.Gray,
                        )
                    }
                }
            }

            if (category.budgetLimit > 0) {
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { pct },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    color = if (pct >= 1f) Color(0xFFEF4444) else category.color,
                    trackColor = Color.White.copy(alpha = 0.1f),
                )
            }
        }
    }
}
