package com.mecnun.assistant

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppsScreen() {
    val ctx = LocalContext.current
    var query by remember { mutableStateOf("") }
    var aliasTick by remember { mutableIntStateOf(0) }
    val apps by produceState(initialValue = emptyList<AppEntry>()) {
        value = withContext(Dispatchers.IO) { Directory.apps(ctx, force = true) }
    }
    val aliases = remember(aliasTick) { Prefs.aliases() }
    val status by ServiceState.status.collectAsState()
    var addFor by remember { mutableStateOf<AppEntry?>(null) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1500)
            aliasTick += 1
        }
    }

    val q = Tr.norm(query)
    val filtered = remember(apps, q) {
        if (q.isEmpty()) apps else apps.filter { Tr.norm(it.label).contains(q) }
    }

    LazyColumn(contentPadding = screenPadding, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { ScreenTitle("Uygulamalar", "${apps.size} uygulama tanınıyor") }
        item {
            PanelBox {
                Sub("Telefondaki tüm uygulamalar otomatik tanınır; yeni yüklediğiniz de listeye girer. Mecnun bir adı yanlış anlıyorsa (örn. WhatsApp'ı \"vatsap\" diye duyuyorsa) \"Sesle öğret\"e basıp adı söyleyin, bundan sonra o söyleyişi tanır.")
            }
        }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Uygulama ara") },
                singleLine = true
            )
        }
        items(filtered, key = { it.pkg }) { app ->
            val mine = aliases.filter { it.value == app.pkg }.keys.toList()
            PanelBox {
                Text(app.label, color = TextHi, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                if (mine.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(top = 6.dp)
                    ) {
                        mine.forEach { a ->
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(Panel2)
                                    .clickable {
                                        Prefs.removeAlias(a)
                                        aliasTick += 1
                                    }
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) { Text("$a  ✕", color = Teal, fontSize = 12.sp) }
                        }
                    }
                }
                Row {
                    TextButton(onClick = {
                        if (status == Status.LISTENING || status == Status.AWAITING) {
                            Assistant.get(ctx).startLearn(app)
                            Toast.makeText(ctx, "Şimdi uygulamanın adını söyleyin", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(ctx, "Önce Ana ekrandan Mecnun'u başlatın", Toast.LENGTH_SHORT).show()
                        }
                    }) { Text("🎤 Sesle öğret", color = Violet) }
                    TextButton(onClick = { addFor = app }) { Text("✎ Yazarak ekle", color = Violet) }
                }
            }
        }
    }

    val target = addFor
    if (target != null) {
        var t by remember(target.pkg) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { addFor = null },
            title = { Text("${target.label} için ad ekle") },
            text = {
                OutlinedTextField(
                    value = t,
                    onValueChange = { t = it },
                    singleLine = true,
                    label = { Text("Nasıl söylersiniz? (örn: vatsap)") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    Prefs.addAlias(t, target.pkg)
                    aliasTick += 1
                    addFor = null
                }) { Text("Ekle") }
            },
            dismissButton = { TextButton(onClick = { addFor = null }) { Text("Vazgeç") } }
        )
    }
}
