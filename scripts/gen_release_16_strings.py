#!/usr/bin/env python3
"""Najwa 1.6 interface strings in all seven languages (ar, en, id, ur, bn, hi, tr).

    python scripts/gen_release_16_strings.py app/src/main/res
"""
import os
import sys
from xml.sax.saxutils import escape

LANGS = ["", "en", "in", "ur", "bn", "hi", "tr"]

S = {
# ── General ──
"action_cancel": ["إلغاء","Cancel","Batal","منسوخ کریں","বাতিল","रद्द करें","İptal"],
"action_save": ["حفظ","Save","Simpan","محفوظ کریں","সংরক্ষণ","सहेजें","Kaydet"],
"streak_days": ["أيام متتالية: %1$s","Streak: %1$s","%1$s hari berturut-turut","مسلسل %1$s دن","টানা %1$s দিন","लगातार %1$s दिन","Üst üste %1$s gün"],
# ── Settings home and sections ──
"settings_title": ["الإعدادات","Settings","Pengaturan","ترتیبات","সেটিংস","सेटिंग्स","Ayarlar"],
"settings_subtitle": ["كل شيء في مكانه، وكل تفصيل على بُعد لمسة","Everything in its place, every detail one tap away","Semua di tempatnya, setiap detail sekali ketuk","ہر چیز اپنی جگہ، ہر تفصیل ایک ٹیپ پر","সবকিছু নিজের জায়গায়, প্রতিটি খুঁটিনাটি এক চাপে","सब कुछ अपनी जगह, हर बारीकी एक टैप पर","Her şey yerli yerinde, her ayrıntı bir dokunuşta"],
"quick_silence": ["الصمت","Silence","Senyap","خاموشی","নীরবতা","साइलेंस","Sessiz"],
"section_times": ["المواقيت","Prayer times","Waktu salat","اوقاتِ نماز","নামাজের সময়","नमाज़ के समय","Vakitler"],
"section_adhan": ["الأذان والصوت","Adhan and sound","Azan dan suara","اذان اور آواز","আজান ও শব্দ","अज़ान और आवाज़","Ezan ve ses"],
"section_adhan_summary": ["صوت الأذان، أذان الفجر، التدرّج والإيقاف","Adhan voice, Fajr adhan, fade-in and stopping","Suara azan, azan Subuh, naik perlahan, berhenti","اذان کی آواز، فجر کی اذان، آہستہ آغاز","আজানের কণ্ঠ, ফজরের আজান, ধীরে শুরু","अज़ान की आवाज़, फ़ज्र की अज़ान, धीमी शुरुआत","Ezan sesi, sabah ezanı, yavaş açılma"],
"section_alerts": ["التنبيهات","Alerts","Pengingat","الرٹس","অ্যালার্ট","अलर्ट","Uyarılar"],
"section_alerts_summary": ["أذان %1$d · إقامة %2$d","Adhan %1$d · Iqama %2$d","Azan %1$d · Iqamah %2$d","اذان %1$d · اقامت %2$d","আজান %1$d · ইকামত %2$d","अज़ान %1$d · इक़ामत %2$d","Ezan %1$d · Kamet %2$d"],
"section_mosque": ["المسجد والإقامة","Mosque and iqama","Masjid dan iqamah","مسجد اور اقامت","মসজিদ ও ইকামত","मस्जिद और इक़ामत","Cami ve kamet"],
"section_mosque_summary": ["مساجدك، دقائق الإقامة والعدّاد","Your mosques, iqama minutes and countdown","Masjid Anda, menit iqamah, hitung mundur","آپ کی مساجد، اقامت کے منٹ اور گنتی","আপনার মসজিদ, ইকামতের মিনিট ও কাউন্টডাউন","आपकी मस्जिदें, इक़ामत के मिनट और गिनती","Camileriniz, kamet dakikaları ve sayaç"],
"section_worship": ["العبادات والتذكيرات","Worship and reminders","Ibadah dan pengingat","عبادات اور یاد دہانیاں","ইবাদত ও রিমাইন্ডার","इबादत और रिमाइंडर","İbadet ve hatırlatmalar"],
"section_worship_summary": ["رمضان، السنن، دعاء اليوم والسجل","Ramadan, sunnah, dua of the day and the log","Ramadan, sunah, doa harian, catatan","رمضان، سنتیں، آج کی دعا اور ریکارڈ","রমজান, সুন্নাহ, আজকের দোয়া ও রেকর্ড","रमज़ान, सुन्नत, आज की दुआ और रिकॉर्ड","Ramazan, sünnet, günün duası, kayıt"],
"section_display": ["المظهر واللغة","Look and language","Tampilan dan bahasa","ظاہری شکل اور زبان","চেহারা ও ভাষা","रूप और भाषा","Görünüm ve dil"],
"display_intro": ["السمة وصيغة الوقت والخط واللغة والويدجت","Theme, time format, text size, language and widgets","Tema, format waktu, ukuran teks, bahasa, widget","تھیم، وقت کی شکل، متن، زبان اور ویجٹ","থিম, সময়ের ধরন, লেখার আকার, ভাষা ও উইজেট","थीम, समय का प्रारूप, अक्षर, भाषा और विजेट","Tema, saat biçimi, yazı boyutu, dil ve widget'lar"],
"worship_intro": ["تذكيرات لطيفة حول صلاتك، وكلها اختيارية","Gentle reminders around your prayers, all optional","Pengingat lembut di sekitar salat, semuanya opsional","نماز کے گرد نرم یاد دہانیاں، سب اختیاری","নামাজ ঘিরে কোমল রিমাইন্ডার, সবই ঐচ্ছিক","नमाज़ के आसपास हल्के रिमाइंडर, सब वैकल्पिक","Namazlarınız etrafında nazik hatırlatmalar, hepsi isteğe bağlı"],
"worship_daily_title": ["كل يوم","Every day","Setiap hari","روزانہ","প্রতিদিন","हर दिन","Her gün"],
"worship_daily_help": ["دعاء اليوم وسجل صلواتك","The dua of the day and your prayer log","Doa harian dan catatan salat","آج کی دعا اور نمازوں کا ریکارڈ","আজকের দোয়া ও নামাজের রেকর্ড","आज की दुआ और नमाज़ रिकॉर्ड","Günün duası ve namaz kaydınız"],
"dua_setting": ["دعاء اليوم في الرئيسية","Dua of the day on home","Doa harian di beranda","ہوم پر آج کی دعا","হোমে আজকের দোয়া","होम पर आज की दुआ","Ana sayfada günün duası"],
"dua_setting_help": ["دعاء قصير من القرآن أو السنة يتغيّر كل يوم","A short dua from the Quran or Sunnah, new each day","Doa singkat dari Al-Qur'an atau sunah, berganti tiap hari","قرآن یا سنت سے مختصر دعا، روز نئی","কুরআন বা সুন্নাহর ছোট দোয়া, প্রতিদিন নতুন","क़ुरआन या सुन्नत की छोटी दुआ, रोज़ नई","Kur'an veya sünnetten kısa bir dua, her gün yeni"],
"dua_title": ["دعاء اليوم","Dua of the day","Doa hari ini","آج کی دعا","আজকের দোয়া","आज की दुआ","Günün duası"],
"iqama_section_title": ["الإقامة","Iqama","Iqamah","اقامت","ইকামত","इक़ामत","Kamet"],
"iqama_section_help": ["العدّاد بعد الأذان، والبطاقة الحيّة على شاشة القفل","The countdown after adhan, and the live card on the lock screen","Hitung mundur setelah azan dan kartu langsung di layar kunci","اذان کے بعد گنتی، اور لاک اسکرین پر لائیو کارڈ","আজানের পর কাউন্টডাউন ও লক স্ক্রিনে লাইভ কার্ড","अज़ान के बाद गिनती, और लॉक स्क्रीन पर लाइव कार्ड","Ezandan sonra sayaç ve kilit ekranında canlı kart"],
"data_title": ["البيانات والأتمتة","Data and automation","Data dan otomatisasi","ڈیٹا اور آٹومیشن","ডেটা ও অটোমেশন","डेटा और ऑटोमेशन","Veri ve otomasyon"],
"data_summary": ["الأتمتة وربط التطبيقات والساعة الذكية","Automation, app linking and smartwatches","Otomatisasi, tautan aplikasi dan jam pintar","آٹومیشن، ایپ ربط اور سمارٹ واچ","অটোমেশন, অ্যাপ সংযোগ ও স্মার্টওয়াচ","ऑटोमेशन, ऐप जोड़ना और स्मार्टवॉच","Otomasyon, uygulama bağlantısı ve akıllı saatler"],
"data_intro": ["احفظ إعداداتك، أو اربط نجوى بتطبيقات الأتمتة","Save your settings, or connect Najwa to automation apps","Simpan pengaturan, atau hubungkan Najwa ke aplikasi otomatisasi","ترتیبات محفوظ کریں یا نجویٰ کو آٹومیشن ایپس سے جوڑیں","সেটিংস সংরক্ষণ করুন বা নাজওয়াকে অটোমেশন অ্যাপে যুক্ত করুন","सेटिंग्स सहेजें या नजवा को ऑटोमेशन ऐप से जोड़ें","Ayarlarınızı kaydedin veya Najwa'yı otomasyon uygulamalarına bağlayın"],
# ── Tasbeeh ──
"tasbeeh_after_prayer": ["تسبيح ما بعد الصلاة","After-prayer tasbeeh","Tasbih setelah salat","نماز کے بعد تسبیح","নামাজের পর তাসবিহ","नमाज़ के बाद तस्बीह","Namaz sonrası tesbih"],
"tasbeeh_of": ["من %1$s","of %1$s","dari %1$s","%1$s میں سے","%1$s-এর মধ্যে","%1$s में से","%1$s içinden"],
"tasbeeh_round_done": ["أحسنت، اكتملت الدورة %1$s","Well done: round %1$s complete","Bagus: putaran %1$s selesai","شاباش: دور %1$s مکمل","চমৎকার: %1$s নম্বর রাউন্ড শেষ","शाबाश: दौर %1$s पूरा","Tebrikler: %1$s. tur tamam"],
"tasbeeh_sequence_done": ["تقبّل الله، أتممت تسبيح ما بعد الصلاة","May Allah accept it: after-prayer tasbeeh complete","Semoga Allah menerima: tasbih setelah salat selesai","اللہ قبول کرے: نماز کے بعد کی تسبیح مکمل","আল্লাহ কবুল করুন: নামাজের পর তাসবিহ সম্পন্ন","अल्लाह क़बूल करे: नमाज़ के बाद की तस्बीह पूरी","Allah kabul etsin: namaz sonrası tesbih tamam"],
"tasbeeh_stats": ["اليوم %1$s · الأسبوع %2$s · المجموع %3$s","Today %1$s · Week %2$s · Total %3$s","Hari ini %1$s · Minggu %2$s · Total %3$s","آج %1$s · ہفتہ %2$s · کل %3$s","আজ %1$s · সপ্তাহ %2$s · মোট %3$s","आज %1$s · हफ़्ता %2$s · कुल %3$s","Bugün %1$s · Hafta %2$s · Toplam %3$s"],
"tasbeeh_undo": ["تراجع","Undo","Urungkan","واپس","পূর্বাবস্থা","पीछे","Geri al"],
"tasbeeh_haptics": ["الاهتزاز","Vibration","Getar","وائبریشن","কম্পন","कंपन","Titreşim"],
"tasbeeh_haptics_help": ["نبضة خفيفة مع كل تسبيحة، وأقوى عند الإكمال","A light tap each count, stronger when complete","Getar ringan tiap hitungan, lebih kuat saat selesai","ہر گنتی پر ہلکی، مکمل ہونے پر تیز","প্রতি গণনায় হালকা, শেষে জোরালো","हर गिनती पर हल्का, पूरा होने पर तेज़","Her sayımda hafif, tamamlanınca güçlü"],
"tasbeeh_volume_short": ["زر الصوت","Volume keys","Tombol volume","والیوم بٹن","ভলিউম বোতাম","वॉल्यूम बटन","Ses tuşları"],
"tasbeeh_volume_keys": ["العدّ بزر الصوت","Count with the volume keys","Hitung dengan tombol volume","والیوم بٹن سے گنیں","ভলিউম বোতামে গুনুন","वॉल्यूम बटन से गिनें","Ses tuşlarıyla say"],
"tasbeeh_volume_keys_help": ["سبّح دون النظر إلى الشاشة ما دامت المسبحة مفتوحة","Count without looking while the tasbeeh is open","Hitung tanpa melihat layar selama tasbih terbuka","تسبیح کھلی ہو تو اسکرین دیکھے بغیر گنیں","তাসবিহ খোলা থাকলে না দেখেই গুনুন","तस्बीह खुली हो तो बिना देखे गिनें","Tesbih açıkken ekrana bakmadan sayın"],
"tasbeeh_keep_awake": ["إبقاء الشاشة مضاءة","Keep the screen on","Biarkan layar menyala","اسکرین روشن رکھیں","স্ক্রিন চালু রাখুন","स्क्रीन चालू रखें","Ekranı açık tut"],
"tasbeeh_keep_awake_help": ["لا تنطفئ الشاشة أثناء التسبيح","The screen won't turn off while you count","Layar tidak mati saat Anda menghitung","گنتی کے دوران اسکرین بند نہیں ہوگی","গণনার সময় স্ক্রিন বন্ধ হবে না","गिनते समय स्क्रीन बंद नहीं होगी","Sayarken ekran kapanmaz"],
"tasbeeh_options": ["خيارات المسبحة","Tasbeeh options","Opsi tasbih","تسبیح کے اختیارات","তাসবিহের বিকল্প","तस्बीह के विकल्प","Tesbih seçenekleri"],
"tasbeeh_options_summary": ["زر الصوت، الشاشة، الاهتزاز، تصفير المجموع","Volume keys, screen, vibration, reset total","Tombol volume, layar, getar, atur ulang total","والیوم، اسکرین، وائبریشن، کل صفر","ভলিউম, স্ক্রিন, কম্পন, মোট শূন্য","वॉल्यूम, स्क्रीन, कंपन, कुल शून्य","Ses tuşu, ekran, titreşim, toplamı sıfırla"],
"tasbeeh_widget_hint": ["أضف ويدجت المسبحة إلى الشاشة الرئيسية لتسبّح دون فتح التطبيق.","Add the tasbeeh widget to your home screen to count without opening the app.","Tambahkan widget tasbih ke layar utama untuk menghitung tanpa membuka aplikasi.","ایپ کھولے بغیر گننے کے لیے تسبیح ویجٹ ہوم اسکرین پر لگائیں۔","অ্যাপ না খুলে গুনতে হোম স্ক্রিনে তাসবিহ উইজেট যোগ করুন।","ऐप खोले बिना गिनने के लिए तस्बीह विजेट होम स्क्रीन पर जोड़ें।","Uygulamayı açmadan saymak için ana ekrana tesbih widget'ını ekleyin."],
"tasbeeh_reset_all_confirm": ["سيُمسح مجموع تسبيحاتك كله، ولا يمكن التراجع.","Your whole tasbeeh total will be cleared. This can't be undone.","Seluruh total tasbih akan dihapus dan tidak bisa dibatalkan.","آپ کی تسبیح کا کل مٹ جائے گا، واپس نہیں ہوگا۔","আপনার মোট তাসবিহ মুছে যাবে, ফেরানো যাবে না।","आपकी तस्बीह का कुल मिट जाएगा, वापस नहीं होगा।","Tüm tesbih toplamınız silinecek, geri alınamaz."],
# ── Adhkar ──
"adhkar_sleep": ["النوم","Sleep","Tidur","سونا","ঘুম","सोना","Uyku"],
"adhkar_waking": ["الاستيقاظ","Waking","Bangun","جاگنا","জাগা","जागना","Uyanış"],
"adhkar_mosque": ["المسجد","Mosque","Masjid","مسجد","মসজিদ","मस्जिद","Cami"],
"adhkar_done_title": ["أتممت أذكار %1$s","%1$s adhkar complete","Zikir %1$s selesai","%1$s کے اذکار مکمل","%1$s-এর যিকর সম্পন্ন","%1$s के अज़कार पूरे","%1$s zikirleri tamam"],
"adhkar_done_text": ["تقبّل الله منك","May Allah accept from you","Semoga Allah menerima","اللہ قبول فرمائے","আল্লাহ কবুল করুন","अल्लाह क़बूल फ़रमाए","Allah kabul etsin"],
"adhkar_smaller": ["تصغير الخط","Smaller text","Teks lebih kecil","چھوٹا متن","ছোট লেখা","छोटे अक्षर","Daha küçük yazı"],
"adhkar_larger": ["تكبير الخط","Larger text","Teks lebih besar","بڑا متن","বড় লেখা","बड़े अक्षर","Daha büyük yazı"],
# ── Quran ──
"quran_title": ["وِردي من القرآن","My Quran reading","Bacaan Al-Qur'an saya","میری تلاوت","আমার কুরআন পাঠ","मेरी क़ुरआन तिलावत","Kur'an okumam"],
"quran_tool": ["وِرد القرآن","Quran reading","Bacaan Qur'an","تلاوت","কুরআন পাঠ","तिलावत","Kur'an okuma"],
"quran_intro": ["سجّل ما تقرؤه كل يوم وتابع ختمتك. لا يحتوي التطبيق على نص المصحف؛ اقرأ من مصحفك.","Record what you read each day and follow your khatma. Najwa has no mushaf text; read from your own mushaf.","Catat bacaan harian dan pantau khatam Anda. Najwa tidak memuat teks mushaf; bacalah dari mushaf Anda.","روز کی تلاوت درج کریں اور ختم دیکھیں۔ ایپ میں مصحف کا متن نہیں؛ اپنے مصحف سے پڑھیں۔","প্রতিদিনের পাঠ লিখুন ও খতম দেখুন। অ্যাপে মুসহাফের লেখা নেই; নিজের মুসহাফ থেকে পড়ুন।","रोज़ की तिलावत दर्ज करें और ख़त्म देखें। ऐप में मुसहफ़ का पाठ नहीं; अपने मुसहफ़ से पढ़ें।","Her gün okuduğunuzu kaydedin, hatminizi izleyin. Uygulamada mushaf metni yok; kendi mushafınızdan okuyun."],
"quran_of_khatma": ["من الختمة","of the khatma","dari khatam","ختم میں سے","খতমের","ख़त्म का","hatmin"],
"quran_start": ["لم تبدأ بعد، سجّل أول صفحاتك","Not started yet: record your first pages","Belum mulai: catat halaman pertama","ابھی شروع نہیں: پہلے صفحات درج کریں","এখনো শুরু হয়নি: প্রথম পৃষ্ঠা লিখুন","अभी शुरू नहीं: पहले पन्ने दर्ज करें","Henüz başlamadınız: ilk sayfalarınızı kaydedin"],
"quran_start_short": ["ابدأ ختمتك","Start a khatma","Mulai khatam","ختم شروع کریں","খতম শুরু করুন","ख़त्म शुरू करें","Hatme başla"],
"quran_page_short": ["صفحة %1$d","Page %1$d","Hal. %1$d","صفحہ %1$d","পৃষ্ঠা %1$d","पन्ना %1$d","Sayfa %1$d"],
"quran_position": ["وصلت إلى صفحة %1$s · الجزء %2$s","You reached page %1$s · Juz %2$s","Sampai halaman %1$s · Juz %2$s","صفحہ %1$s تک · پارہ %2$s","পৃষ্ঠা %1$s পর্যন্ত · পারা %2$s","पन्ना %1$s तक · पारा %2$s","%1$s. sayfa · %2$s. cüz"],
"quran_khatmas": ["ختمات مكتملة: %1$s","Completed khatmas: %1$s","Khatam selesai: %1$s","مکمل ختم: %1$s","সম্পন্ন খতম: %1$s","पूरे ख़त्म: %1$s","Tamamlanan hatim: %1$s"],
"quran_today_title": ["وِرد اليوم","Today's reading","Bacaan hari ini","آج کی تلاوت","আজকের পাঠ","आज की तिलावत","Bugünkü okuma"],
"quran_today": ["قرأت %1$s من %2$s صفحات","You read %1$s of %2$s pages","Dibaca %1$s dari %2$s halaman","%2$s میں سے %1$s صفحات پڑھے","%2$s-এর মধ্যে %1$s পৃষ্ঠা পড়েছেন","%2$s में से %1$s पन्ने पढ़े","%2$s sayfanın %1$s tanesini okudunuz"],
"quran_add": ["سجّل ما قرأته الآن","Record what you just read","Catat yang baru dibaca","جو ابھی پڑھا درج کریں","এইমাত্র যা পড়লেন লিখুন","जो अभी पढ़ा दर्ज करें","Az önce okuduğunuzu kaydedin"],
"quran_add_juz": ["+ جزء كامل","+ a whole juz","+ satu juz","+ پورا پارہ","+ পুরো পারা","+ पूरा पारा","+ bir cüz"],
"quran_set_page": ["وصلت إلى صفحة…","I reached page…","Saya sampai halaman…","میں صفحہ… تک پہنچا","আমি পৃষ্ঠা… পর্যন্ত","मैं पन्ना… तक पहुँचा","Şu sayfadayım…"],
"quran_set_page_title": ["أين وصلت؟","Where did you reach?","Sampai di mana?","کہاں تک پہنچے؟","কোথায় পৌঁছালেন?","कहाँ तक पहुँचे?","Nereye kadar geldiniz?"],
"quran_page_label": ["رقم الصفحة (0 إلى 604)","Page number (0 to 604)","Nomor halaman (0–604)","صفحہ نمبر (0 سے 604)","পৃষ্ঠা নম্বর (0–604)","पन्ना संख्या (0–604)","Sayfa numarası (0–604)"],
"quran_week": ["هذا الأسبوع: %1$s صفحة","This week: %1$s pages","Minggu ini: %1$s halaman","اس ہفتے: %1$s صفحات","এই সপ্তাহে: %1$s পৃষ্ঠা","इस हफ़्ते: %1$s पन्ने","Bu hafta: %1$s sayfa"],
"quran_goal": ["الوِرد اليومي","Daily goal","Target harian","روزانہ ہدف","দৈনিক লক্ষ্য","रोज़ का लक्ष्य","Günlük hedef"],
"quran_goal_help": ["عشرون صفحة يوميًا تعني ختمة كل شهر تقريبًا","Twenty pages a day is about one khatma a month","Dua puluh halaman sehari kira-kira satu khatam sebulan","روزانہ بیس صفحے یعنی تقریباً ماہانہ ایک ختم","দিনে বিশ পৃষ্ঠা মানে প্রায় মাসে এক খতম","रोज़ बीस पन्ने यानी लगभग हर महीने एक ख़त्म","Günde yirmi sayfa ayda yaklaşık bir hatimdir"],
"quran_pages": ["%1$s صفحات","%1$s pages","%1$s halaman","%1$s صفحات","%1$s পৃষ্ঠা","%1$s पन्ने","%1$s sayfa"],
"quran_estimate": ["بهذا الوِرد تختم بعد نحو %1$s يومًا","At this pace you finish in about %1$s days","Dengan laju ini khatam sekitar %1$s hari lagi","اس رفتار سے تقریباً %1$s دن میں ختم","এই গতিতে প্রায় %1$s দিনে খতম","इस रफ़्तार से लगभग %1$s दिन में ख़त्म","Bu hızla yaklaşık %1$s günde biter"],
"quran_goal_done": ["أتممت وِرد اليوم، بارك الله فيك","Today's goal done, may Allah bless you","Target hari ini tercapai, semoga berkah","آج کا ہدف مکمل، اللہ برکت دے","আজকের লক্ষ্য পূর্ণ, আল্লাহ বরকত দিন","आज का लक्ष्य पूरा, अल्लाह बरकत दे","Bugünkü hedef tamam, Allah bereket versin"],
"quran_khatma_done": ["مبارك! أتممت ختمة كاملة","Congratulations: a whole khatma complete","Selamat: satu khatam selesai","مبارک ہو: پورا ختم مکمل","অভিনন্দন: পূর্ণ খতম সম্পন্ন","मुबारक: पूरा ख़त्म हुआ","Tebrikler: bir hatim tamamlandı"],
# ── Prayer log ──
"log_today": ["صلوات اليوم","Today's prayers","Salat hari ini","آج کی نمازیں","আজকের নামাজ","आज की नमाज़ें","Bugünün namazları"],
"log_how": ["كيف صلّيت %1$s؟","How did you pray %1$s?","Bagaimana salat %1$s Anda?","%1$s کیسے پڑھی؟","%1$s কীভাবে পড়েছেন?","%1$s कैसे पढ़ी?","%1$s namazını nasıl kıldınız?"],
"log_status_jamaah": ["في جماعة","In congregation","Berjamaah","باجماعت","জামাতে","जमाअत से","Cemaatle"],
"log_status_on_time": ["في وقتها","On time","Tepat waktu","وقت پر","সময়মতো","वक़्त पर","Vaktinde"],
"log_status_late": ["متأخرة","Late","Terlambat","دیر سے","দেরিতে","देर से","Geç"],
"log_status_missed": ["فاتتني","Missed","Terlewat","چھوٹ گئی","ছুটে গেছে","छूट गई","Kaçırdım"],
"log_status_none": ["لم تُسجَّل","Not marked","Belum dicatat","درج نہیں","লেখা হয়নি","दर्ज नहीं","İşaretlenmedi"],
"log_clear": ["مسح","Clear","Hapus","صاف کریں","মুছুন","हटाएँ","Temizle"],
"log_sunnah_rawatib": ["السنن الرواتب","Sunnah rawatib","Sunah rawatib","سنتِ مؤکدہ","সুন্নাতে রাতিবা","सुन्नते मुअक्कदा","Revatib sünnetleri"],
"log_sunnah_duha": ["الضحى","Duha","Duha","چاشت","দুহা","चाश्त","Kuşluk"],
"log_sunnah_witr": ["الوتر","Witr","Witir","وتر","বিতর","वित्र","Vitir"],
"log_sunnah_qiyam": ["قيام الليل","Night prayer","Qiyamul lail","قیام اللیل","কিয়ামুল লাইল","क़ियामुल लैल","Gece namazı"],
"log_jamaah_month": ["جماعة في ٣٠ يومًا","In congregation, 30 days","Berjamaah, 30 hari","30 دن میں باجماعت","৩০ দিনে জামাতে","30 दिन में जमाअत","30 günde cemaat"],
"log_insight_weakest": ["أكثر صلاة تحتاج عنايتك: %1$s (%2$d من ١٤ يومًا)","The prayer that needs care: %1$s (%2$d of 14 days)","Salat yang perlu perhatian: %1$s (%2$d dari 14 hari)","توجہ طلب نماز: %1$s (14 میں سے %2$d دن)","যত্ন দরকার: %1$s (১৪ দিনে %2$d)","ध्यान चाहिए: %1$s (14 में से %2$d दिन)","Özen isteyen namaz: %1$s (14 günün %2$d'i)"],
"log_insight_fajr": ["الفجر: %1$d أيام متتالية، ثبّتك الله","Fajr: %1$d days in a row, keep going","Subuh: %1$d hari berturut-turut, teruskan","فجر: مسلسل %1$d دن، جاری رکھیں","ফজর: টানা %1$d দিন, চালিয়ে যান","फ़ज्र: लगातार %1$d दिन, जारी रखें","Sabah: üst üste %1$d gün, devam"],
"log_map_title": ["خمسة أسابيع","Five weeks","Lima minggu","پانچ ہفتے","পাঁচ সপ্তাহ","पाँच हफ़्ते","Beş hafta"],
"log_map_help": ["كلما اشتدّ اللون اكتمل اليوم. اضغط يومًا لتعديله.","The darker the day, the more complete. Tap a day to edit it.","Semakin gelap, semakin lengkap. Ketuk hari untuk mengubah.","جتنا گہرا رنگ، اتنا مکمل دن۔ ترمیم کے لیے دن دبائیں۔","রং যত গাঢ়, দিন তত পূর্ণ। সম্পাদনায় দিন চাপুন।","रंग जितना गहरा, दिन उतना पूरा। बदलने के लिए दिन दबाएँ।","Renk koyulaştıkça gün tamamlanır. Düzenlemek için güne dokunun."],
"log_by_prayer": ["كل صلاة في ٣٠ يومًا","Each prayer over 30 days","Tiap salat dalam 30 hari","30 دن میں ہر نماز","৩০ দিনে প্রতিটি নামাজ","30 दिन में हर नमाज़","30 günde her namaz"],
"log_milestones": ["إنجازات","Milestones","Pencapaian","سنگِ میل","অর্জন","उपलब्धियाँ","Kilometre taşları"],
"log_best": ["أطول سلسلة أيام مكتملة: %1$d","Longest run of complete days: %1$d","Rangkaian hari lengkap terpanjang: %1$d","مکمل دنوں کا طویل ترین سلسلہ: %1$d","পূর্ণ দিনের দীর্ঘতম ধারা: %1$d","पूरे दिनों की सबसे लंबी लड़ी: %1$d","En uzun tam gün serisi: %1$d"],
"log_days": ["يومًا","days","hari","دن","দিন","दिन","gün"],
"log_options": ["خيارات السجل","Log options","Opsi catatan","ریکارڈ کے اختیارات","রেকর্ডের বিকল্প","रिकॉर्ड के विकल्प","Kayıt seçenekleri"],
"log_check_setting": ["هل صلّيت؟ قبل خروج الوقت","Have you prayed? before time runs out","Sudah salat? sebelum waktu habis","کیا نماز پڑھی؟ وقت ختم ہونے سے پہلے","নামাজ পড়েছেন? সময় শেষের আগে","क्या नमाज़ पढ़ी? वक़्त ख़त्म होने से पहले","Kıldınız mı? vakit çıkmadan"],
"log_check_setting_help": ["تذكير هادئ قبل نهاية وقت الصلاة إن لم تسجّلها","A quiet reminder before a prayer's time ends, if it isn't marked","Pengingat tenang sebelum waktu salat habis jika belum dicatat","درج نہ ہو تو وقت ختم ہونے سے پہلے ہلکی یاد دہانی","লেখা না থাকলে সময় শেষের আগে শান্ত রিমাইন্ডার","दर्ज न हो तो वक़्त ख़त्म होने से पहले हल्का रिमाइंडर","İşaretlenmemişse vakit bitmeden sessiz hatırlatma"],
"log_check_title": ["هل صلّيت %1$s؟","Have you prayed %1$s?","Sudah salat %1$s?","کیا %1$s پڑھ لی؟","%1$s পড়েছেন?","क्या %1$s पढ़ ली?","%1$s namazını kıldınız mı?"],
"log_check_text": ["يقترب خروج وقت %1$s. إن صلّيت فاضغط «صلّيت».","The time for %1$s is ending soon. If you've prayed, tap Prayed.","Waktu %1$s segera habis. Jika sudah, ketuk Sudah salat.","%1$s کا وقت ختم ہونے کو ہے۔ پڑھ لی ہو تو «پڑھ لی» دبائیں۔","%1$s-এর সময় শেষ হয়ে আসছে। পড়ে থাকলে «পড়েছি» চাপুন।","%1$s का वक़्त ख़त्म होने वाला है। पढ़ ली हो तो «पढ़ ली» दबाएँ।","%1$s vakti çıkmak üzere. Kıldıysanız Kıldım'a dokunun."],
"log_check_fajr_text": ["تبقّى قليل على الشروق. إن صلّيت %1$s فاضغط «صلّيت».","Sunrise is near. If you've prayed %1$s, tap Prayed.","Matahari hampir terbit. Jika sudah salat %1$s, ketuk Sudah salat.","طلوعِ آفتاب قریب ہے۔ %1$s پڑھ لی ہو تو «پڑھ لی» دبائیں۔","সূর্যোদয় কাছে। %1$s পড়ে থাকলে «পড়েছি» চাপুন।","सूर्योदय क़रीब है। %1$s पढ़ ली हो तो «पढ़ ली» दबाएँ।","Güneş doğmak üzere. %1$s kıldıysanız Kıldım'a dokunun."],
"log_widget_hint": ["أضف ويدجت «صلواتي اليوم» لتسجّل بلمسة: مرة صلّيت، ومرتين في جماعة.","Add the Today's prayers widget to log with a tap: once for prayed, twice for congregation.","Tambahkan widget Salat hari ini: ketuk sekali untuk salat, dua kali untuk berjamaah.","«آج کی نمازیں» ویجٹ لگائیں: ایک ٹیپ پڑھ لی، دو ٹیپ باجماعت۔","«আজকের নামাজ» উইজেট যোগ করুন: এক চাপে পড়েছি, দুই চাপে জামাতে।","«आज की नमाज़ें» विजेट जोड़ें: एक टैप पढ़ ली, दो टैप जमाअत।","Bugünün namazları widget'ını ekleyin: bir dokunuş kıldım, iki dokunuş cemaat."],
# ── Widgets and shortcuts ──
"widget_log_label": ["صلواتي اليوم","Today's prayers","Salat hari ini","آج کی نمازیں","আজকের নামাজ","आज की नमाज़ें","Bugünün namazları"],
"widget_log_title": ["صلواتي اليوم","Today's prayers","Salat hari ini","آج کی نمازیں","আজকের নামাজ","आज की नमाज़ें","Bugünün namazları"],
"widget_log_description": ["سجّل صلواتك من الشاشة الرئيسية بلمسة","Log your prayers from the home screen with a tap","Catat salat dari layar utama dengan sekali ketuk","ہوم اسکرین سے ایک ٹیپ میں نمازیں درج کریں","হোম স্ক্রিন থেকে এক চাপে নামাজ লিখুন","होम स्क्रीन से एक टैप में नमाज़ें दर्ज करें","Namazlarınızı ana ekrandan tek dokunuşla kaydedin"],
"widget_log_hint": ["لمسة: صلّيت · لمستان: جماعة · ثلاث: مسح","Tap: prayed · twice: congregation · three: clear","Ketuk: salat · dua: jamaah · tiga: hapus","ایک: پڑھ لی · دو: جماعت · تین: صاف","এক: পড়েছি · দুই: জামাত · তিন: মুছুন","एक: पढ़ ली · दो: जमाअत · तीन: हटाएँ","Bir: kıldım · iki: cemaat · üç: temizle"],
"widget_log_complete": ["اكتمل يومك · متتالية %1$d","Day complete · %1$d in a row","Hari lengkap · %1$d berturut-turut","دن مکمل · مسلسل %1$d","দিন পূর্ণ · টানা %1$d","दिन पूरा · लगातार %1$d","Gün tamam · üst üste %1$d"],
"widget_log_disabled": ["فعّل سجل الصلوات من الإعدادات","Turn on the prayer log in settings","Aktifkan catatan salat di pengaturan","ترتیبات میں نمازوں کا ریکارڈ چالو کریں","সেটিংসে নামাজের রেকর্ড চালু করুন","सेटिंग्स में नमाज़ रिकॉर्ड चालू करें","Ayarlardan namaz kaydını açın"],
"widget_tasbeeh_label": ["المسبحة","Tasbeeh","Tasbih","تسبیح","তাসবিহ","तस्बीह","Tesbih"],
"widget_tasbeeh_description": ["عدّاد تسبيح تضغط عليه من الشاشة الرئيسية","A tasbeeh counter you tap on the home screen","Penghitung tasbih yang diketuk di layar utama","ہوم اسکرین پر ٹیپ ہونے والا تسبیح کاؤنٹر","হোম স্ক্রিনে চাপার তাসবিহ গণক","होम स्क्रीन पर टैप वाला तस्बीह काउंटर","Ana ekranda dokunarak sayılan tesbih"],
"widget_tasbeeh_open": ["فتح المسبحة","Open tasbeeh","Buka tasbih","تسبیح کھولیں","তাসবিহ খুলুন","तस्बीह खोलें","Tesbihi aç"],
"shortcut_tasbeeh_short": ["المسبحة","Tasbeeh","Tasbih","تسبیح","তাসবিহ","तस्बीह","Tesbih"],
"shortcut_tasbeeh_long": ["فتح المسبحة","Open the tasbeeh","Buka tasbih","تسبیح کھولیں","তাসবিহ খুলুন","तस्बीह खोलें","Tesbihi aç"],
"shortcut_log_short": ["سجل صلواتي","Prayer log","Catatan salat","نمازوں کا ریکارڈ","নামাজের রেকর্ড","नमाज़ रिकॉर्ड","Namaz kaydı"],
"shortcut_log_long": ["سجّل صلاتك","Log a prayer","Catat salat","نماز درج کریں","নামাজ লিখুন","नमाज़ दर्ज करें","Namaz kaydet"],
# ── What's new in 1.6 ──
"new16_1": ["إعدادات جديدة: ستة أقسام ملوّنة وكل خيار في مكان واحد","New settings: six coloured sections, each option in one place","Pengaturan baru: enam bagian berwarna, tiap opsi di satu tempat","نئی ترتیبات: چھ رنگین حصے، ہر اختیار ایک جگہ","নতুন সেটিংস: ছয়টি রঙিন বিভাগ, প্রতিটি বিকল্প এক জায়গায়","नई सेटिंग्स: छह रंगीन हिस्से, हर विकल्प एक जगह","Yeni ayarlar: altı renkli bölüm, her seçenek tek yerde"],
"new16_2": ["سجل صلوات أذكى: جماعة أو في وقتها، والسنن، وخريطة أسابيع، وإنجازات","A smarter prayer log: congregation or on time, sunnah, a weekly map, milestones","Catatan salat lebih cerdas: jamaah atau tepat waktu, sunah, peta mingguan, pencapaian","بہتر ریکارڈ: جماعت یا وقت پر، سنتیں، ہفتہ وار نقشہ، سنگِ میل","স্মার্ট রেকর্ড: জামাত বা সময়মতো, সুন্নাহ, সাপ্তাহিক মানচিত্র, অর্জন","बेहतर रिकॉर्ड: जमाअत या वक़्त पर, सुन्नत, हफ़्तों का नक्शा, उपलब्धियाँ","Daha akıllı kayıt: cemaat veya vaktinde, sünnet, haftalık harita, kilometre taşları"],
"new16_3": ["ويدجت «صلواتي اليوم» وتذكير «هل صلّيت؟» قبل خروج الوقت","A Today's prayers widget and a Have you prayed? reminder","Widget Salat hari ini dan pengingat Sudah salat?","«آج کی نمازیں» ویجٹ اور «کیا پڑھ لی؟» یاد دہانی","«আজকের নামাজ» উইজেট ও «পড়েছেন?» রিমাইন্ডার","«आज की नमाज़ें» विजेट और «क्या पढ़ ली?» रिमाइंडर","Bugünün namazları widget'ı ve Kıldınız mı? hatırlatması"],
"new16_4": ["مسبحة جديدة: تسبيح ما بعد الصلاة، والعدّ بزر الصوت، وويدجت","A new tasbeeh: after-prayer sequence, volume-key counting and a widget","Tasbih baru: urutan setelah salat, tombol volume, widget","نئی تسبیح: نماز کے بعد کا سلسلہ، والیوم بٹن، ویجٹ","নতুন তাসবিহ: নামাজের পরের ধারা, ভলিউম বোতাম, উইজেট","नई तस्बीह: नमाज़ के बाद का क्रम, वॉल्यूम बटन, विजेट","Yeni tesbih: namaz sonrası sıra, ses tuşu, widget"],
"new16_5": ["أذكار النوم والاستيقاظ والمسجد، وحجم خط قابل للتغيير","Sleep, waking and mosque adhkar, with adjustable text size","Zikir tidur, bangun, masjid, dengan ukuran teks","سونے، جاگنے اور مسجد کے اذکار، قابلِ تبدیل متن","ঘুম, জাগা ও মসজিদের যিকর, পরিবর্তনযোগ্য লেখা","सोने, जागने और मस्जिद के अज़कार, बदलने योग्य अक्षर","Uyku, uyanış ve cami zikirleri, ayarlanabilir yazı"],
"new16_6": ["وِرد القرآن اليومي، ودعاء اليوم في الرئيسية","Daily Quran reading tracker and a dua of the day on home","Pelacak bacaan Qur'an dan doa harian di beranda","روزانہ تلاوت کا ریکارڈ اور ہوم پر آج کی دعا","দৈনিক কুরআন পাঠ ও হোমে আজকের দোয়া","रोज़ की तिलावत और होम पर आज की दुआ","Günlük Kur'an takibi ve ana sayfada günün duası"],
}

def esc(s):
    s = escape(s).replace("'", "\\'").replace('"', '\\"')
    if s[:1] in "@?":
        s = "\\" + s
    return s

if __name__ == "__main__":
    root = sys.argv[1] if len(sys.argv) > 1 else "app/src/main/res"
    for i, lang in enumerate(LANGS):
        folder = os.path.join(root, "values" + (f"-{lang}" if lang else ""))
        lines = ['<?xml version="1.0" encoding="utf-8"?>', "<!-- Najwa 1.6: generated by scripts/gen_release_16_strings.py -->", "<resources>"]
        for key, values in S.items():
            assert len(values) == 7, key
            lines.append(f'    <string name="{key}">{esc(values[i])}</string>')
        lines.append("</resources>")
        with open(os.path.join(folder, "release_16_strings.xml"), "w", encoding="utf-8", newline="\n") as f:
            f.write("\n".join(lines) + "\n")
    print("wrote", len(S), "strings for", len(LANGS), "languages")
