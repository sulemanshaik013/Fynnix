package com.fynnix.app.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fynnix.app.ui.theme.Primary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesAndBudgetsScreen(onBack: () -> Unit) {
    // Mock data for categories and their budgets
    val mockCategories = remember {
        mutableStateListOf(
            CategoryBudgetUiModel("Food", Icons.Default.Restaurant, Color(0xFFE53935), 8000.0),
            CategoryBudgetUiModel("Transport", Icons.Default.DirectionsCar, Color(0xFF00897B), 4000.0),
            CategoryBudgetUiModel("Rent", Icons.Default.Home, Color(0xFF1E88E5), 15000.0),
            CategoryBudgetUiModel("Shopping", Icons.Default.ShoppingBag, Color(0xFF8E24AA), 8000.0),
            CategoryBudgetUiModel("Entertainment", Icons.Default.Movie, Color(0xFF43A047), 5000.0),
            CategoryBudgetUiModel("Health", Icons.Default.Favorite, Color(0xFF00ACC1), 3000.0),
            CategoryBudgetUiModel("Utilities", Icons.Default.Bolt, Color(0xFF546E7A), 2500.0)
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Categories & Budgets", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = { /* TODO Save logic */ onBack() }) {
                        Text("SAVE", color = Primary, fontWeight = FontWeight.Bold)
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
                text = "SET MONTHLY LIMITS",
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
                items(mockCategories) { category ->
                    CategoryBudgetItem(category)
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedButton(
                        onClick = { /* TODO Add New Category */ },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color.Gray.copy(alpha = 0.3f))
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = Primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Add New Category", color = Color.White)
                    }
                }
            }

            Button(
                onClick = { /* TODO Save logic */ onBack() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .height(56.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary)
            ) {
                Text(
                    text = "Save Configuration",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.Black
                )
            }
        }
    }
}

@Composable
fun CategoryBudgetItem(category: CategoryBudgetUiModel) {
    var budgetValue by remember { mutableStateOf(category.budgetLimit.toInt().toString()) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Category Icon
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

            // Category Name
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = category.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
                Text(
                    text = "Monthly Limit",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }

            // Budget Input
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("₹", style = MaterialTheme.typography.titleMedium, color = Primary)
                Spacer(modifier = Modifier.width(4.dp))
                BasicTextField(
                    value = budgetValue,
                    onValueChange = { if (it.all { char -> char.isDigit() }) budgetValue = it },
                    textStyle = MaterialTheme.typography.titleMedium.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.End
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    cursorBrush = SolidColor(Primary),
                    modifier = Modifier.width(80.dp)
                )
            }
        }
    }
}

data class CategoryBudgetUiModel(
    val name: String,
    val icon: ImageVector,
    val color: Color,
    val budgetLimit: Double
)
