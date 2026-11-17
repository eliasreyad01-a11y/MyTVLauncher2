package com.mytv.tvos

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Drawable
import org.json.JSONArray
import org.json.JSONObject

/** One launchable app (or the built-in Settings entry). */
class AppItem(
    val pkg: String,
    val label: String,
    val icon: Drawable,
    val banner: Drawable?,
    val intent: Intent?
) {
    /** Colour of the icon's own background (edge pixels), used to paint the tile behind a square icon. */
    val tileColor: Int by lazy { Art.edgeColor(icon) }
}

/** A cell of the home grid: an app, a folder, or an empty slot that keeps the dock row six wide. */
class Entry(val key: String, val app: AppItem?, val folderName: String? = null, val folderApps: List<AppItem> = emptyList()) {
    val isFolder get() = folderName != null
    val title: String get() = app?.label ?: folderName.orEmpty()
    val isSpacer get() = app == null && folderName == null

    companion object {
        const val SPACER = "spacer"
        fun spacer() = Entry(SPACER, null)
    }
}

class Layout(val dock: List<AppItem>, val library: List<Entry>) {
    /** Grid cells: the dock padded to a full row of six, then the library. */
    fun cells(): List<Entry> {
        val out = ArrayList<Entry>()
        for (a in dock) out.add(Entry(a.pkg, a))
        while (out.size < Store.COLUMNS) out.add(Entry.spacer())
        out.addAll(library)
        return out
    }
}

/** Dock, library order, folders and hidden apps; everything the home screen remembers. */
class Store(ctx: Context) {
    private val sp = ctx.applicationContext.getSharedPreferences("tvos_home", Context.MODE_PRIVATE)

    companion object {
        const val COLUMNS = 6
        const val SETTINGS_PKG = "tvos.settings"
        private val PREFERRED = listOf("youtube", "netflix", "disney", "primevideo", "spotify", "plex", "kodi", "chrome", "vending", "music")
    }

    private fun csv(key: String): List<String>? = sp.getString(key, null)?.let { s ->
        if (s.isEmpty()) emptyList() else s.split('\n')
    }

    private fun putCsv(key: String, v: List<String>) { sp.edit().putString(key, v.joinToString("\n")).apply() }

    var hidden: Set<String>
        get() = (csv("hidden") ?: emptyList()).toSet()
        set(v) = putCsv("hidden", v.toList())

    private class Folder(val id: String, var name: String, val pkgs: MutableList<String>)

    private fun readFolders(): MutableList<Folder> {
        val out = ArrayList<Folder>()
        try {
            val o = JSONObject(sp.getString("folders", "{}")!!)
            for (id in o.keys()) {
                val f = o.getJSONObject(id)
                val a = f.getJSONArray("apps")
                out.add(Folder(id, f.getString("name"), MutableList(a.length()) { a.getString(it) }))
            }
        } catch (_: Exception) {
        }
        return out.sortedBy { it.id }.toMutableList()
    }

    private fun writeFolders(list: List<Folder>) {
        val o = JSONObject()
        for (f in list) o.put(f.id, JSONObject().put("name", f.name).put("apps", JSONArray(f.pkgs)))
        sp.edit().putString("folders", o.toString()).apply()
    }

    fun layout(all: List<AppItem>): Layout {
        val hiddenSet = hidden
        val visible = all.filter { it.pkg !in hiddenSet }
        val byPkg = visible.associateBy { it.pkg }
        val folders = readFolders()
        for (f in folders) f.pkgs.retainAll(byPkg.keys)
        folders.removeAll { it.pkgs.isEmpty() }
        val inFolder = folders.flatMap { it.pkgs }.toSet()

        val savedDock = csv("dock")
        val dockPkgs: List<String> = if (savedDock != null) {
            savedDock.filter { it in byPkg && it !in inFolder }.distinct().take(COLUMNS)
        } else defaultDock(visible)
        val dockSet = dockPkgs.toSet()

        val saved = csv("order") ?: emptyList()
        val index = HashMap<String, Int>().also { m -> saved.forEachIndexed { i, k -> m[k] = i } }
        val cands = ArrayList<Entry>()
        for (a in visible) if (a.pkg !in dockSet && a.pkg !in inFolder) cands.add(Entry(a.pkg, a))
        for (f in folders) cands.add(Entry("f:" + f.id, null, f.name, f.pkgs.mapNotNull { byPkg[it] }))
        cands.sortWith(compareBy<Entry>({ index[it.key] ?: Int.MAX_VALUE }, { it.title.lowercase() }))
        return Layout(dockPkgs.mapNotNull { byPkg[it] }, cands)
    }

    private fun defaultDock(apps: List<AppItem>): List<String> {
        val out = ArrayList<String>()
        for (p in PREFERRED) {
            val m = apps.firstOrNull { p in it.pkg.lowercase() && it.pkg !in out }
            if (m != null) out.add(m.pkg)
            if (out.size == COLUMNS - 1) break
        }
        for (a in apps) { if (out.size >= COLUMNS - 1) break; if (a.pkg != SETTINGS_PKG && a.pkg !in out) out.add(a.pkg) }
        if (apps.any { it.pkg == SETTINGS_PKG }) out.add(SETTINGS_PKG)
        return out.take(COLUMNS)
    }

