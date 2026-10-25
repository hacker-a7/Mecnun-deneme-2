package com.mecnun.assistant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val examples = listOf(
    "Telefon" to "kapan  •  ekranı kilitle  •  ekran görüntüsü al  •  feneri aç  •  feneri kapat",
    "Ses" to "sesi kıs  •  sesi aç  •  sesi kapat  •  sesi yüzde elli yap",
    "Bilgi" to "saat kaç  •  şarj yüzde kaç",
    "Uygulama" to "WhatsApp'a gir  •  Kamera'yı aç  •  (tanımazsa: Uygulamalar ekranından \"öğret\")",
    "Arama / mesaj" to "Ahmet'i ara  •  Ahmet'e mesaj gönder (sonra mesajı söyle, \"bitti\" de)",
    "Not" to "not al  •  not al süt al  •  notlarımı oku  •  son notu oku",
    "Alarm" to "alarm kur yedi buçuğa  •  on dakika sonraya alarm kur",
    "Kayıt" to "kayıt başlat  •  kaydı durdur",
    "Müzik" to "müziği durdur  •  devam et  •  sonraki şarkı"
)

@Composable
fun TestScreen() {
    val ctx = LocalContext.current
    var text by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LazyColumn(contentPadding = screenPadding, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { ScreenTitle("Dene", "Komutu yazın; Mecnun ne anladığını göstersin ya da çalıştırsın. Mikrofon gerekmez.") }
        item {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Örn: sesi kıs") },
                singleLine = true
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = {
                    scope.launch {
                        result = withContext(Dispatchers.IO) { Assistant.get(ctx).interpretOnly(text) }
                    }
                }) { Text("Ne anlaşılır?") }
                Button(onClick = {
                    val needWake = Wake.find(Tr.tokens(text)) == null
                    Assistant.get(ctx).submit(if (needWake) Prefs.wakeWord + " " + text else text)
                    result = "Çalıştırıldı. Sonuç için Geçmiş ekranına bakın."
                }) { Text("Çalıştır") }
            }
        }
        item {
            val r = result
            if (r != null) {
                PanelBox {
                    Sub("Sonuç")
                    Spacer(Modifier.height(4.dp))
                    Text(r, color = Teal, fontSize = 16.sp)
                }
            }
        }
        item {
            PanelBox {
                Heading("Komut örnekleri")
                Sub("Hepsinin başına \"Mecnun\" diyorsunuz.")
                Spacer(Modifier.height(8.dp))
                examples.forEach { (k, v) ->
                    Text(k, color = Violet, fontSize = 13.sp)
                    Text(v, color = TextHi, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}
