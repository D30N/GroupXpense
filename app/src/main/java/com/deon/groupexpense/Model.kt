package com.deon.groupexpense

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.math.roundToLong

data class Member(var id: String, var name: String)

data class Entry(
    var id: String,
    var kind: String, // expense | advance | settle
    var title: String,
    var payer: String, // member id
    var paidTo: String = "", // for advance/settle
    var amount: Double = 0.0,
    var date: Long = 0L,
    var category: String = "",
    var attachment: String = "",
    var split: MutableMap<String, Double> = mutableMapOf() // memberId -> share (expense only)
)

data class Group(
    var id: String,
    var title: String,
    var desc: String = "",
    var currency: String = "₹",
    var currencyCode: String = "INR",
    var createdAt: Long = 0L,
    var archived: Boolean = false,
    var demo: Boolean = false,
    var members: MutableList<Member> = mutableListOf(),
    var entries: MutableList<Entry> = mutableListOf()
) {
    fun member(id: String): Member? = members.firstOrNull { it.id == id }
    fun memberName(id: String): String = member(id)?.name ?: "Someone"
}

object Store {
    var profileName: String = ""
    var profilePhoto: String = ""
    var mode: String = "DARK" // DARK | LIGHT | SYSTEM
    var accent: Int = 0xFF2DD4BF.toInt()
    var categories: MutableList<String> = mutableListOf()
    var groups: MutableList<Group> = mutableListOf()

    private var file: File? = null
    private var counter = 0

    fun newId(): String {
        counter++
        return System.currentTimeMillis().toString(36) + counter.toString(36)
    }

    val DEFAULT_CATEGORIES = listOf(
        "Food", "Travel", "Movie", "Stay", "Fuel", "Shopping",
        "Bills", "Groceries", "Fun", "Health", "Other"
    )

    fun init(ctx: Context) {
        file = File(ctx.filesDir, "data.json")
        if (file!!.exists()) {
            try {
                load(JSONObject(file!!.readText()))
                return
            } catch (e: Exception) {
                // fall through to seed
            }
        }
        seed()
        save()
    }

    private fun seed() {
        profileName = ""
        profilePhoto = ""
        mode = "DARK"
        accent = 0xFF2DD4BF.toInt()
        categories = DEFAULT_CATEGORIES.toMutableList()
        groups = mutableListOf()
        val g = Group(
            id = newId(), title = "Holiday Trip",
            desc = "This is a sample group expense",
            currency = "₹", currencyCode = "INR",
            createdAt = System.currentTimeMillis(), demo = true
        )
        val you = Member(newId(), "You")
        g.members.add(you)
        g.members.add(Member(newId(), "Arun"))
        g.members.add(Member(newId(), "Meera"))
        groups.add(g)
    }

    fun save() {
        try {
            val root = JSONObject()
            root.put("profileName", profileName)
            root.put("profilePhoto", profilePhoto)
            root.put("mode", mode)
            root.put("accent", accent)
            root.put("categories", JSONArray(categories))
            val gs = JSONArray()
            for (g in groups) {
                val o = JSONObject()
                o.put("id", g.id); o.put("title", g.title); o.put("desc", g.desc)
                o.put("currency", g.currency); o.put("currencyCode", g.currencyCode)
                o.put("createdAt", g.createdAt); o.put("archived", g.archived); o.put("demo", g.demo)
                val ms = JSONArray()
                for (m in g.members) {
                    val mo = JSONObject(); mo.put("id", m.id); mo.put("name", m.name); ms.put(mo)
                }
                o.put("members", ms)
                val es = JSONArray()
                for (e in g.entries) {
                    val eo = JSONObject()
                    eo.put("id", e.id); eo.put("kind", e.kind); eo.put("title", e.title)
                    eo.put("payer", e.payer); eo.put("paidTo", e.paidTo)
                    eo.put("amount", e.amount); eo.put("date", e.date)
                    eo.put("category", e.category); eo.put("attachment", e.attachment)
                    val sp = JSONObject()
                    for ((k, v) in e.split) sp.put(k, v)
                    eo.put("split", sp)
                    es.put(eo)
                }
                o.put("entries", es)
                gs.put(o)
            }
            root.put("groups", gs)
            file?.writeText(root.toString())
        } catch (e: Exception) {
        }
    }

