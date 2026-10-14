package io.github.tuxwheel.settings

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.github.tuxwheel.R

@Composable
fun ThirdPartyLicensesDialog(onDismissRequest: () -> Unit) {
    val context = LocalContext.current
    val licensesText = remember(context) {
        listOf(
            "licenses/third_party_dependencies.txt",
            "licenses/NOTICE.txt",
            "licenses/GPL-3.0.txt",
            "licenses/SourceSansPro-OFL.txt",
        ).joinToString("\n\n") { path ->
            context.assets.open(path).bufferedReader().use { it.readText() }
        }
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(R.string.third_party_licenses_title)) },
        text = {
            SelectionContainer(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(licensesText)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(android.R.string.ok))
            }
        },
    )
}
