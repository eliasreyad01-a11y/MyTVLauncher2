package com.mytv.launcher

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import java.io.File
import java.security.MessageDigest
import java.util.Locale

class SettingsActivity : AppCompatActivity() {

    private class Row(val title: TextView, val sub: TextView)

    private lateinit var prefs: Prefs
    private lateinit var container: LinearLayout
    private lateinit var cityRow: Row
    private lateinit var methodRow: Row
    private lateinit var soundRow: Row
    private lateinit var testRow: Row
    private lateinit var builtinRow: Row
    private lateinit var wallpaperRow: Row
    private lateinit var slideRow: Row
    private lateinit var builtinSlideRow: Row
    private lateinit var intervalRow: Row
    private lateinit var tzRow: Row
    private lateinit var hijriRow: Row
    private lateinit var dimRow: Row
    private lateinit var languageRow: Row
    private lateinit var timeRow: Row
    private lateinit var volumeRow: Row
    private lateinit var spanRow: Row
    private lateinit var rowsRow: Row

    private val methods = listOf(
        23 to "الأردن – وزارة الأوقاف | Jordan (Awqaf)",
        5 to "مصر – الهيئة المصرية | Egyptian General Authority",
        4 to "مكة – أم القرى | Umm Al-Qura, Makkah",
        3 to "رابطة العالم الإسلامي | Muslim World League",
        2 to "أمريكا الشمالية | ISNA",
        1 to "كراتشي | Karachi",
        8 to "منطقة الخليج | Gulf Region",
        9 to "الكويت | Kuwait",
        10 to "قطر | Qatar",
        16 to "دبي | Dubai",
        13 to "تركيا – ديانت | Turkey (Diyanet)",
        21 to "المغرب | Morocco",
        19 to "الجزائر | Algeria",
        18 to "تونس | Tunisia"
    )

    private val presets = listOf(
        "kirkjufell" to R.string.preset_kirkjufell,
        "photo1" to R.string.preset_photo1,
        "photo3" to R.string.preset_photo3,
        "photo6" to R.string.preset_photo6
    )

    private val audioExts = setOf("mp3", "ogg", "m4a", "wav", "aac", "opus")
    private val wallpaperExts = setOf("jpg", "jpeg", "png", "webp", "mp4", "mkv", "webm", "mov", "3gp")
    private val videoExts = setOf("mp4", "mkv", "webm", "mov", "3gp")

