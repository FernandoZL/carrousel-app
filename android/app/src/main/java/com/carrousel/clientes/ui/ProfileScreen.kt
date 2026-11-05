package com.carrousel.clientes.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.carrousel.clientes.data.Profile
import java.time.Instant
import java.time.ZoneOffset

@Composable
fun ProfileScreen(
    profile: Profile?,
    busy: Boolean,
    onSave: (String, String) -> Unit,
    onAddMember: (String, String, () -> Unit) -> Unit,
    onLogout: () -> Unit,
    padding: PaddingValues,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var adding by rememberSaveable { mutableStateOf(false) }
    var confirmLogout by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(profile?.name, profile?.phone) { name = profile?.name.orEmpty(); phone = profile?.phone.orEmpty() }

    Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Mis datos", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(profile?.email.orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(name, { name = it.take(140) }, label = { Text("Nombre") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(phone, { phone = it.take(30) }, label = { Text("Teléfono (opcional)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth())
                Button(onClick = { onSave(name, phone) }, enabled = !busy && name.isNotBlank()) { Text("Guardar") }
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Festejados", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Cada festejado recibe un cupón de cumpleaños al año. La fecha no se puede cambiar después; revísala bien.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val members = profile?.members.orEmpty()
                if (members.isEmpty()) Text("Todavía no agregas festejados.")
                members.forEachIndexed { i, m ->
                    if (i > 0) HorizontalDivider()
                    Column(Modifier.padding(vertical = 6.dp)) {
                        Text(m.name, fontWeight = FontWeight.SemiBold)
                        Text("Nació el ${prettyDate(m.birthDate)}", style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (members.size < (profile?.maxMembers ?: 8)) OutlinedButton(onClick = { adding = true }, enabled = !busy) { Text("Agregar festejado") }
            }
        }
        TextButton(onClick = { confirmLogout = true }) { Text("Cerrar sesión") }
    }
    if (adding) AddMemberDialog(busy, onDismiss = { adding = false }, onAdd = { n, d -> onAddMember(n, d) { adding = false } })
    if (confirmLogout) AlertDialog(
        onDismissRequest = { confirmLogout = false },
        title = { Text("¿Cerrar sesión?") },
        text = { Text("Para volver a entrar te enviaremos un código a tu correo.") },
        confirmButton = { TextButton(onClick = { confirmLogout = false; onLogout() }) { Text("Cerrar sesión") } },
        dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text("Cancelar") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddMemberDialog(busy: Boolean, onDismiss: () -> Unit, onAdd: (String, String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var date by rememberSaveable { mutableStateOf<String?>(null) }
    var picking by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nuevo festejado") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it.take(140) }, label = { Text("Nombre completo") }, singleLine = true)
                OutlinedButton(onClick = { picking = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(date?.let { "Nació el ${prettyDate(it)}" } ?: "Elegir fecha de nacimiento")
                }
            }
        },
        confirmButton = { TextButton(onClick = { onAdd(name, date!!) }, enabled = !busy && name.isNotBlank() && date != null) { Text("Agregar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
    if (picking) {
        val now = System.currentTimeMillis()
        val state = rememberDatePickerState(selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= now
        })
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString() }
                    picking = false
                }) { Text("Aceptar") }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("Cancelar") } },
        ) { DatePicker(state = state) }
    }
}
