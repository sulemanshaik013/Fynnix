package com.fynnix.app.features.onboarding.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fynnix.app.core.sms.ProcessingProgress
import com.fynnix.app.core.ui.theme.Primary
import com.fynnix.app.features.onboarding.ui.components.AllSetViewModel

@Composable
fun AllSetScreen(onStartJourney: () -> Unit) {
    val viewModel: AllSetViewModel = hiltViewModel()
    val progress by viewModel.processingProgress.collectAsState(initial = ProcessingProgress())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.weight(1f))

        Box(
            modifier = Modifier
                .size(120.dp)
                .background(Color.White.copy(alpha = 0.05f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Surface(modifier = Modifier.size(72.dp), shape = CircleShape, color = Primary) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "You're all set!",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 30.sp
            ),
            color = Color.White,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = if (progress.isProcessing) progress.status.ifEmpty { "Scanning your SMS..." }
                   else "Fynnix tracks your finances locally. No data leaves this device.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        if (progress.messagesRead > 0 || progress.isProcessing) {
            LinearProgressIndicator(
                progress = { progress.progress },
                modifier = Modifier.fillMaxWidth().height(6.dp),
                color = Primary,
                trackColor = Color.White.copy(alpha = 0.1f)
            )
            Spacer(modifier = Modifier.height(20.dp))
        }

        // Live stats grid — visible whenever there's any data, regardless of processing state.
        StatsGrid(progress)

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = {
                viewModel.completeOnboarding()
                onStartJourney()
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Primary)
        ) {
            Text(
                text = "Start My Financial Journey",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.Black
            )
        }

        if (progress.isProcessing) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Sync continues in the background",
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "PRIVACY FIRST — LOCAL ONLY",
            style = MaterialTheme.typography.labelSmall.copy(
                letterSpacing = 2.sp,
                fontWeight = FontWeight.Medium
            ),
            color = Color.Gray.copy(alpha = 0.6f)
        )

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun StatsGrid(p: ProcessingProgress) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatCell("SCANNED", "${p.messagesScanned} / ${p.messagesRead}", Modifier.weight(1f))
            StatCell("TRANSACTIONAL", p.messagesTransactional.toString(), Modifier.weight(1f))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatCell("PARSED", p.messagesParsed.toString(), Modifier.weight(1f))
            StatCell("DUPLICATE", p.messagesDuplicate.toString(), Modifier.weight(1f))
            StatCell("SAVED", p.messagesSaved.toString(), Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatCell(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = Color.White.copy(alpha = 0.04f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = Color.White
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                color = Color.Gray
            )
        }
    }
}
