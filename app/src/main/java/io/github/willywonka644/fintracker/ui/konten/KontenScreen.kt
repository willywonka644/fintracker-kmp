package io.github.willywonka644.fintracker.ui.konten

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.willywonka644.fintracker.Account
import io.github.willywonka644.fintracker.R
import io.github.willywonka644.fintracker.ui.accountdetail.EmptyStateMessage
import io.github.willywonka644.fintracker.ui.nav.GradientFAB
import io.github.willywonka644.fintracker.ui.overview.AccountListItem
import io.github.willywonka644.fintracker.ui.theme.FinTheme

/**
 * Anlegen, bearbeiten und löschen von Konten an einer Stelle (#111).
 *
 * Bearbeiten und Löschen hingen bisher an einem **langen Druck** auf die Kontokarte der
 * Übersicht — einer Geste, die sich nirgends ankündigt: wer sie sucht, findet sie nicht,
 * und wer sie nicht will, trifft sie beim Scrollen. Deshalb steht hier ein Satz darüber,
 * was ein Tipp auf eine Zeile tut; das ist der eigentliche Zweck des Bildschirms.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KontenScreen(
    accounts: List<Account>,
    accountBalances: Map<String, Double>,
    onNavigateBack: () -> Unit,
    onAddAccount: () -> Unit,
    onEditAccount: (Account) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Konten") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back),
                        )
                    }
                },
            )
        },
        floatingActionButton = { GradientFAB(onClick = onAddAccount) },
    ) { innerPadding ->
        if (accounts.isEmpty()) {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                EmptyStateMessage("Noch keine Konten angelegt")
            }
        } else {
            LazyColumn(
                modifier = modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 16.dp),
            ) {
                item {
                    Text(
                        text = "Tippen zum Bearbeiten oder Löschen",
                        style = MaterialTheme.typography.bodySmall,
                        color = FinTheme.colors.textSub,
                    )
                }
                items(accounts, key = { it.id }) { account ->
                    AccountListItem(
                        account = account,
                        currentBalance = accountBalances[account.id] ?: 0.0,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onEditAccount(account) },
                    )
                }
            }
        }
    }
}
