package com.mecnun.assistant

import kotlin.random.Random

/** Cevap cümleleri. Yer tutucular: {h} hitap, {n} sayı, {ad} isim, {saat}, {metin}. */
object Responses {
    class Def(val key: String, val title: String, val defaults: List<String>)

    val defs: List<Def> = listOf(
        Def("awake", "Sadece \"Mecnun\" dediğimde", listOf("Dinliyorum {h}.", "Buyurun {h}.", "Emrinizi bekliyorum {h}.")),
        Def("volume_down", "Ses kısıldı", listOf("Ses azaltıldı {h}.", "Sesi kıstım {h}.")),
        Def("volume_up", "Ses açıldı", listOf("Ses artırıldı {h}.", "Sesi yükselttim {h}.")),
        Def("volume_set", "Ses seviyesi ayarlandı", listOf("Ses yüzde {n} yapıldı {h}.")),
        Def("mute", "Ses kapatıldı", listOf("Ses kapatıldı {h}.", "Sessize aldım {h}.")),
        Def("lock", "Ekran kilitlendi", listOf("Ekran kilitleniyor {h}.", "Tamamdır {h}.")),
        Def("screenshot", "Ekran görüntüsü alındı", listOf("Ekran görüntüsü alındı {h}.", "Çektim {h}.")),
        Def("torch_on", "Fener açıldı", listOf("Fener açıldı {h}.")),
        Def("torch_off", "Fener kapandı", listOf("Fener kapatıldı {h}.")),
        Def("time", "Saat", listOf("Saat {saat} {h}.")),
        Def("battery", "Şarj durumu", listOf("Şarj yüzde {n} {h}.")),
        Def("battery_charging", "Şarj durumu (şarjdayken)", listOf("Şarj yüzde {n}, şarj oluyor {h}.")),
        Def("battery_low", "Şarj azaldı uyarısı", listOf("Şarj yüzde {n} {h}, şarj cihazını takmanız iyi olur.")),
        Def("app_open", "Uygulama açılıyor", listOf("{ad} açılıyor {h}.", "{ad} uygulamasına giriyorum {h}.")),
        Def("app_not_found", "Uygulama bulunamadı", listOf("Bu isimde bir uygulama bulamadım {h}.")),
        Def("rec_start", "Ses kaydı başladı", listOf("Ses kaydı başlatıldı {h}.")),
        Def("rec_stop", "Ses kaydı bitti", listOf("Ses kaydı kaydedildi {h}.")),
        Def("rec_already", "Kayıt zaten açık", listOf("Zaten kayıt yapıyorum {h}.")),
        Def("rec_none", "Kayıt yok", listOf("Şu an devam eden bir kayıt yok {h}.")),
        Def("call", "Arama başlatıldı", listOf("{ad} aranıyor {h}.")),
        Def("call_confirm", "Arama onayı", listOf("{ad} aransın mı {h}?")),
        Def("contact_not_found", "Kişi bulunamadı", listOf("Rehberde bu isimde birini bulamadım {h}.")),
        Def("sms_ask", "Mesaj metni sorusu", listOf("{ad} için ne yazayım {h}?")),
        Def("sms_confirm", "Mesaj onayı", listOf("Mesaj şöyle: {metin}. Göndereyim mi {h}?")),
        Def("sms_sent", "Mesaj gönderildi", listOf("Mesaj gönderildi {h}.")),
        Def("cancelled", "İptal edildi", listOf("Tamam, iptal ettim {h}.")),
        Def("timeout", "Cevap gelmedi", listOf("Bir şey duyamadım, iptal ettim {h}.")),
        Def("note_ask", "Not sorusu", listOf("Söyleyin {h}.")),
        Def("note_saved", "Not kaydedildi", listOf("Notu aldım {h}.", "Not alındı {h}.")),
        Def("note_none", "Not yok", listOf("Henüz kayıtlı bir notunuz yok {h}.")),
        Def("alarm_set", "Alarm kuruldu", listOf("Alarm {saat} için kuruldu {h}.")),
        Def("media", "Müzik komutu", listOf("Tamam {h}.")),
        Def("learned", "Uygulama adı öğrenildi", listOf("Tamam, {ad} uygulamasını artık \"{metin}\" diye de tanıyacağım {h}.")),
        Def("need_accessibility", "Erişilebilirlik izni yok", listOf("Bunun için erişilebilirlik iznini açmanız gerekiyor {h}.")),
        Def("need_permission", "İzin yok", listOf("Bunun için gerekli izni vermediniz {h}.")),
        Def("screen_off", "Ekran kapalıyken ekran görüntüsü", listOf("Ekran kilitliyken ekran görüntüsü alamam {h}.")),
        Def("unknown", "Komut anlaşılmadı", listOf("Anlayamadım {h}.", "Bunu yapamadım {h}.")),
        Def("error", "Hata", listOf("Bir sorun oluştu {h}."))
    )

    private val byKey = defs.associateBy { it.key }

    fun variants(key: String): List<String> {
        val o = Prefs.responseOverride(key)
        val list = if (o != null) o.lines().map { it.trim() }.filter { it.isNotEmpty() } else emptyList()
        return if (list.isNotEmpty()) list else (byKey[key]?.defaults ?: listOf("Tamam {h}."))
    }

    fun pick(key: String, vars: Map<String, String> = emptyMap()): String {
        val all = variants(key)
        var t = all[Random.nextInt(all.size)]
        t = t.replace("{h}", Prefs.hitap)
        for ((k, v) in vars) t = t.replace("{$k}", v)
        return t.replace(Regex("\\s+"), " ").replace(" .", ".").replace(" ?", "?").trim()
    }
}
