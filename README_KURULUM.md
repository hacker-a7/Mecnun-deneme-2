# Mecnun - GitHub ile APK

## Doğru klasör yapısı (depo kökünde şunlar görünmeli)
    .github/workflows/build.yml
    app/
    build.gradle.kts
    settings.gradle.kts
    gradle.properties
    README_KURULUM.md

## Adımlar
1. Yeni depo aç (Private önerilir).
2. Bu arşivi aç; içindeki dosya ve klasörleri (arşivin kendisini veya dışına ekstra klasör değil) deponun köküne yükle.
   "Add file > Upload files" ile sürükle > Commit changes.
3. `.github` klasörü yüklenmediyse (gizli klasör): "Add file > Create new file", ad kısmına
   `.github/workflows/build.yml` yaz, `workflow_yedek_build.yml` içeriğini yapıştır, Commit.
4. Actions sekmesi > "APK derle" > Run workflow (yükleme sonrası kendiliğinden de başlar). 5-10 dk.
5. Bitince Releases > Mecnun.apk indir (ya da çalışmanın altındaki Artifacts > Mecnun-apk).
6. Telefona kur, Mecnun'u aç, Kurulum listesindeki "Ver" düğmelerine bas.
