package com.medidiary.app.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@Composable
fun MessageBanner(text: String, kind: BannerKind, modifier: Modifier = Modifier) {
    val (icon: ImageVector, container) = when (kind) {
        BannerKind.ERROR -> Icons.Outlined.ErrorOutline to MaterialTheme.colorScheme.errorContainer
        BannerKind.INFO -> Icons.Outlined.Info to MaterialTheme.colorScheme.secondaryContainer
        BannerKind.MOCK -> Icons.Outlined.Science to MaterialTheme.colorScheme.tertiaryContainer
    }
    Card(modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = container)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null)
            Spacer(Modifier.width(10.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

enum class BannerKind { ERROR, INFO, MOCK }

const val MOCK_MODE_TEXT =
    "Demo mode: model files not installed, so AI output is scripted. Install models in Settings → AI models."