    private fun load(root: JSONObject) {
        profileName = root.optString("profileName", "")
        profilePhoto = root.optString("profilePhoto", "")
        mode = root.optString("mode", "DARK")
        accent = root.optInt("accent", 0xFF2DD4BF.toInt())
        categories = mutableListOf()
        val ca = root.optJSONArray("categories")
        if (ca != null) for (i in 0 until ca.length()) categories.add(ca.optString(i))
        if (categories.isEmpty()) categories = DEFAULT_CATEGORIES.toMutableList()
        groups = mutableListOf()
        val gs = root.optJSONArray("groups") ?: return
        for (i in 0 until gs.length()) {
            val o = gs.getJSONObject(i)
            val g = Group(
                id = o.optString("id"), title = o.optString("title"),
                desc = o.optString("desc"), currency = o.optString("currency", "₹"),
                currencyCode = o.optString("currencyCode", "INR"),
                createdAt = o.optLong("createdAt"), archived = o.optBoolean("archived"),
                demo = o.optBoolean("demo")
            )
            val ms = o.optJSONArray("members")
            if (ms != null) for (j in 0 until ms.length()) {
                val mo = ms.getJSONObject(j)
                g.members.add(Member(mo.optString("id"), mo.optString("name")))
            }
            val es = o.optJSONArray("entries")
            if (es != null) for (j in 0 until es.length()) {
                val eo = es.getJSONObject(j)
                val e = Entry(
                    id = eo.optString("id"), kind = eo.optString("kind", "expense"),
                    title = eo.optString("title"), payer = eo.optString("payer"),
                    paidTo = eo.optString("paidTo"), amount = eo.optDouble("amount", 0.0),
                    date = eo.optLong("date"), category = eo.optString("category"),
                    attachment = eo.optString("attachment")
                )
                val sp = eo.optJSONObject("split")
                if (sp != null) {
                    val keys = sp.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        e.split[k] = sp.optDouble(k, 0.0)
                    }
                }
                g.entries.add(e)
            }
            groups.add(g)
        }
    }

    fun group(id: String): Group? = groups.firstOrNull { it.id == id }

    fun exportJson(): String {
        save()
        return file?.readText() ?: "{}"
    }

    fun importJson(text: String): Boolean {
        return try {
            load(JSONObject(text))
            save()
            true
        } catch (e: Exception) {
            false
        }
    }
}

fun round2(v: Double): Double = (v * 100).roundToLong() / 100.0

fun fmt(amount: Double): String = String.format("%,.2f", amount)

// Deon's format rule: the amount comes AFTER ₹ (₹500.00), not before it.
// Prefix currencies: ₹ £ ¥ — the rest stay suffix (50$ style).
val PREFIX_SYMBOLS = setOf("₹", "£", "¥")

fun money(sym: String, amt: Double): String =
    if (PREFIX_SYMBOLS.contains(sym)) "$sym${fmt(amt)}" else "${fmt(amt)}$sym"

fun Group.moneyOf(amt: Double): String = money(currency, amt)

// Balance math:
// expense -> payer Paid += amount; each split member Charged += share
// advance/settle -> payer Paid += amount; paidTo Charged += amount
data class BalRow(val member: Member, var paid: Double = 0.0, var charged: Double = 0.0) {
    val net: Double get() = round2(paid - charged)
}

fun balances(g: Group): List<BalRow> {
    val map = LinkedHashMap<String, BalRow>()
    for (m in g.members) map[m.id] = BalRow(m)
    for (e in g.entries) {
        val p = map[e.payer] ?: continue
        p.paid = round2(p.paid + e.amount)
        if (e.kind == "expense") {
            for ((mid, share) in e.split) {
                val r = map[mid] ?: continue
                r.charged = round2(r.charged + share)
            }
        } else {
            val t = map[e.paidTo] ?: continue
            t.charged = round2(t.charged + e.amount)
        }
    }
    return map.values.toList()
}

data class SettleMove(val from: Member, val to: Member, val amount: Double)

fun settleMoves(g: Group): List<SettleMove> {
    val rows = balances(g)
    val debtors = rows.filter { it.net < -0.004 }.map { it.member to -it.net }.toMutableList()
    val creditors = rows.filter { it.net > 0.004 }.map { it.member to it.net }.toMutableList()
    debtors.sortByDescending { it.second }
    creditors.sortByDescending { it.second }
    val out = mutableListOf<SettleMove>()
    var i = 0; var j = 0
    val dAmt = debtors.map { it.second }.toMutableList()
    val cAmt = creditors.map { it.second }.toMutableList()
    while (i < debtors.size && j < creditors.size) {
        val amt = round2(minOf(dAmt[i], cAmt[j]))
        if (amt > 0.004) out.add(SettleMove(debtors[i].first, creditors[j].first, amt))
        dAmt[i] = round2(dAmt[i] - amt)
        cAmt[j] = round2(cAmt[j] - amt)
        if (dAmt[i] <= 0.004) i++
        if (cAmt[j] <= 0.004) j++
    }
    return out
}

fun totalExpenses(g: Group): Double = round2(g.entries.filter { it.kind == "expense" }.sumOf { it.amount })
fun totalAdvanced(g: Group): Double = round2(g.entries.filter { it.kind == "advance" }.sumOf { it.amount })
fun totalSettled(g: Group): Double = round2(g.entries.filter { it.kind == "settle" }.sumOf { it.amount })
