package com.fynnix.app.features.onboarding.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.hilt.navigation.compose.hiltViewModel
import com.fynnix.app.core.ui.theme.Primary
import com.fynnix.app.features.categories.data.CategoryIcons
import com.fynnix.app.features.onboarding.ui.components.OnboardingViewModel

@Composable
fun AboutYouScreen(
    onBack: () -> Unit,
    onNext: () -> Unit
) {

    val viewModel: OnboardingViewModel = hiltViewModel()
    LaunchedEffect(Unit) {
        viewModel.loadCategories()
    }
    val categories  by viewModel.categories.collectAsState()
    val budgetEdits by viewModel.budgetEdits.collectAsState()

    var name by remember { mutableStateOf("") }

    var income by remember {
        mutableStateOf(getDefaultIncome().toString())
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        // Back Button
        IconButton(
            onClick = onBack,
            modifier = Modifier.padding(8.dp)
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(40.dp),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 40.dp)
        ) {
            item {
                Column {
                    Text(
                        text = "Tell us about yourself",
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Stored only on your device. You can change everything later in Settings.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray
                    )
                }
            }

            // Section 1: Name
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "WHAT SHOULD WE CALL YOU?",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray,
                        letterSpacing = 1.sp
                    )
                    TextField(
                        value = name,
                        onValueChange = { name = it },
                        placeholder = { Text("Your name", color = Color.Gray) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            unfocusedContainerColor = Color.Transparent,
                            focusedContainerColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Gray.copy(alpha = 0.5f),
                            focusedIndicatorColor = Primary,
                            cursorColor = Primary,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                }
            }

            // Section 2: Income
            item {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "WHAT IS YOUR MONTHLY INCOME?",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray,
                        letterSpacing = 1.sp,
                        modifier = Modifier.align(Alignment.Start)
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text("₹", style = MaterialTheme.typography.displayMedium, color = Primary)
                        Spacer(modifier = Modifier.width(16.dp))

                        BasicTextField(
                            value = income,
                            onValueChange = { newValue ->
                                if (newValue.all { it.isDigit() }) income = newValue
                            },
                            textStyle = MaterialTheme.typography.displayLarge.copy(
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Start
                            ),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            cursorBrush = SolidColor(Primary),
                            modifier = Modifier.widthIn(min = 120.dp),
                            decorationBox = { innerTextField ->
                                Box {
                                    if (income.isEmpty()) {
                                        Text(
                                            "0",
                                            style = MaterialTheme.typography.displayLarge.copy(
                                                color = Color.Gray.copy(alpha = 0.3f),
                                                fontWeight = FontWeight.Bold
                                            )
                                        )
                                    }
                                    innerTextField()
                                }
                            }
                        )
                    }
                    Text(
                        text = "You can change this later in Settings",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
            }

            // Section 3: Budgets
            item {
                Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    Column {
                        Text(
                            text = "SET YOUR SPENDING LIMITS",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Gray,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "We've set healthy defaults for you.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }

                    categories.forEach { category ->
                        BudgetInputRow(
                            label = category.name,
                            icon  = CategoryIcons.get(category.icon),
                            value = budgetEdits[category.id] ?: 0.0,
                            onValueChange = { viewModel.updateBudget(category.id, it) }
                        )
                    }
                }
            }

            item {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Skip for now",
                        color = if (name.isNotBlank()) Primary else Color.Gray,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable(enabled = name.isNotBlank()) {
                            val incomeValue = income.toDoubleOrNull() ?: getDefaultIncome().toDouble()
                            viewModel.saveData(name, incomeValue)
                            onNext()
                        }
                    )
                }
            }
        }

        Button(
            onClick = {
                val incomeValue = income.toDoubleOrNull() ?: getDefaultIncome().toDouble()
                viewModel.saveData(name, incomeValue)
                onNext()
            },
            enabled = name.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .height(56.dp),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Primary,
                disabledContainerColor = Primary.copy(alpha = 0.2f)
            )
        ) {
            Text(
                text = "Next",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = if (name.isNotBlank()) Color.Black else Color.Black.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
private fun BudgetInputRow(label: String, icon: ImageVector, value: Double, onValueChange: (Double) -> Unit) {
    var textValue by remember { mutableStateOf(value.toInt().toString()) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(Color.White.copy(alpha = 0.05f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = Primary, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White,
            modifier = Modifier.weight(1f)
        )

        Text("₹", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
        Spacer(modifier = Modifier.width(8.dp))

        BasicTextField(
            value = textValue,
            onValueChange = { newValue ->
                if (newValue.all { it.isDigit() }) {
                    textValue = newValue
                    onValueChange(newValue.toDoubleOrNull() ?: 0.0)
                }
            },
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = Color.White,
                textAlign = TextAlign.End,
                fontWeight = FontWeight.Bold
            ),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            cursorBrush = SolidColor(Primary),
            modifier = Modifier.width(80.dp)
        )
    }
}

fun getDefaultIncome(): Int {
    val manufacturer = android.os.Build.MANUFACTURER.lowercase()
    val model = android.os.Build.MODEL.lowercase()

    return when {
        manufacturer.contains("samsung") && model.contains("s") -> 60000
        manufacturer.contains("oneplus") -> 40000
        manufacturer.contains("apple") -> 70000

        manufacturer.contains("xiaomi") || manufacturer.contains("realme") -> 30000

        else -> 25000
    }
}