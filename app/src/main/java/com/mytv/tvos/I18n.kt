package com.mytv.tvos

/** English / Arabic for the launcher's own texts. English is the source text; Arabic comes from this table. */
object L {
    @Volatile var ar = false

    private val d = mapOf(
        "Settings" to "الإعدادات", "General" to "عام", "Home Screen" to "الشاشة الرئيسية", "Prayer and Adhan" to "الأذان ومواقيت الصلاة",
        "Screen Saver" to "شاشة التوقف", "Video and Audio" to "الفيديو والصوت", "Remotes and Devices" to "الريموت والأجهزة",
        "Accessibility" to "سهولة الاستخدام", "Apps" to "التطبيقات", "Network" to "الشبكة", "System" to "النظام",
        "Appearance" to "المظهر", "Language and Region" to "اللغة والمنطقة", "Date and Time" to "التاريخ والوقت", "Storage" to "التخزين", "About" to "حول",
        "Language" to "اللغة", "English" to "English", "Time Format" to "صيغة الوقت", "24-Hour" to "24 ساعة", "12-Hour" to "12 ساعة", "System" to "النظام",
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
        "No city found (check internet)" to "ما لقيت مدينة (تأكد من الإنترنت)", "Could not start the Adhan" to "تعذر تشغيل الأذان", "Not available on this device" to "غير متوفر على هذا الجهاز",
        "Open" to "فتح", "Rename" to "إعادة تسمية", "Delete Folder" to "حذف المجلد", "Move" to "نقل", "Remove from Dock" to "إزالة من الدوك", "Add to Dock" to "إضافة للدوك",
        "The dock is full" to "الدوك ممتلئ", "New Folder with This App" to "مجلد جديد بهذا التطبيق", "Remove from Folder" to "إزالة من المجلد", "Hide" to "إخفاء",
        "App Info" to "معلومات التطبيق", "Uninstall" to "إلغاء التثبيت", "Rename Folder" to "تسمية المجلد", "Not available" to "غير متوفر", "Folder" to "مجلد",
        "Sleep" to "نوم", "Wi-Fi" to "واي فاي", "Connected" to "متصل", "Not connected" to "غير متصل", "Ethernet" to "إيثرنت", "Do Not Disturb" to "عدم الإزعاج",
        "On" to "مفعّل", "Off" to "متوقف", "Sleep Timer" to "مؤقت النوم", "Audio" to "الصوت", "Output" to "المخرج", "Search Apps" to "بحث عن تطبيق",
        "Type an app name" to "اكتب اسم التطبيق", "No apps found" to "ما في تطبيقات", "Control Center" to "مركز التحكم",
        "Today" to "اليوم", "Tomorrow" to "بكرة", "in" to "بعد", "h" to "س", "m" to "د",
        "Clear" to "صافي", "Partly cloudy" to "غائم جزئياً", "Cloudy" to "غائم", "Fog" to "ضباب", "Rain" to "مطر", "Snow" to "ثلج", "Thunderstorm" to "عاصفة رعدية",
        "Loading…" to "جاري التحميل…", "Prayer Times" to "مواقيت الصلاة", "Weather" to "الطقس"
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