    /** Writes the current layout down so the next edit starts from what the user sees. */
    private fun freeze(l: Layout) {
        if (csv("dock") == null) putCsv("dock", l.dock.map { it.pkg })
        putCsv("order", l.library.map { it.key })
    }

    fun moveInLibrary(l: Layout, key: String, delta: Int): Boolean {
        val keys = l.library.map { it.key }.toMutableList()
        val i = keys.indexOf(key)
        val j = (i + delta).coerceIn(0, keys.size - 1)
        if (i < 0 || i == j) return false
        keys.add(j, keys.removeAt(i))
        if (csv("dock") == null) putCsv("dock", l.dock.map { it.pkg })
        putCsv("order", keys)
        return true
    }

    fun moveInDock(l: Layout, pkg: String, delta: Int): Boolean {
        val d = l.dock.map { it.pkg }.toMutableList()
        val i = d.indexOf(pkg)
        val j = (i + delta).coerceIn(0, d.size - 1)
        if (i < 0 || i == j) return false
        d.add(j, d.removeAt(i))
        freeze(l)
        putCsv("dock", d)
        return true
    }

    /** False when the dock is already full. */
    fun addToDock(l: Layout, pkg: String): Boolean {
        if (l.dock.size >= COLUMNS) return false
        freeze(l)
        removeFromFolders(pkg)
        putCsv("dock", l.dock.map { it.pkg } + pkg)
        return true
    }

    fun removeFromDock(l: Layout, pkg: String) {
        freeze(l)
        putCsv("dock", l.dock.map { it.pkg }.filter { it != pkg })
        putCsv("order", (csv("order") ?: emptyList()) + ("" + pkg))
    }

    private fun removeFromFolders(pkg: String) {
        val fs = readFolders()
        var changed = false
        for (f in fs) if (f.pkgs.remove(pkg)) changed = true
        fs.removeAll { it.pkgs.isEmpty() }
        if (changed) writeFolders(fs)
    }

    fun newFolder(l: Layout, pkg: String): String {
        freeze(l)
        val fs = readFolders()
        removeFromFolders(pkg)
        val fs2 = readFolders()
        val id = ((fs2.maxOfOrNull { it.id.toIntOrNull() ?: 0 } ?: 0) + 1 + fs.size).toString().padStart(4, '0')
        fs2.add(Folder(id, "Folder " + (fs2.size + 1), mutableListOf(pkg)))
        writeFolders(fs2)
        putCsv("dock", l.dock.map { it.pkg }.filter { it != pkg })
        putCsv("order", (csv("order") ?: emptyList()) + "f:$id")
        return id
    }

    fun addToFolder(l: Layout, folderKey: String, pkg: String) {
        freeze(l)
        val id = folderKey.removePrefix("f:")
        removeFromFolders(pkg)
        val fs = readFolders()
        fs.firstOrNull { it.id == id }?.pkgs?.add(pkg)
        writeFolders(fs)
        putCsv("dock", l.dock.map { it.pkg }.filter { it != pkg })
    }

    fun removeFromFolder(l: Layout, pkg: String) {
        freeze(l)
        removeFromFolders(pkg)
        putCsv("order", (csv("order") ?: emptyList()) + pkg)
    }

    fun renameFolder(folderKey: String, name: String) {
        val id = folderKey.removePrefix("f:")
        val fs = readFolders()
        fs.firstOrNull { it.id == id }?.name = name.ifBlank { "Folder" }
        writeFolders(fs)
    }

    fun deleteFolder(folderKey: String) {
        val id = folderKey.removePrefix("f:")
        val fs = readFolders()
        val gone = fs.firstOrNull { it.id == id } ?: return
        fs.remove(gone)
        writeFolders(fs)
        putCsv("order", (csv("order") ?: emptyList()).filter { it != folderKey } + gone.pkgs)
    }

    fun folderKeys(l: Layout): List<Pair<String, String>> = l.library.filter { it.isFolder }.map { it.key to it.title }

    fun reset() { sp.edit().remove("dock").remove("order").remove("folders").apply() }
}

object Art {
    /** Average colour of the icon's outer ring of pixels: that is the colour its own background has. */
    fun edgeColor(d: Drawable): Int {
        val n = 24
        val bmp = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        try {
            d.setBounds(0, 0, n, n)
            d.draw(c)
        } catch (_: Exception) {
            return 0xFF2C2C2E.toInt()
        }
        var r = 0L; var g = 0L; var b = 0L; var cnt = 0L
        for (i in 0 until n) for (j in 0 until n) {
            if (i > 1 && j > 1 && i < n - 2 && j < n - 2) continue
            val p = bmp.getPixel(i, j)
            val a = Color.alpha(p)
            if (a < 200) continue
            r += Color.red(p); g += Color.green(p); b += Color.blue(p); cnt++
        }
        bmp.recycle()
        if (cnt == 0L) return 0xFF2C2C2E.toInt()
        val avg = Color.rgb((r / cnt).toInt(), (g / cnt).toInt(), (b / cnt).toInt())
        val lum = (0.299 * Color.red(avg) + 0.587 * Color.green(avg) + 0.114 * Color.blue(avg)) / 255.0
        return if (lum > 0.82) 0xFFF4F4F6.toInt() else avg
    }
}