    private var photoPermissionThen: (() -> Unit)? = null
    private val photoPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val then = photoPermissionThen
            photoPermissionThen = null
            if (granted) then?.invoke()
            else Toast.makeText(this, R.string.photos_permission_needed, Toast.LENGTH_LONG).show()
        }

    private var storageThen: (() -> Unit)? = null
    private val storagePermission =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            val then = storageThen
            storageThen = null
            then?.invoke() // open the browser even when refused: app folders still work
        }

    private fun withStorage(open: () -> Unit) {
        val needed = if (Build.VERSION.SDK_INT >= 33) {
            listOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        val missing = needed.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) {
            open()
            return
        }
        storageThen = open
        try {
            storagePermission.launch(missing.toTypedArray())
        } catch (e: Exception) {
            storageThen = null
            open()
        }
    }

    private fun browseFiles(title: String, exts: Set<String>, onPick: (File) -> Unit) {
        withStorage { FileBrowser.show(this, title, exts, onPick) }
    }

    /** Opens the in-launcher photo grid (TVs usually have no system file picker). */
    private fun withMediaPermission(kind: PhotoBrowser.Kind, open: () -> Unit) {
        if (PhotoBrowser.hasPermission(this, kind)) open()
        else {
            photoPermissionThen = open
            photoPermission.launch(PhotoBrowser.permission(kind))
        }
    }

    private fun browsePhotos(kind: PhotoBrowser.Kind = PhotoBrowser.Kind.IMAGE, onPick: (Uri) -> Unit) {
        withMediaPermission(kind) {
            PhotoBrowser.show(
                this,
                getString(if (kind == PhotoBrowser.Kind.VIDEO) R.string.browse_videos else R.string.browse_photos),
                getString(R.string.photos_empty_fmt, MediaDirs.slideDir(this).absolutePath), kind, onPick
            )
        }
    }

    /** Lists the audio files on the device so an Adhan sound can be chosen without a file manager. */
    private fun browseAudio() {
        withMediaPermission(PhotoBrowser.Kind.AUDIO) {
            Thread {
                val list = PhotoBrowser.queryAudio(this)
                runOnUiThread {
                    if (list.isEmpty()) {
                        Toast.makeText(this, R.string.audio_empty, Toast.LENGTH_LONG).show()
                    } else {
                        Dialogs.showChoice(this, getString(R.string.browse_audio), list.map { it.title }) { which ->
                            copyAdhan(list[which])
                        }
                    }
                }
            }.start()
        }
    }

    private fun copyAdhan(item: PhotoBrowser.AudioItem) {
        Thread {
            try {
                val dir = getExternalFilesDir("Adhan") ?: java.io.File(filesDir, "Adhan")
                dir.mkdirs()
                dir.listFiles()?.filter { it.name.startsWith("custom_") }?.forEach { it.delete() }
                val ext = item.title.substringAfterLast('.', "mp3")
                val out = java.io.File(dir, "custom_${System.currentTimeMillis()}.$ext")
                contentResolver.openInputStream(item.uri)?.use { input ->
                    out.outputStream().use { input.copyTo(it) }
                }
                runOnUiThread { setSound(Uri.fromFile(out).toString(), item.title) }
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this, R.string.cannot_launch, Toast.LENGTH_SHORT).show() }
            }
        }.start()
    }

    private fun setWallpaperFromPhoto(uri: Uri, video: Boolean = false) {
        Thread {
            try {
                val dir = getExternalFilesDir("Wallpapers") ?: java.io.File(filesDir, "Wallpapers")
                dir.mkdirs()
                dir.listFiles()?.filter { it.name.startsWith("wp_") }?.forEach { it.delete() }
                val out = java.io.File(dir, "wp_${System.currentTimeMillis()}." + if (video) "mp4" else "jpg")
                contentResolver.openInputStream(uri)?.use { input ->
                    out.outputStream().use { input.copyTo(it) }
                }
                runOnUiThread { setWallpaper(Uri.fromFile(out).toString(), if (video) "video" else "image") }
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this, R.string.cannot_launch, Toast.LENGTH_SHORT).show() }
            }
        }.start()
    }

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundResource(R.drawable.bg_settings)
        }
        container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(72), dp(38), dp(72), dp(38))
        }
        scroll.addView(container)
        setContentView(scroll)
        build()
    }

    override fun onPause() {
        super.onPause()
        AdhanScheduler.reschedule(this)
    }

    // ---------------------------------------------------------------- UI

    private fun build() {
        val section = intent.getStringExtra("section")
        container.addView(TextView(this).apply {
            text = getString(
                when (section) {
                    "prayer" -> R.string.cat_prayer
                    "wallpaper" -> R.string.cat_wallpaper
                    "home" -> R.string.cat_home
                    "general" -> R.string.cat_general
                    else -> R.string.settings_title
                }
            )
            textSize = 34f
            setTextColor(Color.parseColor("#F4F8FF"))
        })
        when (section) {
            "prayer" -> buildPrayer()
            "wallpaper" -> buildWallpaper()
            "home" -> buildHome()
            "general" -> buildGeneral()
            else -> buildCategories()
        }
    }

    private fun openSection(name: String) {
        startActivity(Intent(this, SettingsActivity::class.java).putExtra("section", name))
    }

    private fun buildCategories() {
        addRow(getString(R.string.cat_prayer), getString(R.string.cat_prayer_sub)) { openSection("prayer") }
        addRow(getString(R.string.cat_wallpaper), getString(R.string.cat_wallpaper_sub)) { openSection("wallpaper") }
        addRow(getString(R.string.cat_home), getString(R.string.cat_home_sub)) { openSection("home") }
        addRow(getString(R.string.cat_general), getString(R.string.cat_general_sub)) { openSection("general") }
    }

    private fun buildPrayer() {
        addToggle(getString(R.string.adhan_auto), prefs.adhanEnabled) { on ->
            prefs.adhanEnabled = on
            if (on) askNotificationPermission()
            AdhanScheduler.reschedule(this)
        }
        addLabel(R.string.prayers_to_announce)
        for (name in PrayerRepository.PRAYERS) {
            addToggle(prayerLabel(name), prefs.isPrayerEnabled(name)) { on ->
                prefs.setPrayerEnabled(name, on)
                AdhanScheduler.reschedule(this)
            }
        }
        addToggle(getString(R.string.card_prayer), prefs.showPrayerCard) { on -> prefs.showPrayerCard = on }
        cityRow = addRow(getString(R.string.city), prefs.cityName) { showCityDialog() }
        methodRow = addRow(getString(R.string.calc_method), methodLabel()) { showMethodDialog() }
        addRow(getString(R.string.prayer_adjust), offsetsSummary()) { showOffsetsDialog() }
        soundRow = addRow(getString(R.string.adhan_sound), soundSummary()) { showSoundDialog() }
        volumeRow = addRow(getString(R.string.adhan_volume), "${prefs.adhanVolume}%") { showVolumeDialog() }
        testRow = addRow(getString(R.string.test_adhan), null) { toggleTest() }
    }

    private fun buildWallpaper() {
        builtinRow = addRow(getString(R.string.wallpaper_builtin), presetLabel(prefs.wallpaperPreset)) {
            showPresetDialog()
        }
        wallpaperRow = addRow(getString(R.string.wallpaper_choose), wallpaperSummary()) { showWallpaperDialog() }
        builtinSlideRow = addRow(getString(R.string.slideshow_builtin), slideBuiltinSummary()) {
            prefs.wallpaperType = "slideshow_builtin"
            prefs.dim = 0
            prefs.wallpaperStamp = System.currentTimeMillis()
            if (::dimRow.isInitialized) dimRow.sub.text = getString(R.string.dim_fmt, prefs.dim)
            refreshWallpaperRows()
        }
        intervalRow = addRow(getString(R.string.slide_interval), intervalLabel()) { showIntervalDialog() }
        slideRow = addRow(getString(R.string.wallpaper_slideshow), slideSummary()) { showSlideshowDialog() }
        addRow(getString(R.string.wallpaper_reset), null) {
            prefs.wallpaperType = "none"
            prefs.wallpaperUri = null
            prefs.wallpaperPreset = "kirkjufell"
            refreshWallpaperRows()
        }
        dimRow = addRow(getString(R.string.wallpaper_dim), getString(R.string.dim_fmt, prefs.dim)) { showDimDialog() }
    }

    private fun buildHome() {
        spanRow = addRow(getString(R.string.apps_per_row), prefs.gridSpan.toString()) {
            val values = listOf(4, 5, 6, 7)
            Dialogs.showChoice(this, getString(R.string.apps_per_row), values.map { it.toString() }) { which ->
                prefs.gridSpan = values[which]
                spanRow.sub.text = prefs.gridSpan.toString()
            }
        }
        rowsRow = addRow(getString(R.string.rows_below_fav), rowsLabel(prefs.homeRows)) {
            val values = listOf(0, 1, 2, 3, 4, 5, 6, 8, 99)
            Dialogs.showChoice(this, getString(R.string.rows_below_fav), values.map { rowsLabel(it) }) { which ->
                prefs.homeRows = values[which]
                rowsRow.sub.text = rowsLabel(prefs.homeRows)
            }
        }
        addToggle(getString(R.string.glossy_tiles), prefs.glossy) { prefs.glossy = it }
        addToggle(getString(R.string.focus_animation), prefs.animations) { prefs.animations = it }
        addToggle(getString(R.string.show_greeting), prefs.showGreeting) { prefs.showGreeting = it }
        addToggle(getString(R.string.show_wifi_btn), prefs.showWifiButton) { prefs.showWifiButton = it }
        addToggle(getString(R.string.card_prayer), prefs.showPrayerCard) { prefs.showPrayerCard = it }
        addToggle(getString(R.string.card_weather), prefs.showWeather) { prefs.showWeather = it }
        timeRow = addRow(getString(R.string.time_format), timeFormatLabel()) { showTimeFormatDialog() }
        ssRow = addRow(getString(R.string.screensaver), screensaverLabel()) { showScreensaverDialog() }
    }

    private lateinit var ssRow: Row

    private fun screensaverLabel(): String =
        prefs.screensaverMinutes.let { if (it == 0) getString(R.string.off_label) else getString(R.string.minutes_fmt, it) }

    private fun showScreensaverDialog() {
        val values = listOf(0, 5, 10, 30, 60)
        Dialogs.showChoice(
            this, getString(R.string.screensaver),
            values.map { if (it == 0) getString(R.string.off_label) else getString(R.string.minutes_fmt, it) }
        ) { which ->
            prefs.screensaverMinutes = values[which]
            ssRow.sub.text = screensaverLabel()
        }
    }

    private fun buildGeneral() {
        languageRow = addRow(getString(R.string.language), languageLabel()) { showLanguageDialog() }
        tzRow = addRow(getString(R.string.time_zone), tzLabel()) { showTzDialog() }
        hijriRow = addRow(getString(R.string.hijri_adjust), hijriAdjustLabel()) { showHijriDialog() }
        addRow(getString(R.string.menu_set_home), getString(R.string.menu_set_home_sub)) { showSetHomeHelp() }
        addRow(getString(R.string.about), null) {
            AlertDialog.Builder(this, R.style.LauncherDialog)
                .setTitle(R.string.about)
                .setMessage(R.string.about_text)
                .setPositiveButton(R.string.ok, null)
                .show()
        }
    }

    private fun showSetHomeHelp() {
        AlertDialog.Builder(this, R.style.LauncherDialog)
            .setTitle(R.string.menu_set_home)
            .setMessage(R.string.set_home_text)
            .setPositiveButton(R.string.ok, null)
            .show()
    }

    private fun offsetsSummary(): String = PrayerRepository.PRAYERS.joinToString("  ") {
        val o = prefs.prayerOffset(it)
        "${prayerLabel(it)} ${if (o > 0) "+$o" else o.toString()}"
    }

    private fun showOffsetsDialog() {
        val names = PrayerRepository.PRAYERS
        Dialogs.showChoice(
            this, getString(R.string.prayer_adjust),
            names.map { "${prayerLabel(it)}: ${prefs.prayerOffset(it)}" }
        ) { which ->
            val name = names[which]
            val values = listOf(-10, -5, -3, -2, -1, 0, 1, 2, 3, 5, 10)
            Dialogs.showChoice(this, prayerLabel(name), values.map { if (it > 0) "+$it" else it.toString() }) { i ->
                prefs.setPrayerOffset(name, values[i])
                AdhanScheduler.reschedule(this)
                showOffsetsDialog()
            }
        }
    }

    private fun showVolumeDialog() {
        val values = listOf(20, 40, 60, 80, 100)
        Dialogs.showChoice(this, getString(R.string.adhan_volume), values.map { "$it%" }) { which ->
            prefs.adhanVolume = values[which]
            volumeRow.sub.text = "${prefs.adhanVolume}%"
        }
    }

    private fun addSection(titleRes: Int) {
        container.addView(TextView(this).apply {
            text = getString(titleRes)
            textSize = 20f
            setTextColor(Color.parseColor("#F4F8FF"))
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, dp(28), 0, dp(8)) })
    }

    private fun addLabel(textRes: Int) {
        container.addView(TextView(this).apply {
            text = getString(textRes)
            textSize = 14f
            setTextColor(Color.parseColor("#ACB8C1"))
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(dp(4), dp(4), 0, dp(2)) })
    }

    private fun addRow(title: String, subtitle: String?, onClick: () -> Unit): Row {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            isFocusable = true
            isClickable = true
            setBackgroundResource(R.drawable.bg_row)
            setPadding(dp(20), dp(12), dp(20), dp(12))
            setOnClickListener { onClick() }
        }
        val titleView = TextView(this).apply {
            text = title
            textSize = 20f
            setTextColor(Color.parseColor("#F4F8FF"))
        }
        val subView = TextView(this).apply {
            text = subtitle ?: ""
            textSize = 14f
            setTextColor(Color.parseColor("#C0D4E1"))
            visibility = if (subtitle == null) View.GONE else View.VISIBLE
        }
        box.addView(titleView)
        box.addView(subView)
        container.addView(box, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, dp(4), 0, dp(4)) })
        return Row(titleView, subView)
    }

    private fun addToggle(title: String, initial: Boolean, onChange: (Boolean) -> Unit) {
        var state = initial
        lateinit var row: Row
        row = addRow(title, onOff(state)) {
            state = !state
            row.sub.text = onOff(state)
            onChange(state)
        }
    }

    private fun onOff(on: Boolean) = getString(if (on) R.string.on else R.string.off)

    private fun prayerLabel(name: String): String = getString(
        when (name) {
            "Fajr" -> R.string.prayer_fajr
            "Dhuhr" -> R.string.prayer_dhuhr
            "Asr" -> R.string.prayer_asr
            "Maghrib" -> R.string.prayer_maghrib
            else -> R.string.prayer_isha
        }
    )

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    // ---------------------------------------------------------------- city & method

    private fun currentLang(): String =
        if (prefs.language == "system") Locale.getDefault().language else prefs.language

    private fun showCityDialog() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(8))
        }
        val input = EditText(this).apply {
            hint = getString(R.string.city_search_hint)
            setSingleLine()
            setTextColor(Color.WHITE)
            setHintTextColor(ContextCompat.getColor(this@SettingsActivity, R.color.text_secondary))
            inputType = InputType.TYPE_CLASS_TEXT
            imeOptions = EditorInfo.IME_ACTION_SEARCH
        }
        val results = ArrayList<City>()
        val listAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, ArrayList<String>())
        val list = ListView(this).apply { adapter = listAdapter }

        fun doSearch() {
            val q = input.text.toString().trim()
            if (q.isEmpty()) return
            val lang = currentLang()
            Thread {
                val found = PrayerRepository.searchCities(q, lang)
                runOnUiThread {
                    results.clear()
                    results.addAll(found)
                    listAdapter.clear()
                    listAdapter.addAll(found.map { it.display })
                    if (found.isEmpty()) Toast.makeText(this, R.string.no_results, Toast.LENGTH_SHORT).show()
                }
            }.start()
        }

        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                doSearch()
                true
            } else {
                false
            }
        }
        val button = Button(this).apply {
            text = getString(R.string.search_btn)
            setOnClickListener { doSearch() }
        }
        root.addView(input)
        root.addView(button)
        root.addView(list, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(260)))

        val dialog = AlertDialog.Builder(this, R.style.LauncherDialog)
            .setTitle(R.string.city)
            .setView(root)
            .setNegativeButton(R.string.cancel, null)
            .create()
        list.setOnItemClickListener { _, _, position, _ ->
            val city = results[position]
            prefs.cityName = city.display
            prefs.latitude = city.lat
            prefs.longitude = city.lon
            cityRow.sub.text = city.display
            dialog.dismiss()
            AdhanScheduler.reschedule(this)
        }
        dialog.show()
    }

    private fun methodLabel(): String =
        methods.firstOrNull { it.first == prefs.method }?.second ?: "#${prefs.method}"

    private fun showMethodDialog() {
        Dialogs.showChoice(this, getString(R.string.calc_method), methods.map { it.second }) { which ->
            prefs.method = methods[which].first
            methodRow.sub.text = methodLabel()
            AdhanScheduler.reschedule(this)
        }
    }

    // ---------------------------------------------------------------- Adhan sound

    private fun soundSummary(): String =
        prefs.adhanName?.let { getString(R.string.sound_custom_fmt, it) } ?: getString(R.string.sound_default)

    private fun setSound(uri: String?, name: String?) {
        prefs.adhanUri = uri
        prefs.adhanName = name
        if (::soundRow.isInitialized) soundRow.sub.text = soundSummary()
    }

    private fun showSoundDialog() {
        val items = listOf(
            getString(R.string.browse_files),
            getString(R.string.browse_audio),
            getString(R.string.reset_default)
        )
        Dialogs.showChoice(this, getString(R.string.adhan_sound), items) { which ->
            when (which) {
                0 -> browseFiles(getString(R.string.browse_files), audioExts) { file ->
                    copyAdhan(PhotoBrowser.AudioItem(Uri.fromFile(file), file.name))
                }
                1 -> browseAudio()
                2 -> setSound(null, null)
            }
        }
    }

    private fun toggleTest() {
        if (AdhanService.isPlaying) {
            AdhanService.stopNow()
            testRow.title.text = getString(R.string.test_adhan)
        } else {
            val intent = Intent(this, AdhanService::class.java)
                .setAction(AdhanService.ACTION_TEST)
                .putExtra(AdhanScheduler.EXTRA_PRAYER, "Fajr")
            ContextCompat.startForegroundService(this, intent)
            testRow.title.text = getString(R.string.stop_adhan)
        }
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            try {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            } catch (e: Exception) {
                // Some TV builds have no permission dialog; the Adhan still plays.
            }
        }
    }

    // ---------------------------------------------------------------- wallpaper

    private fun presetLabel(id: String): String =
        getString(presets.firstOrNull { it.first == id }?.second ?: R.string.preset_kirkjufell)

    private fun slideBuiltinSummary(): String =
        getString(if (prefs.wallpaperType == "slideshow_builtin") R.string.on else R.string.off)

    private fun intervalLabel(): String {
        val s = prefs.slideSeconds
        return if (s < 60) getString(R.string.seconds_fmt, s) else getString(R.string.minutes_fmt, s / 60)
    }

    private fun showIntervalDialog() {
        val values = listOf(10, 30, 60, 300, 900)
        Dialogs.showChoice(
            this, getString(R.string.slide_interval),
            values.map { if (it < 60) getString(R.string.seconds_fmt, it) else getString(R.string.minutes_fmt, it / 60) }
        ) { which ->
            prefs.slideSeconds = values[which]
            prefs.wallpaperStamp = System.currentTimeMillis()
            intervalRow.sub.text = intervalLabel()
        }
    }

    private fun wallpaperSummary(): String = when (prefs.wallpaperType) {
        "slideshow_builtin" -> getString(R.string.slideshow_builtin)
        "video" -> getString(R.string.wallpaper_video)
        "image" -> getString(R.string.wallpaper_image)
        "slideshow" -> getString(R.string.slideshow_on)
        else -> presetLabel(prefs.wallpaperPreset)
    }

    private fun slideSummary(): String =
        getString(R.string.slideshow_count_fmt, MediaDirs.slidePhotos(this).size)

    private fun refreshWallpaperRows() {
        if (::builtinRow.isInitialized) builtinRow.sub.text = presetLabel(prefs.wallpaperPreset)
        if (::wallpaperRow.isInitialized) wallpaperRow.sub.text = wallpaperSummary()
        if (::slideRow.isInitialized) slideRow.sub.text = slideSummary()
        if (::builtinSlideRow.isInitialized) builtinSlideRow.sub.text = slideBuiltinSummary()
    }

    private fun setWallpaper(uri: String, type: String) {
        prefs.wallpaperUri = uri
        prefs.wallpaperType = type
        refreshWallpaperRows()
    }

    private fun showPresetDialog() {
        Dialogs.showChoice(
            this, getString(R.string.wallpaper_builtin), presets.map { getString(it.second) }
        ) { which ->
            prefs.wallpaperPreset = presets[which].first
            prefs.wallpaperType = "none"
            if (presets[which].first.startsWith("photo")) prefs.dim = 0 // photos should look clear
            prefs.wallpaperStamp = System.currentTimeMillis()
            refreshWallpaperRows()
        }
    }

    private fun showWallpaperDialog() {
        val items = listOf(
            getString(R.string.browse_files),
            getString(R.string.browse_photos),
            getString(R.string.browse_videos)
        )
        Dialogs.showChoice(this, getString(R.string.wallpaper_choose), items) { which ->
            when (which) {
                0 -> browseFiles(getString(R.string.browse_files), wallpaperExts) { file ->
                    setWallpaperFromPhoto(Uri.fromFile(file), file.extension.lowercase() in videoExts)
                }
                1 -> browsePhotos { setWallpaperFromPhoto(it) }
                2 -> browsePhotos(PhotoBrowser.Kind.VIDEO) { setWallpaperFromPhoto(it, true) }
            }
        }
    }

    // ---------------------------------------------------------------- slideshow

    private fun showSlideshowDialog() {
        val items = listOf(
            getString(R.string.slideshow_start),
            getString(R.string.browse_files),
            getString(R.string.slideshow_add_browse),
            getString(R.string.slideshow_delete)
        )
        Dialogs.showChoice(this, getString(R.string.wallpaper_slideshow), items) { which ->
            when (which) {
                0 -> startSlideshow()
                1 -> browseFiles(getString(R.string.browse_files), MediaDirs.IMAGE_EXTS) { addSlidePhoto(Uri.fromFile(it)) }
                2 -> browsePhotos { addSlidePhoto(it) }
                3 -> showDeletePhotoDialog()
            }
        }
    }

    private fun startSlideshow() {
        if (MediaDirs.slidePhotos(this).isEmpty()) {
            AlertDialog.Builder(this, R.style.LauncherDialog)
                .setMessage(getString(R.string.slideshow_empty_fmt, MediaDirs.slideDir(this).absolutePath))
                .setPositiveButton(R.string.ok, null)
                .show()
            return
        }
        prefs.wallpaperType = "slideshow"
        prefs.wallpaperStamp = System.currentTimeMillis()
        refreshWallpaperRows()
    }

    private fun showDeletePhotoDialog() {
        val photos = MediaDirs.slidePhotos(this)
        if (photos.isEmpty()) {
            startSlideshow()
            return
        }
        Dialogs.showChoice(this, getString(R.string.slideshow_delete), photos.map { it.name }) { which ->
            val file = photos[which]
            AlertDialog.Builder(this, R.style.LauncherDialog)
                .setMessage(getString(R.string.delete_confirm_fmt, file.name))
                .setPositiveButton(R.string.ok) { _, _ ->
                    file.delete()
                    if (MediaDirs.slidePhotos(this).isEmpty() && prefs.wallpaperType == "slideshow") {
                        prefs.wallpaperType = "none"
                    }
                    prefs.wallpaperStamp = System.currentTimeMillis()
                    refreshWallpaperRows()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }

    /** Copies the picked photo into the slideshow folder, skipping exact duplicates. */
    private fun addSlidePhoto(uri: Uri) {
        Thread {
            val dir = MediaDirs.slideDir(this)
            val incoming = File(dir, ".incoming.tmp")
            val copied = try {
                contentResolver.openInputStream(uri)?.use { input ->
                    incoming.outputStream().use { out -> input.copyTo(out) }
                }
                incoming.exists()
            } catch (e: Exception) {
                false
            }
            if (!copied) {
                incoming.delete()
                runOnUiThread { Toast.makeText(this, R.string.picker_missing, Toast.LENGTH_LONG).show() }
                return@Thread
            }
            val hash = sha256(incoming)
            val duplicate = MediaDirs.slidePhotos(this).any { sha256(it) == hash }
            if (duplicate) {
                incoming.delete()
                runOnUiThread { Toast.makeText(this, R.string.slideshow_dup, Toast.LENGTH_LONG).show() }
            } else {
                val mime = contentResolver.getType(uri) ?: ""
                val name = (uri.lastPathSegment ?: "").lowercase()
                val ext = when {
                    mime.contains("png") || name.endsWith(".png") -> "png"
                    mime.contains("webp") || name.endsWith(".webp") -> "webp"
                    else -> "jpg"
                }
                incoming.renameTo(File(dir, "photo_${System.currentTimeMillis()}.$ext"))
                runOnUiThread {
                    prefs.wallpaperStamp = System.currentTimeMillis()
                    refreshWallpaperRows()
                    Toast.makeText(this, R.string.slideshow_added, Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun showDimDialog() {
        val value = TextView(this).apply {
            text = getString(R.string.dim_fmt, prefs.dim)
            textSize = 18f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
        }
        val seek = SeekBar(this).apply {
            max = 80
            progress = prefs.dim.coerceIn(0, 80)
            keyProgressIncrement = 5
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    value.text = getString(R.string.dim_fmt, progress)
                }

                override fun onStartTrackingTouch(seekBar: SeekBar) {}
                override fun onStopTrackingTouch(seekBar: SeekBar) {}
            })
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(16), dp(24), dp(8))
            addView(value)
            addView(seek)
        }
        AlertDialog.Builder(this, R.style.LauncherDialog)
            .setTitle(R.string.wallpaper_dim)
            .setView(box)
            .setPositiveButton(R.string.ok) { _, _ ->
                prefs.dim = seek.progress
                dimRow.sub.text = getString(R.string.dim_fmt, prefs.dim)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ---------------------------------------------------------------- language

    private fun languageLabel(): String = getString(
        when (prefs.language) {
            "ar" -> R.string.lang_ar
            "en" -> R.string.lang_en
            else -> R.string.lang_system
        }
    )

    private fun timeFormatLabel(): String = getString(
        when (prefs.timeFormat) {
            "12" -> R.string.time_12
            "24" -> R.string.time_24
            else -> R.string.lang_system
        }
    )

    private fun hijriAdjustLabel(): String {
        val a = prefs.hijriAdjust
        return if (a == 0) getString(R.string.hijri_adjust_none) else getString(R.string.days_fmt, if (a > 0) "+$a" else a.toString())
    }

    private fun showHijriDialog() {
        val values = listOf(-2, -1, 0, 1, 2)
        Dialogs.showChoice(
            this, getString(R.string.hijri_adjust),
            values.map { if (it == 0) getString(R.string.hijri_adjust_none) else getString(R.string.days_fmt, if (it > 0) "+$it" else it.toString()) }
        ) { which ->
            prefs.hijriAdjust = values[which]
            hijriRow.sub.text = hijriAdjustLabel()
        }
    }

    private fun tzLabel(): String {
        val auto = prefs.tzMode == "auto"
        val label = CityClock.label(CityClock.offsetSeconds(this))
        return if (auto) getString(R.string.tz_auto_fmt, label) else label
    }

    private fun showTzDialog() {
        val hours = listOf(-8, -7, -6, -5, -4, -3, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
        val secs = listOf(null) + hours.map { it * 3600 } + listOf(12600, 16200, 19800)
        val labels = listOf(getString(R.string.tz_auto)) + (hours.map { it * 3600 } + listOf(12600, 16200, 19800)).map { CityClock.label(it) }
        Dialogs.showChoice(this, getString(R.string.time_zone), labels) { which ->
            val s = secs[which]
            prefs.tzMode = s?.toString() ?: "auto"
            tzRow.sub.text = tzLabel()
            if (s == null) Thread { CityClock.ensure(applicationContext); runOnUiThread { tzRow.sub.text = tzLabel() } }.start()
            AdhanScheduler.reschedule(this)
        }
    }

    private fun showTimeFormatDialog() {
        val codes = listOf("system", "12", "24")
        val labels = listOf(getString(R.string.lang_system), getString(R.string.time_12), getString(R.string.time_24))
        Dialogs.showChoice(this, getString(R.string.time_format), labels) { which ->
            prefs.timeFormat = codes[which]
            timeRow.sub.text = timeFormatLabel()
        }
    }

    private fun showLanguageDialog() {
        val codes = listOf("system", "ar", "en")
        val labels = listOf(getString(R.string.lang_system), getString(R.string.lang_ar), getString(R.string.lang_en))
        Dialogs.showChoice(this, getString(R.string.language), labels) { which ->
            val code = codes[which]
            prefs.language = code
            AppCompatDelegate.setApplicationLocales(
                if (code == "system") LocaleListCompat.getEmptyLocaleList()
                else LocaleListCompat.forLanguageTags(code)
            )
        }
    }

    // ---------------------------------------------------------------- file helpers

    private fun launchPicker(launcher: ActivityResultLauncher<Array<String>>, mimeTypes: Array<String>) {
        try {
            launcher.launch(mimeTypes)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, R.string.picker_missing, Toast.LENGTH_LONG).show()
        }
    }

    private fun persist(uri: Uri) {
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (e: Exception) {
            // Not every provider supports persistable grants.
        }
    }

    private fun queryName(uri: Uri): String? {
        return try {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            } ?: uri.lastPathSegment
        } catch (e: Exception) {
            uri.lastPathSegment
        }
    }

    private fun pickFromFolder(folder: String, extensions: Set<String>, onPick: (File) -> Unit) {
        val dir = getExternalFilesDir(folder)?.also { it.mkdirs() }
        val files = dir?.listFiles()
            ?.filter { it.isFile && it.extension.lowercase() in extensions }
            ?.sortedBy { it.name }
            ?: emptyList()
        if (files.isEmpty()) {
            AlertDialog.Builder(this, R.style.LauncherDialog)
                .setMessage(getString(R.string.folder_empty_fmt, dir?.absolutePath ?: "-"))
                .setPositiveButton(R.string.ok, null)
                .show()
            return
        }
        Dialogs.showChoice(this, folder, files.map { it.name }) { which -> onPick(files[which]) }
    }

    private fun rowsLabel(n: Int) = if (n >= 99) getString(R.string.all_apps_rows) else n.toString()
}
