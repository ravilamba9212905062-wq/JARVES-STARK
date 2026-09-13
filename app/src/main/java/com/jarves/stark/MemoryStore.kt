package com.jarves.stark

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * JARVES permanent local memory.
 * Every non-empty user/assistant turn is stored. There is intentionally no
 * time-based deletion. Retrieval sends the most relevant old memories plus
 * recent conversation to the AI, so the user normally does not need to repeat
 * earlier context.
 */
class MemoryStore(context: Context) {
    private val db = Helper(context.applicationContext).writableDatabase

    fun add(role: String, text: String) {
        if (text.isBlank()) return
        val clean = text.trim().take(20000)
        val v = ContentValues().apply {
            put("ts", System.currentTimeMillis())
            put("role", role)
            put("text", clean)
            put("pinned", if (clean.startsWith("[PINNED]")) 1 else 0)
        }
        val id = db.insert("memory", null, v)
        if (id != -1L) {
            try { db.execSQL("INSERT INTO memory_fts(rowid, text) VALUES(?, ?)", arrayOf(id, clean)) } catch (_: Exception) {}
        }
    }

    fun remember(text: String) {
        add("user_memory", "[PINNED] ${text.trim()}")
    }

    fun recent(limit: Int = 40): List<String> {
        val out = mutableListOf<String>()
        db.query("memory", arrayOf("ts", "role", "text"), null, null, null, null, "ts DESC", limit.toString()).use { c ->
            while (c.moveToNext()) out.add(format(c.getLong(0), c.getString(1), c.getString(2)))
        }
        return out.asReversed()
    }

    /** Token-based search: a whole sentence no longer has to match exactly. */
    fun search(query: String, limit: Int = 40): List<String> {
        val terms = query.lowercase(Locale.getDefault())
            .split(Regex("[^\\p{L}\\p{Nd}]+"))
            .map { it.trim() }.filter { it.length >= 2 }
            .distinct().take(12)
        if (terms.isEmpty()) return emptyList()

        val score = mutableMapOf<Long, Pair<Int, String>>()
        db.query("memory", arrayOf("id", "ts", "role", "text"), null, null, null, null, "ts DESC", "1500").use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0); val text = c.getString(3).lowercase(Locale.getDefault())
                var hits = 0
                for (t in terms) if (text.contains(t)) hits++
                if (hits > 0) score[id] = hits to format(c.getLong(1), c.getString(2), c.getString(3))
            }
        }
        return score.entries.sortedWith(compareByDescending<Map.Entry<Long, Pair<Int, String>>> { it.value.first }
            .thenByDescending { it.key }).take(limit).map { it.value.second }
    }

    fun pinned(limit: Int = 30): List<String> {
        val out = mutableListOf<String>()
        db.query("memory", arrayOf("ts", "role", "text"), "pinned=1", null, null, null, "ts DESC", limit.toString()).use { c ->
            while (c.moveToNext()) out.add(format(c.getLong(0), c.getString(1), c.getString(2)))
        }
        return out.asReversed()
    }

    fun count(): Long = db.rawQuery("SELECT COUNT(*) FROM memory", null).use { c -> if (c.moveToFirst()) c.getLong(0) else 0 }

    fun databaseBytes(): Long = try { java.io.File(db.path).length() } catch (_: Exception) { 0L }

    fun clear() { db.delete("memory", null, null); try { db.execSQL("DELETE FROM memory_fts") } catch (_: Exception) {} }

    /** Context pack: pinned facts + relevant old turns + recent turns, deduplicated. */
    fun contextFor(query: String): String {
        val all = LinkedHashSet<String>()
        all.addAll(pinned(40))
        all.addAll(search(query, 60))
        all.addAll(recent(50))
        // Keep the prompt bounded while retaining a large amount of context.
        return all.take(130).joinToString("\n").take(30000)
    }

    fun exportText(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val sb = StringBuilder("JARVES PERMANENT LONG-TERM MEMORY\n\n")
        db.query("memory", arrayOf("ts", "role", "text"), null, null, null, null, "ts ASC").use { c ->
            while (c.moveToNext()) sb.append('[').append(sdf.format(Date(c.getLong(0)))).append("] ")
                .append(c.getString(1)).append(": ").append(c.getString(2)).append('\n')
        }
        return sb.toString()
    }

    private fun format(ts: Long, role: String, text: String): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        return "[${sdf.format(Date(ts))}] $role: $text"
    }

    private class Helper(ctx: Context) : SQLiteOpenHelper(ctx, "jarves_long_memory.db", null, 2) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE memory(id INTEGER PRIMARY KEY AUTOINCREMENT, ts INTEGER NOT NULL, role TEXT NOT NULL, text TEXT NOT NULL, pinned INTEGER NOT NULL DEFAULT 0)")
            db.execSQL("CREATE INDEX idx_memory_ts ON memory(ts DESC)")
            db.execSQL("CREATE INDEX idx_memory_role ON memory(role)")
            try { db.execSQL("CREATE VIRTUAL TABLE memory_fts USING fts4(text)") } catch (_: Exception) {}
        }
        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            if (oldVersion < 2) {
                try { db.execSQL("ALTER TABLE memory ADD COLUMN pinned INTEGER NOT NULL DEFAULT 0") } catch (_: Exception) {}
                try { db.execSQL("CREATE VIRTUAL TABLE memory_fts USING fts4(text)") } catch (_: Exception) {}
            }
        }
    }
}
