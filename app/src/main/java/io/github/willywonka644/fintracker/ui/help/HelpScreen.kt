package io.github.willywonka644.fintracker.ui.help

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.willywonka644.fintracker.R

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen(
    onNavigateBack: () -> Unit
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.help_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            HelpSection(
                title = stringResource(R.string.help_account_title),
                body = stringResource(R.string.help_account_body)
            )
            HelpSection(
                title = stringResource(R.string.help_booking_title),
                body = stringResource(R.string.help_booking_body)
            )
            HelpSection(
                title = stringResource(R.string.help_amounts_title),
                body = stringResource(R.string.help_amounts_body)
            )
            HelpSection(
                title = stringResource(R.string.help_billing_title),
                body = stringResource(R.string.help_billing_body)
            )
            HelpSection(
                title = stringResource(R.string.help_limits_title),
                body = stringResource(R.string.help_limits_body)
            )
            HelpSection(
                title = stringResource(R.string.help_recurring_title),
                body = stringResource(R.string.help_recurring_body)
            )
            HelpSection(
                title = stringResource(R.string.help_categories_title),
                body = stringResource(R.string.help_categories_body)
            )
            HelpSection(
                title = stringResource(R.string.help_analytics_title),
                body = stringResource(R.string.help_analytics_body)
            )
            HelpSection(
                title = stringResource(R.string.help_csv_import_title),
                body = stringResource(R.string.help_csv_import_body)
            )
            HelpSection(
                title = stringResource(R.string.help_qr_scan_title),
                body = stringResource(R.string.help_qr_scan_body)
            )
            HelpSection(
                title = stringResource(R.string.help_ocr_title),
                body = stringResource(R.string.help_ocr_body)
            )
            HelpSection(
                title = stringResource(R.string.help_attachments_title),
                body = stringResource(R.string.help_attachments_body)
            )
            HelpSection(
                title = stringResource(R.string.help_installments_title),
                body = stringResource(R.string.help_installments_body)
            )
            HelpSection(
                title = stringResource(R.string.help_reconciliation_title),
                body = stringResource(R.string.help_reconciliation_body)
            )
            HelpSection(
                title = stringResource(R.string.help_audit_title),
                body = stringResource(R.string.help_audit_body)
            )
            HelpSection(
                title = stringResource(R.string.help_quickadd_title),
                body = stringResource(R.string.help_quickadd_body)
            )
            HelpSection(
                title = stringResource(R.string.help_backup_title),
                body = stringResource(R.string.help_backup_body)
            )
        }
    }
}

@Composable
private fun HelpSection(
    title: String,
    body: String
) {
    var expanded by remember { mutableStateOf(false) }
    val icon = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown
    val iconLabel = if (expanded) {
        stringResource(R.string.help_collapse)
    } else {
        stringResource(R.string.help_expand)
    }

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        imageVector = icon,
                        contentDescription = iconLabel
                    )
                }
            }
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = body, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
