package com.mecnun.assistant

import android.Manifest
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PermRow(val title: String, val desc: String, val ok: Boolean, val action: () -> Unit)

fun statusColor(s: Status): Color = when (s) {
    Status.STOPPED -> TextLo
    Status.LOADING -> Sky
    Status.LISTENING -> Teal
    Status.AWAITING -> Violet
    Status.QUIET -> Amber
    Status.ERROR -> Rose
}

fun statusTitle(s: Status): String = when (s) {
    Status.STOPPED -> "Kapalı"
    Status.LOADING -> "Hazırlanıyor"
    Status.LISTENING -> "Dinliyor"
    Status.AWAITING -> "Komut bekliyor"
    Status.QUIET -> "Gece modu"
    Status.ERROR -> "Sorun var"
}

@Composable
fun HomeScreen() {
    val ctx = LocalContext.current
    val status by ServiceState.status.collectAsState()
    val note by ServiceState.note.collectAsState()
    val history by History.items.collectAsState()
    val tick = MainActivity.resumeTick.intValue
    var pendingStart by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        MainActivity.resumeTick.intValue = MainActivity.resumeTick.intValue + 1
        if (pendingStart && hasPerm(ctx, micPerm)) {
            pendingStart = false
            ListenService.start(ctx)
        }
    }

    fun toggle() {
        if (status == Status.STOPPED || status == Status.ERROR) {
            if (hasPerm(ctx, micPerm)) {
                ListenService.start(ctx)
            } else {
                pendingStart = true
                val list = ArrayList<String>()
                list.add(micPerm)
                if (Build.VERSION.SDK_INT >= 33) list.add(Manifest.permission.POST_NOTIFICATIONS)
                launcher.launch(list.toTypedArray())
            }
        } else {
            ListenService.stop(ctx)
        }
    }

    val rows = remember(tick) {
        val pkg = ctx.packageName
        val pm = ctx.getSystemService(android.content.Context.POWER_SERVICE) as PowerManager
        val list = ArrayList<PermRow>()
        list.add(PermRow("Mikrofon", "Sesinizi dinlemek için şart", hasPerm(ctx, micPerm)) {
            launcher.launch(arrayOf(micPerm))
        })
        if (Build.VERSION.SDK_INT >= 33) {
            list.add(PermRow("Bildirim", "Dinlediğini gösteren bildirim için", hasPerm(ctx, Manifest.permission.POST_NOTIFICATIONS)) {
                launcher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
            })
        }
        val contactPerms = arrayOf(
            Manifest.permission.READ_CONTACTS, Manifest.permission.CALL_PHONE, Manifest.permission.SEND_SMS
        )
        list.add(PermRow("Rehber, arama, SMS", "Kişi arama ve mesaj gönderme için", contactPerms.all { hasPerm(ctx, it) }) {
            launcher.launch(contactPerms)
        })
        list.add(PermRow("Erişilebilirlik servisi", "Ekranı kilitleme ve ekran görüntüsü için", accessibilityEnabled(ctx)) {
            openSettingsIntent(ctx, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })
        list.add(PermRow("Diğer uygulamaların üzerinde göster", "Arka plandan uygulama açma ve arama için", Settings.canDrawOverlays(ctx)) {
            openSettingsIntent(ctx, Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$pkg")))
        })
        list.add(PermRow("Pil kısıtlaması yok", "Android'in servisi uyutmaması için", pm.isIgnoringBatteryOptimizations(pkg)) {
            openSettingsIntent(ctx, Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$pkg")))
        })
        list
    }

    LazyColumn(
        contentPadding = screenPadding,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Hero(status, note) { toggle() }
        }
        item {
            val last = history.firstOrNull()
            PanelBox {
                Heading("Son komut")
                if (last == null) {
                    Sub("Henüz komut yok. Mecnun'u başlatıp \"Mecnun, saat kaç?\" deyin.")
                } else {
                    val fmt = SimpleDateFormat("HH:mm:ss", Locale("tr", "TR"))
                    Text("“${last.heard}”", color = TextHi, fontSize = 15.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("→ ${last.understood}", color = Teal, fontSize = 14.sp)
                    Text(last.result, color = TextLo, fontSize = 13.sp)
                    Text(fmt.format(Date(last.time)), color = TextLo, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
        item {
            val done = rows.count { it.ok }
            PanelBox {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Heading("Kurulum")
                    Text("$done / ${rows.size}", color = if (done == rows.size) Teal else Amber, fontWeight = FontWeight.SemiBold)
                }
                rows.forEach { r ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(if (r.ok) Teal else Amber))
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(r.title, color = TextHi, fontSize = 15.sp)
                            Sub(r.desc)
                        }
                        if (!r.ok) TextButton(onClick = r.action) { Text("Ver", color = Violet) }
                    }
                }
            }
        }
        item {
            PanelBox {
                Heading("Samsung ipuçları")
                Sub("• Erişilebilirlik anahtarı gri ve kapalıysa: Ayarlar > Uygulamalar > Mecnun > sağ üst ⋮ > \"Kısıtlı ayarlara izin ver\". Sonra tekrar deneyin.")
                Spacer(Modifier.height(6.dp))
                Sub("• Ayarlar > Pil > Arka plan kullanım sınırları > \"Uyku moduna alınmayacak uygulamalar\" listesine Mecnun'u ekleyin.")
                Spacer(Modifier.height(6.dp))
                Sub("• Telefon yeniden başlayınca bir bildirim gelir; dokunup Mecnun'u tekrar başlatın (Android kuralı).")
            }
        }
    }
}

@Composable
private fun Hero(status: Status, note: String, onToggle: () -> Unit) {
    val c = statusColor(status)
    val active = status == Status.LISTENING || status == Status.AWAITING
    val inf = rememberInfiniteTransition(label = "orb")
    val pulse by inf.animateFloat(
        initialValue = 0.93f,
        targetValue = 1.07f,
        animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )
    PanelBox {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier
                    .size(160.dp)
                    .scale(if (active) pulse else 1f)
                    .clip(CircleShape)
                    .background(Brush.radialGradient(listOf(c.copy(alpha = 0.55f), c.copy(alpha = 0.05f)))),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier.size(100.dp).clip(CircleShape).background(c),
                    contentAlignment = Alignment.Center
                ) {
                    Text("M", color = Color(0xFF0B1020), fontSize = 44.sp, fontWeight = FontWeight.Black)
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(statusTitle(status), color = c, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(
                if (note.isBlank()) "Başlatınca arka planda \"Mecnun\" demenizi bekler" else note,
                color = TextLo, fontSize = 13.sp, textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp, bottom = 14.dp)
            )
            Button(
                onClick = onToggle,
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (status == Status.STOPPED || status == Status.ERROR) Violet else Panel2
                ),
                modifier = Modifier.fillMaxWidth(0.7f).height(50.dp)
            ) {
                Text(if (status == Status.STOPPED || status == Status.ERROR) "Dinlemeyi başlat" else "Durdur", fontSize = 16.sp)
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}
