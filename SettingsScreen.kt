package com.mecnun.assistant

import android.app.TimePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

private fun hm(m: Int) = "%02d:%02d".format(m / 60, m % 60)

@Composable
fun SettingsScreen() {
    var sub by remember { mutableStateOf(false) }
    BackHandler(enabled = sub) { sub = false }
    if (sub) ResponsesScreen { sub = false } else MainSettings { sub = true }
}

@Composable
private fun MainSettings(openResponses: () -> Unit) {
    val ctx = LocalContext.current

    var hitap by remember { mutableStateOf(Prefs.hitap) }
    var wake by remember { mutableStateOf(Prefs.wakeWord) }
    var tol by remember { mutableIntStateOf(Prefs.wakeTolerance) }
    var voice by remember { mutableIntStateOf(Prefs.voiceMode) }
    var rate by remember { mutableFloatStateOf(Prefs.speechRate) }
    var pitch by remember { mutableFloatStateOf(Prefs.pitch) }
    var quiet by remember { mutableStateOf(Prefs.quietEnabled) }
    var qStart by remember { mutableIntStateOf(Prefs.quietStart) }
    var qEnd by remember { mutableIntStateOf(Prefs.quietEnd) }
    var confirm by remember { mutableStateOf(Prefs.confirmCritical) }
    var noteApp by remember { mutableIntStateOf(Prefs.noteApp) }
    var folder by remember { mutableStateOf(Prefs.recFolder) }
    var quality by remember { mutableIntStateOf(Prefs.recQuality) }
    var batt by remember { mutableStateOf(Prefs.batteryWarn) }
    var battLevel by remember { mutableIntStateOf(Prefs.batteryLevel) }
    var logIgnored by remember { mutableStateOf(Prefs.logIgnored) }

    LazyColumn(contentPadding = screenPadding, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { ScreenTitle("Ayarlar") }

        item {
            PanelBox {
                Heading("Hitap ve uyanma")
                OutlinedTextField(
                    value = hitap,
                    onValueChange = { hitap = it; Prefs.hitap = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Size nasıl hitap etsin?") },
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                Choices(listOf("Efendim", "Patron", "Kaptan", "Hocam"), -1) {
                    val v = listOf("Efendim", "Patron", "Kaptan", "Hocam")[it]
                    hitap = v; Prefs.hitap = v
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = wake,
                    onValueChange = { wake = it; Prefs.wakeWord = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Uyandırma kelimesi") },
                    singleLine = true
                )
                Sub("Değiştirirseniz Türkçe model bu kelimeyi bilmeli. Dene ekranından ve Geçmişten kontrol edin.")
                Spacer(Modifier.height(10.dp))
                Text("Yanlış duymaya tolerans (\"mecnum\", \"mecnuh\" gibi)", color = TextHi, fontSize = 15.sp)
                Spacer(Modifier.height(6.dp))
                Choices(listOf("Sıkı", "Normal", "Esnek"), tol) { tol = it; Prefs.wakeTolerance = it }
            }
        }

        item {
            PanelBox {
                Heading("Sesli cevap")
                Choices(listOf("Kapalı", "Sadece sorular ve onaylar", "Hepsi"), voice) { voice = it; Prefs.voiceMode = it }
                Sub("Kapalıyken Mecnun sadece işi yapar, konuşmaz. Sorular bildirimde yazılı görünür.")
                Spacer(Modifier.height(8.dp))
                SliderRow("Konuşma hızı", "%.1fx".format(rate), rate, 0.6f..1.6f, 9) {
                    val v = (it * 10).roundToInt() / 10f
                    rate = v; Prefs.speechRate = v
                }
                SliderRow("Ses tonu", "%.1f".format(pitch), pitch, 0.6f..1.6f, 9) {
                    val v = (it * 10).roundToInt() / 10f
                    pitch = v; Prefs.pitch = v
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { Assistant.get(ctx).speakSample() }) { Text("Sesi dene") }
                    OutlinedButton(onClick = openResponses) { Text("Cevap cümleleri") }
                }
            }
        }

        item {
            PanelBox {
                Heading("Dinleme saatleri")
                SwitchRow("Gece mikrofonu kapat", "Servis açık kalır ama mikrofonu bırakır; pil de kazandırır.", quiet) {
                    quiet = it; Prefs.quietEnabled = it
                }
                if (quiet) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 6.dp)) {
                        OutlinedButton(onClick = {
                            TimePickerDialog(ctx, { _, h, m -> qStart = h * 60 + m; Prefs.quietStart = h * 60 + m }, qStart / 60, qStart % 60, true).show()
                        }) { Text("Başlangıç ${hm(qStart)}") }
                        OutlinedButton(onClick = {
                            TimePickerDialog(ctx, { _, h, m -> qEnd = h * 60 + m; Prefs.quietEnd = h * 60 + m }, qEnd / 60, qEnd % 60, true).show()
                        }) { Text("Bitiş ${hm(qEnd)}") }
                    }
                }
            }
        }

        item {
            PanelBox {
                Heading("Güvenlik")
                SwitchRow("Arama ve mesajda onay iste", "Aramadan önce \"aransın mı?\", mesajdan önce \"göndereyim mi?\" diye sorar.", confirm) {
                    confirm = it; Prefs.confirmCritical = it
                }
            }
        }

        item {
            PanelBox {
                Heading("Not alma")
                Sub("Notun gideceği uygulama. Samsung Notes kaydetmek için bir dokunuş isteyebilir.")
                Spacer(Modifier.height(8.dp))
                Choices(listOf("Otomatik", "Samsung Notes", "Google Keep", "Sadece burada"), noteApp) {
                    noteApp = it; Prefs.noteApp = it
                }
            }
        }

        item {
            PanelBox {
                Heading("Ses kaydı")
                OutlinedTextField(
                    value = folder,
                    onValueChange = { folder = it; Prefs.recFolder = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Klasör (Müzik/ altında)") },
                    singleLine = true
                )
                Spacer(Modifier.height(10.dp))
                Choices(listOf("Normal (16 kHz)", "Yüksek (48 kHz)"), quality) { quality = it; Prefs.recQuality = it }
                Sub("Kayıtlar WAV olarak Müzik/${folder} klasörüne yazılır.")
            }
        }

        item {
            PanelBox {
                Heading("Pil")
                SwitchRow("Şarj düşünce sesli uyar", null, batt) { batt = it; Prefs.batteryWarn = it }
                if (batt) {
                    SliderRow("Uyarı seviyesi", "%$battLevel", battLevel.toFloat(), 5f..40f, 6) {
                        val v = (it / 5).roundToInt() * 5
                        battLevel = v; Prefs.batteryLevel = v
                    }
                }
            }
        }

        item {
            PanelBox {
                Heading("Hata ayıklama")
                SwitchRow(
                    "Komut olmayan konuşmaları da geçmişe yaz",
                    "Mecnun'un neden uyanmadığını anlamak için. Duyduğu her cümle metin olarak kaydedilir.",
                    logIgnored
                ) { logIgnored = it; Prefs.logIgnored = it }
            }
        }
    }
}

@Composable
private fun ResponsesScreen(onBack: () -> Unit) {
    LazyColumn(contentPadding = screenPadding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Column {
                TextButton(onClick = onBack) { Text("← Ayarlar", color = Teal) }
                ScreenTitle("Cevap cümleleri", "Her satır bir seçenek; Mecnun rastgele birini seçer. {h} = hitap, {n} = sayı, {ad} = isim, {saat}, {metin}.")
            }
        }
        items(Responses.defs, key = { it.key }) { d ->
            var text by remember(d.key) { mutableStateOf(Responses.variants(d.key).joinToString("\n")) }
            PanelBox {
                Text(d.title, color = TextHi, fontSize = 15.sp)
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = {
                        text = it
                        val lines = it.lines().map { l -> l.trim() }.filter { l -> l.isNotEmpty() }
                        Prefs.setResponseOverride(d.key, if (lines.isEmpty() || lines == d.defaults) null else it)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                TextButton(onClick = {
                    Prefs.setResponseOverride(d.key, null)
                    text = d.defaults.joinToString("\n")
                }) { Text("Varsayılana dön", color = Violet) }
            }
        }
    }
}
