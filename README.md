# Lao to Thai Voice (Android) / ສຽງລາວເປັນໄທ

ເວີສັນ Android ຂອງ addon NVDA "LaoToThai". ມັນເປັນ **ເຄື່ອງຈັກສຽງ (Text-to-speech engine)** ຕົວກາງ:
TalkBack ສົ່ງຂໍ້ຄວາມມາ → ແອັບນີ້ແປງຕົວອັກສອນລາວເປັນໄທ (ໃຊ້ຕາຕະລາງ `lao_thai.tsv` ອັນດຽວກັນກັບ addon,
`app/src/main/assets/lao_thai.tsv`) → ສົ່ງໃຫ້ເຄື່ອງຈັກສຽງຈິງ (ເຊັ່ນ Google) ອ່ານດ້ວຍສຽງໄທ.

## ສ້າງ APK (ບໍ່ຕ້ອງຕິດຕັ້ງ Android Studio)
1. ສ້າງ repository ໃໝ່ໃນ github.com (ຟຣີ) ແລ້ວອັບໂຫຼດເນື້ອໃນໂຟນເດີນີ້ທັງໝົດ (ລວມໂຟນເດີ `.github`).
2. ໄປແຖບ **Actions** → ເລືອກ **Build APK** → **Run workflow** (ຫຼືລໍຖ້າມັນເລີ່ມເອງຫຼັງອັບໂຫຼດ).
3. ເມື່ອສຳເລັດ (ປະມານ 3-5 ນາທີ) ເປີດການແລ່ນນັ້ນ ແລ້ວດາວໂຫຼດ **LaoToThaiVoice-apk** ຈາກສ່ວນ Artifacts → ແຕກ ZIP ຈະໄດ້ `app-debug.apk`.

ຫຼືຖ້າມີ Android Studio / JDK 17: ແລ່ນ `./gradlew assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`.

## ຕິດຕັ້ງ ແລະໃຊ້
1. ຕິດຕັ້ງ `app-debug.apk` (ອະນຸຍາດ "ຕິດຕັ້ງຈາກແຫຼ່ງທີ່ບໍ່ຮູ້ຈັກ" ຖ້າຖືກຖາມ).
2. ຕ້ອງມີ **Speech Services by Google** ແລະດາວໂຫຼດສຽງ **ໄທ (Thai)** ໄວ້ແລ້ວ
   (Settings → ການເຂົ້າເຖິງ/Accessibility → Text-to-speech output → Google → Install voice data → Thai).
3. ເປີດ TalkBack settings → Text-to-speech → Preferred engine → ເລືອກ **Lao to Thai Voice**
   (ຫຼື Settings → System → Languages → Text-to-speech output → Preferred engine).
4. ເປີດແອັບ "Lao to Thai Voice" ກົດ **ທົດລອງອ່ານພາສາລາວ** ເພື່ອທົດສອບ.

ໝາຍເຫດ: ການອອກສຽງແມ່ນ "ລາວສຳນຽງໄທ" ຄືກັບ addon NVDA — ວັນນະຍຸດ ແລະບາງຄຳອາດບໍ່ຄືລາວແທ້.
ຂໍ້ຄວາມທີ່ບໍ່ແມ່ນລາວ (ອັງກິດ ແລະອື່ນໆ) ຈະຖືກສົ່ງຜ່ານໄປຫາເຄື່ອງຈັກຈິງໂດຍກົງ.

## ໂຄງສ້າງ
- `LaoToThai.java` — ຕົວແປງ (port ຂອງ engine.py; ທົດສອບແລ້ວວ່າໄດ້ຜົນຕົງກັນ 3,860 ກໍລະນີ)
- `LaoThaiTtsService.java` — ເຄື່ອງຈັກສຽງຕົວກາງ
- `SettingsActivity.java` — ໜ້າຕັ້ງຄ່າ (ເລືອກເຄື່ອງຈັກຈິງ, ເປີດ/ປິດການແປງ, ທົດລອງ)
