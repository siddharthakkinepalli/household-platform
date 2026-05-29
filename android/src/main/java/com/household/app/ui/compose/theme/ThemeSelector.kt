package com.household.app.ui.compose.theme

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import java.util.Locale

@Composable
fun ThemeSelector(
    currentTheme: JugaadThemeSelection,
    onThemeSelected: (JugaadThemeSelection) -> Unit
) {
    Column {
        Text(
            text = "App Theme",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        JugaadThemeSelection.entries.forEach { option ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = option == currentTheme,
                        onClick = { onThemeSelected(option) },
                        role = Role.RadioButton
                    )
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = option == currentTheme, onClick = null)
                Text(
                    text = option.name.replace("_", " ")
                        .lowercase(Locale.getDefault())
                        .split(" ")
                        .joinToString(" ") { it.replaceFirstChar { c -> c.uppercaseChar() } },
                    modifier = Modifier.padding(start = 16.dp)
                )
            }
        }
    }
}
