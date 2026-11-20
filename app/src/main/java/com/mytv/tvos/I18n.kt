package com.mytv.tvos

/** English / Arabic for the launcher's own texts. English is the source text; Arabic comes from this table. */
object L {
    @Volatile var ar = false

    private val d = mapOf(
        "Settings" to "الإعدادات", "General" to "عام", "Home Screen" to "الشاشة الرئيسية", "Prayer and Adhan" to "الأذان ومواقيت الصلاة",
        "Screen Saver" to "شاشة التوقف", "Video and Audio" to "الفيديو والصوت", "Remotes and Devices" to "الريموت والأجهزة",
        "Accessibility" to "سهولة الاستخدام", "Apps" to "التطبيقات", "Network" to "الشبكة", "System" to "النظام",
        "Appearance" to "المظهر", "Language and Region" to "اللغة والمنطقة", "Date and Time" to "التاريخ والوقت", "Storage" to "التخزين", "About" to "حول",
        "Language" to "اللغة", "English" to "English", "Time Format" to "صيغة الوقت", "Numerals" to "الأرقام", "English (0-9)" to "إنجليزية (0-9)", "Arabic (٠-٩)" to "عربية (٠-٩)", "24-Hour" to "24 ساعة", "12-Hour" to "12 ساعة", "System" to "النظام",
        "Time Zone" to "المنطقة الزمنية", "Automatic" to "تلقائي", "Automatic (by city)" to "تلقائي (حسب المدينة)", "UTC+3 (Amman, summer time)" to "UTC+3 (عمّان، توقيت صيفي)",
        "Date Display" to "عرض التاريخ", "Gregorian and Hijri" to "ميلادي وهجري", "Gregorian" to "ميلادي", "Hijri" to "هجري",
        "Hijri Date Adjustment" to "تعديل التاريخ الهجري", "day" to "يوم",
        "Always Show App Names" to "إظهار أسماء التطبيقات دائماً", "Show the name under every app instead of only the focused one." to "إظهار الاسم تحت كل تطبيق وليس المحدد فقط.",
        "Reduce Motion" to "تقليل الحركة", "Turn off the shine and the wiggle in the move mode." to "إيقاف اللمعان والاهتزاز في وضع النقل.",
        "Wallpaper" to "الخلفية", "Show a picture behind the home screen." to "إظهار صورة خلف الشاشة الرئيسية.", "LAYOUT" to "الترتيب",
        "Reset Home Screen Layout" to "إعادة ترتيب الشاشة الرئيسية", "Unhide All Apps" to "إظهار كل التطبيقات المخفية",
        "Layout reset" to "تمت إعادة الترتيب", "All apps are visible again" to "كل التطبيقات ظاهرة من جديد",
        "Never" to "أبداً", "After" to "بعد", "minutes" to "دقائق", "min" to "د",
        "Name" to "الاسم", "Version" to "الإصدار", "Model" to "الموديل", "Android" to "أندرويد",
        "Play Adhan Automatically" to "تشغيل الأذان تلقائياً", "Play the Adhan at each prayer time that is switched on below." to "تشغيل الأذان عند كل صلاة مفعّلة بالأسفل.",
        "City" to "المدينة", "Calculation Method" to "طريقة الحساب", "Adhan Volume" to "صوت الأذان", "Test Adhan" to "تجربة الأذان", "Stop Adhan" to "إيقاف الأذان",
        "PRAYERS TO ANNOUNCE" to "الصلوات المفعّلة", "MANUAL CORRECTION (MINUTES)" to "تصحيح يدوي (بالدقائق)", "correction" to "تصحيح",
        "Fajr" to "الفجر", "Dhuhr" to "الظهر", "Asr" to "العصر", "Maghrib" to "المغرب", "Isha" to "العشاء",
        "Jordan (Ministry of Awqaf)" to "الأردن (وزارة الأوقاف)", "Muslim World League" to "رابطة العالم الإسلامي", "Umm al-Qura, Makkah" to "أم القرى، مكة",
        "Egyptian General Authority" to "الهيئة المصرية العامة", "Karachi" to "كراتشي", "ISNA (North America)" to "أمريكا الشمالية", "Diyanet, Turkey" to "ديانت، تركيا",
        "Search City" to "بحث عن مدينة", "City name" to "اسم المدينة", "Search" to "بحث", "Cancel" to "إلغاء", "OK" to "موافق", "Choose City" to "اختر المدينة",
        "No city found (check internet)" to "لم يتم العثور على المدينة (تحقق من الاتصال بالإنترنت)", "Could not start the Adhan" to "تعذّر تشغيل الأذان", "Not available on this device" to "غير متاح على هذا الجهاز",
        "Open" to "فتح", "Rename" to "إعادة تسمية", "Delete Folder" to "حذف المجلد", "Move" to "نقل", "Remove from Dock" to "إزالة من الشريط السفلي", "Add to Dock" to "إضافة إلى الشريط السفلي",
        "The dock is full" to "الشريط السفلي ممتلئ", "New Folder with This App" to "مجلد جديد بهذا التطبيق", "Remove from Folder" to "إزالة من المجلد", "Hide" to "إخفاء",
        "App Info" to "معلومات التطبيق", "Uninstall" to "إزالة التثبيت", "Rename Folder" to "تسمية المجلد", "Not available" to "غير متاح", "Folder" to "مجلد",
        "Sleep" to "نوم", "Wi-Fi" to "واي فاي", "Connected" to "متصل", "Not connected" to "غير متصل", "Ethernet" to "إيثرنت", "Do Not Disturb" to "عدم الإزعاج",
        "On" to "مفعّل", "Off" to "متوقف", "Sleep Timer" to "مؤقت النوم", "Audio" to "الصوت", "Output" to "المخرج", "Search Apps" to "بحث عن تطبيق",
        "Type an app name" to "اكتب اسم التطبيق", "No apps found" to "لا توجد تطبيقات مطابقة", "Control Center" to "مركز التحكم",
        "Today" to "اليوم", "Tomorrow" to "غداً", "in" to "بعد", "h" to "س", "m" to "د",
        "Clear" to "صافي", "Partly cloudy" to "غائم جزئياً", "Cloudy" to "غائم", "Fog" to "ضباب", "Rain" to "مطر", "Snow" to "ثلج", "Thunderstorm" to "عاصفة رعدية",
        "None" to "بدون", "Default" to "الافتراضية", "Choose from TV storage" to "اختيار من ذاكرة الجهاز", "Clock Style" to "شكل الساعة", "Clock Size" to "حجم الساعة",
        "Light" to "رفيع", "Regular" to "عادي", "Bold" to "عريض", "Medium" to "متوسط", "Large" to "كبير", "Extra Large" to "كبير جداً",
        "Up" to "للأعلى", "Choose a location" to "اختر المكان", "Cannot read this folder" to "ما بقدر أقرأ هالمجلد", "Internal storage" to "الذاكرة الداخلية",
        "App folder" to "مجلد التطبيق", "Choose a picture" to "اختر صورة", "Wallpaper set" to "تم تعيين الخلفية", "Smooth Icon Edges" to "حواف أيقونات ناعمة", "Anti-aliased rounded corners on the icons. Turn this off if your TV draws the icons wrongly." to "حواف مدوّرة ناعمة للأيقونات. أوقفها إذا التلفزيون رسم الأيقونات غلط.", "Loading…" to "جارٍ التحميل…", "Prayer Times" to "مواقيت الصلاة", "Weather" to "الطقس",
        "Home" to "الرئيسية", "Wallpapers" to "الخلفيات", "Clock" to "الساعة", "Style" to "الشكل", "Size" to "الحجم", "Font" to "الخط", "Color" to "اللون", "Position" to "المكان", "Seconds" to "الثواني", "Show Date" to "إظهار التاريخ", "Glass Look" to "مظهر زجاجي", "Save" to "حفظ", "Reset" to "إعادة الضبط", "Thin Digital" to "رقمية رفيعة", "Big Digital" to "رقمية كبيرة", "Analog with Hands" to "دائرية بعقارب", "Simple Analog" to "دائرية بسيطة", "Flip" to "Flip", "Minimal" to "بسيطة", "Glass Digital" to "رقمية زجاجية", "Thin" to "رفيع", "Serif" to "سيريف", "Condensed" to "ضيق", "White" to "أبيض", "Silver" to "فضي", "Gold" to "ذهبي", "Ice Blue" to "أزرق جليدي", "Mint" to "نعناعي", "Rose" to "وردي", "Orange" to "برتقالي", "Custom" to "مخصص", "Left" to "يسار", "Center" to "وسط", "Right" to "يمين", "Preview" to "معاينة", "Apply" to "تطبيق", "Choose with the system picker" to "اختيار عبر منتقي النظام", "This picture can't be opened" to "تعذّر فتح هذه الصورة", "Could not save the wallpaper" to "تعذّر حفظ الخلفية", "Refresh" to "تحديث", "Feels like" to "الإحساس", "Humidity" to "الرطوبة", "Wind" to "الرياح", "Not available (no internet)" to "غير متاح (لا يوجد اتصال بالإنترنت)", "Internet OK" to "الإنترنت متاح", "Connected, no internet" to "متصل بالشبكة دون اتصال بالإنترنت", "Checking internet…" to "جارٍ التحقق من الاتصال بالإنترنت…", "Connected (network name hidden)" to "متصل (اسم الشبكة غير متاح)", "Reset all corrections" to "إعادة ضبط جميع التصحيحات", "Original time (reset)" to "الوقت الأصلي (إعادة ضبط)", "DURING THE ADHAN" to "وقت الأذان", "Pause Playback at Adhan" to "إيقاف التشغيل عند الأذان", "Pauses the video or music that is playing when the Adhan starts, and resumes it afterwards. Some apps cannot be paused by another app." to "يوقف الفيديو أو الموسيقى عند بدء الأذان ويكملها بعده. بعض التطبيقات لا يمكن إيقافها من تطبيق آخر.",
        "Glass" to "الزجاج", "Glass Color" to "لون الزجاج", "Glass Opacity" to "شفافية الزجاج", "Glass Shade" to "تعتيم الزجاج", "Reset Glass" to "إعادة ضبط الزجاج", "Frost White" to "أبيض جليدي", "Graphite" to "رمادي غرافيت", "Lavender" to "بنفسجي فاتح", "Amber" to "عنبري", "Soft" to "ناعم", "Strong" to "قوي",
        "Good morning" to "صباح الخير", "Good afternoon" to "طاب يومك", "Good evening" to "مساء الخير", "Good night" to "تصبحون على خير", "Downloading…" to "جارٍ التنزيل…", "Download failed (check internet)" to "فشل التنزيل (تحقق من الاتصال بالإنترنت)", "Online wallpapers — press one to download it" to "خلفيات من الإنترنت — اضغط على واحدة لتحميلها",
        "High" to "العظمى", "Low" to "الصغرى", "Pressure" to "الضغط الجوي", "UV Index" to "مؤشر الأشعة فوق البنفسجية", "Sunrise" to "الشروق", "Sunset" to "الغروب", "Moderate" to "متوسط", "Very high" to "مرتفع جداً", "Extreme" to "شديد", "Next hours" to "الساعات القادمة", "5-day forecast" to "توقعات 5 أيام", "Sleep Screen" to "شاشة السكون", "Sleep Screen Clock" to "ساعة شاشة السكون", "START AFTER" to "البدء بعد", "Greeting" to "التحية", "Prayer times" to "مواقيت الصلاة", "Online wallpapers (4K) — press one to download it" to "خلفيات 4K من الإنترنت — اضغط على إحداها لتنزيلها",
    )

    fun t(s: String): String {
        if (!ar) return s
        d[s]?.let { return it }
        if (s.startsWith("Add to ")) return "إضافة إلى " + t(s.removePrefix("Add to "))
        if (s.startsWith("After ") && s.endsWith(" minutes")) return "بعد " + s.removePrefix("After ").removeSuffix(" minutes") + " دقيقة"
        if (s.endsWith(" min")) return s.removeSuffix(" min") + " د"
        if (s.endsWith(" day")) return s.removeSuffix(" day") + " يوم"
        if (s.endsWith(" correction")) return t(s.removeSuffix(" correction")) + " - تصحيح"
        return s
    }

    fun locale(): java.util.Locale = if (ar) java.util.Locale("ar") else java.util.Locale.ENGLISH
}
