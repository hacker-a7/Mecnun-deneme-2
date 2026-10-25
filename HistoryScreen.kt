package com.mecnun.assistant

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen() {
    var tab by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            ScreenTitle("Geçmiş", "Ne duyduğu, ne anladığı ve ne yaptığı. Ses kaydı tutulmaz, sadece metin.")
        }
        TabRow(
            selectedTabIndex = tab,
            containerColor = Bg,
            contentColor = Teal
        ) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Komutlar") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Notlar") })
        }
        if (tab == 0) CommandList() else NoteList()
    }
}

@Composable
private fun CommandList() {
    val ctx = LocalContext.current
    val items by History.items.collectAsState()
    val fmt = remember { SimpleDateFormat("d MMM HH:mm:ss", Locale("tr", "TR")) }
    LazyColumn(contentPadding = screenPadding, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = {
                    val i = Intent(Intent.ACTION_SEND).setType("text/plain")
                        .putExtra(Intent.EXTRA_TEXT, History.exportText())
                    try {
                        ctx.startActivity(Intent.createChooser(i, "Geçmişi dışa aktar"))
                    } catch (_: Exception) {
                    }
                }) { Text("Dışa aktar", color = Teal) }
                TextButton(onClick = { History.clear() }) { Text("Temizle", color = Rose) }
            }
        }
        if (items.isEmpty()) {
            item { PanelBox { Sub("Henüz kayıt yok.") } }
        }
        items(items) { h ->
            PanelBox {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(fmt.format(Date(h.time)), color = TextLo, fontSize = 12.sp)
                    Text(if (h.ok) "başarılı" else "başarısız", color = if (h.ok) Teal else Rose, fontSize = 12.sp)
                }
                Text("Duyulan: ${h.heard}", color = TextHi, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
                Text("Anlaşılan: ${h.understood}", color = Violet, fontSize = 14.sp)
                Text("Sonuç: ${h.result}", color = TextLo, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun NoteList() {
    val ctx = LocalContext.current
    val notes by Notes.items.collectAsState()
    val fmt = remember { SimpleDateFormat("d MMM HH:mm", Locale("tr", "TR")) }
    LazyColumn(contentPadding = screenPadding, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Sub("Her not telefondaki not uygulamasına gönderilir; burada da bir kopyası durur. \"Notlarımı oku\" komutu bu kopyayı okur.")
        }
        if (notes.isEmpty()) {
            item { PanelBox { Sub("Henüz not yok. \"Mecnun, not al\" deyin.") } }
        }
        items(notes) { n ->
            PanelBox {
                Text(fmt.format(Date(n.time)), color = TextLo, fontSize = 12.sp)
                Text(n.text, color = TextHi, fontSize = 15.sp, modifier = Modifier.padding(vertical = 6.dp))
                Row {
                    TextButton(onClick = { shareToNotes(ctx, n.text) }) { Text("Not uygulamasına gönder", color = Teal) }
                    TextButton(onClick = { Notes.remove(n) }) { Text("Sil", color = Rose) }
                }
            }
        }
    }
}
