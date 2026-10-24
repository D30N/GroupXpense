package com.deon.groupexpense

import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class CurrencyOpt(val code: String, val symbol: String, val name: String, val rateInr: Double)

val CURRENCIES = listOf(
    CurrencyOpt("INR", "₹", "Indian Rupee", 1.0),
    CurrencyOpt("USD", "$", "US Dollar", 83.5),
    CurrencyOpt("EUR", "€", "Euro", 90.5),
    CurrencyOpt("GBP", "£", "British Pound", 106.0),
    CurrencyOpt("AED", "د.إ", "UAE Dirham", 22.7),
    CurrencyOpt("SAR", "﷼", "Saudi Riyal", 22.3),
    CurrencyOpt("QAR", "﷼", "Qatari Riyal", 22.9),
    CurrencyOpt("KWD", "د.ك", "Kuwaiti Dinar", 272.0),
    CurrencyOpt("SGD", "S$", "Singapore Dollar", 62.0),
    CurrencyOpt("MYR", "RM", "Malaysian Ringgit", 17.8),
    CurrencyOpt("JPY", "¥", "Japanese Yen", 0.54),
    CurrencyOpt("AUD", "A$", "Australian Dollar", 54.0)
)

val ACCENTS = listOf(
    0xFFF8FAFC.toInt(), 0xFF111111.toInt(), 0xFF2DD4BF.toInt(),
    0xFF38BDF8.toInt(), 0xFF60A5FA.toInt(), 0xFFA78BFA.toInt(),
    0xFFF472B6.toInt(), 0xFFFB7185.toInt(), 0xFFEF4444.toInt(),
    0xFFFB923C.toInt(), 0xFFFBBF24.toInt(), 0xFFA3E635.toInt(),
    0xFF9CA3AF.toInt()
)

val CAT_ICONS = mapOf(
    "Food" to "🍔", "Travel" to "✈️", "Movie" to "🎬", "Stay" to "🏨",
    "Fuel" to "⛽", "Shopping" to "🛍️", "Bills" to "🧾", "Groceries" to "🛒",
    "Fun" to "🎉", "Health" to "💊", "Other" to "📦"
)

fun catIcon(name: String): String = CAT_ICONS[name] ?: "🏷️"

fun dateLabel(ms: Long): String {
    val now = Calendar.getInstance()
    val d = Calendar.getInstance().apply { timeInMillis = ms }
    val sameDay = now.get(Calendar.YEAR) == d.get(Calendar.YEAR) && now.get(Calendar.DAY_OF_YEAR) == d.get(Calendar.DAY_OF_YEAR)
    if (sameDay) return "Today"
    now.add(Calendar.DAY_OF_YEAR, -1)
    if (now.get(Calendar.YEAR) == d.get(Calendar.YEAR) && now.get(Calendar.DAY_OF_YEAR) == d.get(Calendar.DAY_OF_YEAR)) return "Yesterday"
    return SimpleDateFormat("d MMM yyyy", Locale.ENGLISH).format(Date(ms))
}

fun fullDate(ms: Long): String = SimpleDateFormat("d MMM yyyy", Locale.ENGLISH).format(Date(ms))

class MainActivity : Activity() {

    private lateinit var root: FrameLayout
    private val stack = ArrayDeque<() -> View>()
    private var pendingPick: ((Uri?) -> Unit)? = null
    private var openTab = 0 // 0 expenses, 1 balance, 2 share
    private var pendingAnim = 0 // 0 none, 1 push, 2 back
    private var tabAnimDir = 0
    private var summaryOpen = true
    private var settleOpen = true
    private var sortMode = 0 // 0 date, 1 amount, 2 title
    private var filterCategory: String? = null

    // drafts so navigating away (e.g. to Categories) keeps typed values
    class ExpenseDraft {
        var title = ""; var payer = ""; var amount = ""
        var date = System.currentTimeMillis()
        var category = ""; var attachment = ""
        var selected = mutableSetOf<String>()
        var custom = false
        var customAmt = mutableMapOf<String, String>()
        var editId: String? = null
    }
    class AdvanceDraft {
        var title = ""; var payer = ""; var paidTo = ""
        var date = System.currentTimeMillis(); var amount = ""
        var editId: String? = null
    }
    class GroupDraft {
        var title = ""; var desc = ""; var currency = CURRENCIES[0]
        var members = mutableListOf<Member>()
        var editId: String? = null
    }
    private var expenseDraft: ExpenseDraft? = null
    private var advanceDraft: AdvanceDraft? = null
    private var groupDraft: GroupDraft? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        P.apply(this, Store.mode, Store.accent)
        root = FrameLayout(this)
        setContentView(root)
        applyChrome()
        nav({ viewHome() }, clear = true)
    }

    private fun applyChrome() {
        root.background = bgGradient(this)
        window.statusBarColor = P.bgTop
        window.navigationBarColor = P.navBg
    }

    private fun nav(builder: () -> View, clear: Boolean = false) {
        if (clear) stack.clear()
        stack.addLast(builder)
        pendingAnim = 1
        render()
    }

    private fun render() {
        root.removeAllViews()
        val v = stack.last()()
        root.addView(v)
        if (pendingAnim != 0) {
            v.alpha = 0f
            v.translationX = dpf(56f) * pendingAnim
            v.animate().alpha(1f).translationX(0f).setDuration(240)
                .setInterpolator(android.view.animation.DecelerateInterpolator()).start()
            pendingAnim = 0
        }
    }

    private fun refresh() = render()

    private fun back() {
        if (stack.size > 1) {
            stack.removeLast()
            pendingAnim = -1
            render()
        } else finish()
    }

    override fun onBackPressed() {
        back()
    }

    private fun themedDialog(content: LinearLayout): android.app.Dialog {
        val dlg = android.app.Dialog(this)
        val card = LinearLayout(this)
        card.orientation = LinearLayout.VERTICAL
        card.background = roundRect(P.card, 22f, this, P.cardStroke, 1)
        card.setPadding(dp(20), dp(18), dp(20), dp(16))
        card.addView(content)
        dlg.setContentView(card)
        dlg.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
        dlg.setCanceledOnTouchOutside(true)
        dlg.setOnShowListener {
            val w = (resources.displayMetrics.widthPixels * 0.88).toInt()
            dlg.window?.setLayout(w, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        return dlg
    }

    private fun confirm(title: String, msg: String, okLabel: String, onOk: () -> Unit) {
        val content = LinearLayout(this)
        content.orientation = LinearLayout.VERTICAL
        content.addView(txt(this, title, 17.5f, P.ink, true))
        val m = txt(this, msg, 14f, P.sub)
        m.setPadding(0, dp(8), 0, 0)
        content.addView(m)
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.END
        row.setPadding(0, dp(18), 0, 0)
        lateinit var dlg: android.app.Dialog
        val cancel = ghostBtn(this, "Cancel")
        cancel.setOnClickListener { dlg.dismiss() }
        row.addView(cancel)
        val ok = primaryBtn(this, okLabel)
        ok.textSize = 13.5f
        val olp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        olp.leftMargin = dp(10)
        ok.layoutParams = olp
        ok.setOnClickListener { dlg.dismiss(); onOk() }
        row.addView(ok)
        content.addView(row)
        dlg = themedDialog(content)
        dlg.show()
    }

    private fun pickFromList(title: String, items: List<String>, onPick: (Int) -> Unit) {
        val content = LinearLayout(this)
        content.orientation = LinearLayout.VERTICAL
        content.addView(txt(this, title, 17.5f, P.ink, true))
        content.addView(vSpace(this, 6))
        val list = LinearLayout(this)
        list.orientation = LinearLayout.VERTICAL
        lateinit var dlg: android.app.Dialog
        items.forEachIndexed { i, item ->
            if (i > 0) list.addView(hLine(this))
            val r = txt(this, item, 15.5f, P.ink)
            r.setPadding(dp(4), dp(13), dp(4), dp(13))
            r.isClickable = true
            r.setOnClickListener { dlg.dismiss(); onPick(i) }
            list.addView(r)
        }
        val sc = ScrollView(this)
        sc.addView(list)
        val scLp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            if (items.size > 8) dp(380) else ViewGroup.LayoutParams.WRAP_CONTENT
        )
        content.addView(sc, scLp)
        dlg = themedDialog(content)
        dlg.show()
    }

    private fun uiDate(initial: Long, onPick: (Long) -> Unit) {
        val initCal = Calendar.getInstance().apply { timeInMillis = initial }
        var selY = initCal.get(Calendar.YEAR)
        var selM = initCal.get(Calendar.MONTH)
        var selD = initCal.get(Calendar.DAY_OF_MONTH)
        var viewY = selY
        var viewM = selM

        val content = LinearLayout(this)
        content.orientation = LinearLayout.VERTICAL
        content.addView(txt(this, "Pick a date", 16.5f, P.ink, true))
        content.addView(vSpace(this, 10))

        val navRow = LinearLayout(this)
        navRow.orientation = LinearLayout.HORIZONTAL
        navRow.gravity = Gravity.CENTER_VERTICAL
        val prev = txt(this, "\u2039", 24f, P.accent, true)
        prev.gravity = Gravity.CENTER
        val monthLabel = txt(this, "", 15f, P.ink, true)
        monthLabel.gravity = Gravity.CENTER
        val next = txt(this, "\u203A", 24f, P.accent, true)
        next.gravity = Gravity.CENTER
        navRow.addView(prev, LinearLayout.LayoutParams(dp(36), dp(36)))
        navRow.addView(monthLabel, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        navRow.addView(next, LinearLayout.LayoutParams(dp(36), dp(36)))
        content.addView(navRow)
        content.addView(vSpace(this, 6))

        val wdRow = LinearLayout(this)
        wdRow.orientation = LinearLayout.HORIZONTAL
        for (d in listOf("S", "M", "T", "W", "T", "F", "S")) {
            val t = txt(this, d, 11f, P.faint, true)
            t.gravity = Gravity.CENTER
            wdRow.addView(t, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        content.addView(wdRow)
        content.addView(vSpace(this, 4))

        val grid = LinearLayout(this)
        grid.orientation = LinearLayout.VERTICAL
        content.addView(grid)

        val selLabel = txt(this, "", 13.5f, P.accent, true)
        selLabel.gravity = Gravity.CENTER
        selLabel.setPadding(0, dp(10), 0, 0)
        content.addView(selLabel)

        fun selMillis(): Long {
            val c = Calendar.getInstance()
            c.set(selY, selM, selD, initCal.get(Calendar.HOUR_OF_DAY), initCal.get(Calendar.MINUTE), 0)
            c.set(Calendar.MILLISECOND, 0)
            return c.timeInMillis
        }
        fun buildGrid() {
            grid.removeAllViews()
            val first = Calendar.getInstance()
            first.set(viewY, viewM, 1)
            val offset = first.get(Calendar.DAY_OF_WEEK) - 1
            val days = first.getActualMaximum(Calendar.DAY_OF_MONTH)
            val today = Calendar.getInstance()
            val monthNames = arrayOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
            monthLabel.text = "${monthNames[viewM]} $viewY"
            selLabel.text = fullDate(selMillis())
            var day = 1
            for (r in 0 until 6) {
                val rowL = LinearLayout(this)
                rowL.orientation = LinearLayout.HORIZONTAL
                for (cIdx in 0 until 7) {
                    val cell = txt(this, "", 13.5f, P.ink)
                    cell.gravity = Gravity.CENTER
                    val idx = r * 7 + cIdx
                    if (idx >= offset && day <= days) {
                        val dNum = day
                        cell.text = dNum.toString()
                        val isSel = viewY == selY && viewM == selM && dNum == selD
                        val isToday = viewY == today.get(Calendar.YEAR) && viewM == today.get(Calendar.MONTH) && dNum == today.get(Calendar.DAY_OF_MONTH)
                        if (isSel) {
                            cell.setTextColor(P.onAccent)
                            cell.typeface = android.graphics.Typeface.DEFAULT_BOLD
                            cell.background = GradientDrawable().apply {
                                shape = GradientDrawable.OVAL
                                setColor(P.accent)
                            }
                        } else if (isToday) {
                            cell.background = GradientDrawable().apply {
                                shape = GradientDrawable.OVAL
                                setStroke(dp(1), P.accent)
                            }
                        }
                        cell.isClickable = true
                        cell.setOnClickListener {
                            selY = viewY
                            selM = viewM
                            selD = dNum
                            buildGrid()
                        }
                        day++
                    }
                    rowL.addView(cell, LinearLayout.LayoutParams(0, dp(34), 1f))
                }
                grid.addView(rowL)
                if (day > days) break
            }
        }
        prev.isClickable = true
        next.isClickable = true
        prev.setOnClickListener {
            viewM--
            if (viewM < 0) {
                viewM = 11
                viewY--
            }
            buildGrid()
        }
        next.setOnClickListener {
            viewM++
            if (viewM > 11) {
                viewM = 0
                viewY++
            }
            buildGrid()
        }
        buildGrid()

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.END
        row.setPadding(0, dp(12), 0, 0)
        lateinit var dlg: android.app.Dialog
        val cancel = ghostBtn(this, "Cancel")
        cancel.setOnClickListener { dlg.dismiss() }
        row.addView(cancel)
        val ok = primaryBtn(this, "SET")
        ok.textSize = 13f
        val olp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        olp.leftMargin = dp(10)
        ok.layoutParams = olp
        ok.setOnClickListener {
            dlg.dismiss()
            onPick(selMillis())
        }
        row.addView(ok)
        content.addView(row)
        dlg = themedDialog(content)
        dlg.show()
    }

    private fun pickImage(cb: (Uri?) -> Unit) {
        pendingPick = cb
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
        i.addCategory(Intent.CATEGORY_OPENABLE)
        i.type = "image/*"
        i.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        startActivityForResult(i, 701)
    }

    private fun openDoc(cb: (Uri?) -> Unit) {
        pendingPick = cb
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
        i.addCategory(Intent.CATEGORY_OPENABLE)
        i.type = "*/*"
        startActivityForResult(i, 702)
    }

    private fun createDoc(fileName: String, cb: (Uri?) -> Unit) {
        pendingPick = cb
        val i = Intent(Intent.ACTION_CREATE_DOCUMENT)
        i.addCategory(Intent.CATEGORY_OPENABLE)
        i.type = "*/*"
        i.putExtra(Intent.EXTRA_TITLE, fileName)
        startActivityForResult(i, 703)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode in 701..703) {
            val uri = if (resultCode == RESULT_OK) data?.data else null
            if (requestCode == 701 && uri != null) {
                try {
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (e: Exception) {
                }
            }
            pendingPick?.invoke(uri)
            pendingPick = null
        }
    }

    private fun scrollOf(content: LinearLayout): ScrollView {
        val s = ScrollView(this)
        s.addView(content)
        return s
    }

    private fun pageColumn(): LinearLayout {
        val l = LinearLayout(this)
        l.orientation = LinearLayout.VERTICAL
        l.setPadding(dp(16), dp(6), dp(16), dp(20))
        return l
    }

    private fun topBar(title: String?, onClose: () -> Unit, saveLabel: String?, onSave: (() -> Unit)?): LinearLayout {
        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL
        bar.setPadding(dp(14), dp(12), dp(14), dp(4))
        val x = txt(this, "✕", 19f, P.ink, false)
        x.gravity = Gravity.CENTER
        x.background = roundRect(P.field, 12f, this, P.fieldStroke, 1)
        x.setOnClickListener { onClose() }
        bar.addView(x, LinearLayout.LayoutParams(dp(38), dp(38)))
        if (title != null) {
            val t = txt(this, title, 19f, P.ink, true)
            t.setPadding(dp(12), 0, 0, 0)
            bar.addView(t)
        }
        if (saveLabel != null && onSave != null) {
            val spacer = View(this)
            bar.addView(spacer, LinearLayout.LayoutParams(0, 1, 1f))
            val sv = primaryBtn(this, saveLabel)
            sv.textSize = 13f
            sv.setPadding(dp(18), dp(9), dp(18), dp(9))
            sv.setOnClickListener { onSave() }
            bar.addView(sv)
        }
        return bar
    }

    // ---------------- HOME ----------------

    private fun viewHome(): View {
        val col = pageColumn()
        val top = LinearLayout(this)
        top.orientation = LinearLayout.HORIZONTAL
        top.gravity = Gravity.CENTER_VERTICAL
        val menuBtn = iconBtn(this, R.drawable.ic_more, P.ink)
        menuBtn.setOnClickListener { showDrawer() }
        top.addView(menuBtn)
        val sp = View(this); top.addView(sp, LinearLayout.LayoutParams(0, 1, 1f))
        val prof = profilePhotoView(38)
        prof.setOnClickListener { nav({ viewSettings(null) }) }
        top.addView(prof)
        col.addView(top)

        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val greet = when {
            hour < 12 -> "Good Morning"
            hour < 17 -> "Good Afternoon"
            else -> "Good Evening"
        }
        val g = txt(this, greet, 26f, P.ink, true)
        g.setPadding(0, dp(18), 0, 0)
        col.addView(g)
        col.addView(txt(this, "Your groups at a glance", 14f, P.sub))

        val createRow = LinearLayout(this)
        createRow.orientation = LinearLayout.HORIZONTAL
        createRow.gravity = Gravity.END
        val create = primaryBtn(this, "+  Create Group")
        create.background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(P.lighten(P.accent, 0.15f), P.accentDark)).apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadii = floatArrayOf(dpf(16f), dpf(16f), 0f, 0f, 0f, 0f, dpf(16f), dpf(16f))
        }
        val clp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        clp.topMargin = dp(16); clp.rightMargin = -dp(16)
        create.layoutParams = clp
        create.setPadding(dp(20), dp(13), dp(30), dp(13))
        create.setOnClickListener {
            groupDraft = GroupDraft()
            nav({ viewGroupForm() })
        }
        createRow.addView(create)
        col.addView(createRow)

        val lbl = txt(this, "YOUR GROUPS", 12f, P.sub, true)
        lbl.setPadding(dp(2), dp(22), 0, dp(10))
        col.addView(lbl)

        val active = Store.groups.filter { !it.archived }
        if (active.isEmpty()) {
            val empty = cardBox(this)
            empty.addView(txt(this, "No groups yet", 15f, P.ink, true))
            empty.addView(txt(this, "Tap “Create Group” to start your first group", 13f, P.sub))
            col.addView(empty)
        }
        active.forEachIndexed { idx, grp ->
            val card = cardBox(this)
            card.orientation = LinearLayout.HORIZONTAL
            card.gravity = Gravity.CENTER_VERTICAL
            val tile = txt(this, "👥", 22f, P.ink)
            tile.gravity = Gravity.CENTER
            tile.background = roundRect(P.field, 15f, this, P.cardStroke, 1)
            card.addView(tile, LinearLayout.LayoutParams(dp(50), dp(50)))
            val texts = LinearLayout(this)
            texts.orientation = LinearLayout.VERTICAL
            texts.setPadding(dp(13), 0, 0, 0)
            texts.addView(txt(this, grp.title, 16f, P.ink, true))
            texts.addView(txt(this, grp.desc.ifBlank { "${grp.members.size} members · ${grp.currencyCode}" }, 12.5f, P.sub))
            card.addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            if (grp.demo) {
                val badge = txt(this, "Demo", 11f, P.accent, true)
                badge.background = roundRect(Color.TRANSPARENT, 99f, this, P.accent, 1)
                badge.setPadding(dp(10), dp(4), dp(10), dp(4))
                card.addView(badge)
            }
            card.setOnClickListener {
                openTab = 0
                nav({ viewGroup(grp.id) })
            }
            card.setOnLongClickListener {
                groupActions(grp)
                true
            }
            animateIn(card, idx * 70L)
            val wrap = LinearLayout(this)
            wrap.orientation = LinearLayout.VERTICAL
            wrap.setPadding(0, 0, 0, dp(10))
            wrap.addView(card)
            col.addView(wrap)
        }
        return scrollOf(col)
    }

    private fun groupActions(grp: Group) {
        val items = listOf("Edit group", if (grp.archived) "Unarchive" else "Archive", "Delete group")
        pickFromList(grp.title, items) { which ->
            when (which) {
                0 -> {
                    groupDraft = GroupDraft().apply {
                        title = grp.title; desc = grp.desc
                        currency = CURRENCIES.firstOrNull { it.code == grp.currencyCode } ?: CURRENCIES[0]
                        members = grp.members.map { it.copy() }.toMutableList()
                        editId = grp.id
                    }
                    nav({ viewGroupForm() })
                }
                1 -> {
                    grp.archived = !grp.archived
                    Store.save()
                    refresh()
                }
                2 -> confirm("Delete group?", "“${grp.title}” and all its expenses will be removed.", "Delete") {
                    Store.groups.remove(grp)
                    Store.save()
                    refresh()
                }
            }
        }
    }

    private fun showDrawer() {
        val overlay = FrameLayout(this)
        overlay.setBackgroundColor(0x99000000.toInt())
        val panel = LinearLayout(this)
        panel.orientation = LinearLayout.VERTICAL
        panel.background = roundRect(P.card, 0f, this)
        panel.setPadding(dp(18), dp(22), dp(18), dp(18))
        val pw = (resources.displayMetrics.widthPixels * 0.78).toInt()
        val plp = FrameLayout.LayoutParams(pw, ViewGroup.LayoutParams.MATCH_PARENT)
        panel.layoutParams = plp
        panel.addView(txt(this, "Group Xpense", 19f, P.ink, true))
        panel.addView(txt(this, Store.profileName.ifBlank { "Welcome" }, 13f, P.sub))
        panel.addView(vSpace(this, 14))
        panel.addView(hLine(this))
        val items = listOf(
            "👤  Profile & Settings" to { nav({ viewSettings(null) }) },
            "🗄  Archived Groups" to { nav({ viewArchive() }) },
            "🏷  Categories" to { nav({ viewCategories() }) },
            "💱  Currency Converter" to { nav({ viewConverter() }) }
        )
        for ((label, fn) in items) {
            val row = txt(this, label, 15f, P.ink)
            row.setPadding(dp(4), dp(15), dp(4), dp(15))
            row.isClickable = true
            row.setOnClickListener {
                root.removeView(overlay)
                fn()
            }
            panel.addView(row)
            panel.addView(hLine(this))
        }
        panel.addView(vSpace(this, 18))
        panel.addView(txt(this, "Fully on-device · No ads · No sign-up", 12f, P.faint))
        overlay.addView(panel)
        overlay.setOnClickListener { root.removeView(overlay) }
        panel.translationX = -pw.toFloat()
        root.addView(overlay)
        panel.animate().translationX(0f).setDuration(260).start()
    }

    // ---------------- GROUP FORM ----------------

    private fun labelRow(label: String, field: View): LinearLayout {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        val l = txt(this, label, 13.5f, P.sub)
        row.addView(l, LinearLayout.LayoutParams(dp(94), ViewGroup.LayoutParams.WRAP_CONTENT))
        row.addView(field, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        return row
    }

    private fun selectorRow(value: String, onClick: () -> Unit): TextView {
        val t = txt(this, value, 15f, P.ink)
        t.background = roundRect(P.field, 13f, this, P.fieldStroke, 1)
        t.setPadding(dp(14), dp(12), dp(14), dp(12))
        t.isClickable = true
        t.setOnClickListener { onClick() }
        return t
    }

    private fun viewGroupForm(): View {
        val d = groupDraft ?: GroupDraft().also { groupDraft = it }
        val rootCol = LinearLayout(this)
        rootCol.orientation = LinearLayout.VERTICAL
        rootCol.addView(topBar(if (d.editId == null) "Create Group" else "Edit Group", {
            groupDraft = null
            back()
        }, "SAVE") { saveGroupForm() })
        val col = pageColumn()

        val card1 = cardBox(this)
        val titleF = fieldBox(this, "e.g. Holiday Trip")
        titleF.setText(d.title)
        titleF.addTextChangedListener(simpleWatcher { d.title = it })
        card1.addView(txt(this, "TITLE", 11f, P.sub, true))
        card1.addView(vSpace(this, 6)); card1.addView(titleF)
        card1.addView(vSpace(this, 14))
        card1.addView(txt(this, "CURRENCY", 11f, P.sub, true))
        card1.addView(vSpace(this, 6))
        val curSel = selectorRow("${d.currency.symbol}  ·  ${d.currency.code} — ${d.currency.name}") {
            pickFromList("Currency", CURRENCIES.map { "${it.symbol}  ${it.code} — ${it.name}" }) { w ->
                d.currency = CURRENCIES[w]
                refresh()
            }
        }
        card1.addView(curSel)
        card1.addView(vSpace(this, 14))
        card1.addView(txt(this, "DESCRIPTION", 11f, P.sub, true))
        card1.addView(vSpace(this, 6))
        val descF = fieldBox(this, "What is this group for?")
        descF.setText(d.desc)
        descF.addTextChangedListener(simpleWatcher { d.desc = it })
        card1.addView(descF)
        col.addView(card1)
        col.addView(vSpace(this, 14))

        val card2 = cardBox(this)
        card2.addView(txt(this, "Participants", 16f, P.ink, true))
        card2.addView(vSpace(this, 12))
        val addRow = LinearLayout(this)
        addRow.orientation = LinearLayout.HORIZONTAL
        addRow.gravity = Gravity.CENTER_VERTICAL
        val nameF = fieldBox(this, if (d.members.isEmpty()) "Your name" else "Participant name")
        addRow.addView(nameF, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val addBtn = TextView(this)
        addBtn.text = "＋👤"
        addBtn.textSize = 15f
        addBtn.gravity = Gravity.CENTER
        addBtn.background = gradAccent(this, 13f)
        val abLp = LinearLayout.LayoutParams(dp(48), dp(48))
        abLp.leftMargin = dp(10)
        addBtn.layoutParams = abLp
        addBtn.isClickable = true
        addBtn.setOnClickListener {
            val n = nameF.text.toString().trim()
            if (n.isEmpty()) {
                toast("Type a name first")
            } else if (d.members.any { it.name.equals(n, true) }) {
                toast("“$n” is already added")
            } else {
                d.members.add(Member(Store.newId(), n))
                if (Store.profileName.isBlank() && d.editId == null && d.members.size == 1) {
                    Store.profileName = n
                    Store.save()
                }
                refresh()
            }
        }
        addRow.addView(addBtn)
        card2.addView(addRow)
        card2.addView(vSpace(this, 8))
        val existing = d.editId?.let { Store.group(it) }
        d.members.forEachIndexed { idx, m ->
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            row.setPadding(dp(6), dp(7), dp(6), dp(7))
            row.addView(avatarView(this, m.name, 32))
            val nm = txt(this, m.name, 14.5f, P.ink)
            nm.setPadding(dp(10), 0, 0, 0)
            row.addView(nm, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            val rm = txt(this, "✕", 13f, P.faint)
            rm.setPadding(dp(8), dp(6), dp(8), dp(6))
            rm.isClickable = true
            rm.setOnClickListener {
                val used = existing?.entries?.any { e ->
                    e.payer == m.id || e.paidTo == m.id || e.split.containsKey(m.id)
                } ?: false
                if (used) {
                    toast("This member has entries and can't be removed")
                } else {
                    d.members.removeAt(idx)
                    refresh()
                }
            }
            row.addView(rm)
            card2.addView(row)
            animateIn(row, idx * 40L)
        }
        if (d.members.isEmpty()) {
            card2.addView(txt(this, "No participants yet — type a name above and tap add.", 12.5f, P.faint))
        }
        col.addView(card2)
        rootCol.addView(scrollOf(col))
        return rootCol
    }

    private fun saveGroupForm() {
        val d = groupDraft ?: return
        if (d.title.trim().isEmpty()) {
            toast("Give the group a title")
            return
        }
        if (d.members.isEmpty()) {
            toast("Add at least one participant")
            return
        }
        if (d.editId == null) {
            val g = Group(
                id = Store.newId(), title = d.title.trim(), desc = d.desc.trim(),
                currency = d.currency.symbol, currencyCode = d.currency.code,
                createdAt = System.currentTimeMillis(),
                members = d.members
            )
            Store.groups.add(g)
        } else {
            val g = Store.group(d.editId!!) ?: return
            g.title = d.title.trim(); g.desc = d.desc.trim()
            g.currency = d.currency.symbol; g.currencyCode = d.currency.code
            g.members = d.members
            g.demo = false
        }
        Store.save()
        groupDraft = null
        back()
    }

    private fun simpleWatcher(after: (String) -> Unit): android.text.TextWatcher {
        return object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                after(s?.toString() ?: "")
            }
        }
    }

    // ---------------- GROUP SCREEN ----------------

    private fun viewGroup(gid: String): View {
        val g = Store.group(gid) ?: return viewHome()
        val frame = FrameLayout(this)
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL

        val header = LinearLayout(this)
        header.orientation = LinearLayout.HORIZONTAL
        header.gravity = Gravity.CENTER_VERTICAL
        header.setPadding(dp(16), dp(12), dp(16), dp(6))
        header.addView(txt(this, g.title, 20f, P.ink, true))
        val hsp = View(this); header.addView(hsp, LinearLayout.LayoutParams(0, 1, 1f))
        if (openTab == 0) {
            val sortBtn = iconBtn(this, R.drawable.ic_sort, P.ink)
            sortBtn.setOnClickListener { sortFilterDialog(g) }
            header.addView(sortBtn)
        }
        col.addView(header)

        val content = when (openTab) {
            1 -> balanceTab(g)
            2 -> shareTab(g)
            else -> expensesTab(g)
        }
        val sc = ScrollView(this)
        sc.addView(content)
        if (tabAnimDir != 0) {
            sc.alpha = 0f
            sc.translationX = dpf(36f) * tabAnimDir
            sc.animate().alpha(1f).translationX(0f).setDuration(250)
                .setInterpolator(android.view.animation.DecelerateInterpolator()).start()
            tabAnimDir = 0
        }
        col.addView(sc, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        col.addView(bottomNav(g))
        frame.addView(col)

        if (openTab == 0) {
            frame.addView(fabDial(g))
        }
        return frame
    }

    private fun bottomNav(g: Group): View {
        val wrap = LinearLayout(this)
        wrap.orientation = LinearLayout.VERTICAL
        val top = View(this)
        top.setBackgroundColor(P.cardStroke)
        wrap.addView(top, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)))
        val nav = LinearLayout(this)
        nav.orientation = LinearLayout.HORIZONTAL
        nav.gravity = Gravity.CENTER_VERTICAL
        nav.background = roundRect(P.navBg, 0f, this)
        nav.setPadding(dp(2), dp(8), dp(2), dp(10))
        wrap.addView(nav)
        val tabs = listOf(
            Triple(R.drawable.ic_receipt, "Expenses", 0),
            Triple(R.drawable.ic_balance, "Balance", 1),
            Triple(R.drawable.ic_share, "Share", 2),
            Triple(R.drawable.ic_more, "More", 3)
        )
        for ((icon, label, idx) in tabs) {
            val active = openTab == idx
            val item = LinearLayout(this)
            item.orientation = LinearLayout.VERTICAL
            item.gravity = Gravity.CENTER
            item.setPadding(0, dp(3), 0, dp(3))
            val iconWrap = FrameLayout(this)
            if (active) {
                val glow = View(this)
                val gd = GradientDrawable()
                gd.shape = GradientDrawable.OVAL
                gd.setColor((P.accent and 0x00FFFFFF) or 0x2E000000)
                glow.background = gd
                val glp = FrameLayout.LayoutParams(dp(48), dp(32))
                glp.gravity = Gravity.CENTER
                glow.layoutParams = glp
                iconWrap.addView(glow)
            }
            val iv = ImageView(this)
            iv.setImageResource(icon)
            iv.setColorFilter(if (active) P.accent else P.faint)
            val ivLp = FrameLayout.LayoutParams(dp(27), dp(27))
            ivLp.gravity = Gravity.CENTER
            iv.layoutParams = ivLp
            iconWrap.addView(iv)
            item.addView(iconWrap, LinearLayout.LayoutParams(dp(48), dp(32)))
            val lb = txt(this, label, 11.5f, if (active) P.accent else P.faint, active)
            lb.gravity = Gravity.CENTER
            lb.setPadding(0, dp(3), 0, 0)
            item.addView(lb)
            item.isClickable = true
            item.setOnClickListener {
                if (idx == 3) {
                    nav({ viewSettings(g.id) })
                } else if (idx != openTab) {
                    tabAnimDir = if (idx > openTab) 1 else -1
                    iv.animate().scaleX(1.3f).scaleY(1.3f).setDuration(110).withEndAction {
                        iv.animate().scaleX(1f).scaleY(1f).setDuration(150).start()
                    }.start()
                    item.postDelayed({
                        openTab = idx
                        refresh()
                    }, 120)
                }
            }
            nav.addView(item, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        return wrap
    }

    private fun sortFilterDialog(g: Group) {
        val items = mutableListOf<String>()
        items.add("⇅  Sort by date")
        items.add("⇅  Sort by amount")
        items.add("⇅  Sort by title")
        items.add("🏷  Filter: All categories")
        for (c in Store.categories) items.add("${catIcon(c)}  Filter: $c")
        if (filterCategory != null) items.add("✕  Clear filter")
        pickFromList("Sort & filter", items) { w ->
            when {
                w == 0 -> sortMode = 0
                w == 1 -> sortMode = 1
                w == 2 -> sortMode = 2
                w == 3 -> filterCategory = null
                w == items.size - 1 && filterCategory != null -> filterCategory = null
                else -> filterCategory = Store.categories[w - 4]
            }
            refresh()
        }
    }

    private fun expensesTab(g: Group): View {
        val col = pageColumn()
        var entries = g.entries.toList()
        if (filterCategory != null) {
            entries = entries.filter { it.kind == "expense" && it.category == filterCategory }
        }
        entries = when (sortMode) {
            1 -> entries.sortedByDescending { it.amount }
            2 -> entries.sortedBy { it.title.lowercase() }
            else -> entries.sortedByDescending { it.date }
        }
        if (entries.isEmpty()) {
            val empty = LinearLayout(this)
            empty.orientation = LinearLayout.VERTICAL
            empty.gravity = Gravity.CENTER
            empty.setPadding(dp(30), dp(90), dp(30), 0)
            val ring = FrameLayout(this)
            ring.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(P.field)
                setStroke(dp(1), P.accent)
            }
            val iv = ImageView(this)
            iv.setImageResource(R.drawable.ic_receipt)
            iv.setColorFilter(P.accent)
            val ivLp = FrameLayout.LayoutParams(dp(46), dp(46))
            ivLp.gravity = Gravity.CENTER
            ring.addView(iv, ivLp)
            empty.addView(ring, LinearLayout.LayoutParams(dp(110), dp(110)))
            val h = txt(this, if (filterCategory != null) "Nothing here" else "No Expenses yet.", 18f, P.ink, true)
            h.gravity = Gravity.CENTER
            h.setPadding(0, dp(18), 0, 0)
            empty.addView(h)
            val p = txt(this, if (filterCategory != null) "No expenses in this category" else "Tap '+' button below to add", 13.5f, P.sub)
            p.gravity = Gravity.CENTER
            empty.addView(p)
            col.addView(empty)
            return col
        }
        val card = cardBox(this)
        card.setPadding(dp(16), dp(6), dp(16), dp(6))
        entries.forEachIndexed { i, e ->
            if (i > 0) card.addView(hLine(this))
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            row.setPadding(0, dp(12), 0, dp(12))
            val dim = e.kind != "expense"
            val left = LinearLayout(this)
            left.orientation = LinearLayout.VERTICAL
            left.addView(txt(this, e.title, 15.5f, if (dim) P.sub else P.ink, true))
            val sub = when (e.kind) {
                "advance" -> "Advanced payment by ${g.memberName(e.payer)}"
                "settle" -> "Settlement by ${g.memberName(e.payer)}"
                else -> "Paid by ${g.memberName(e.payer)}"
            }
            left.addView(txt(this, sub + if (e.attachment.isNotEmpty()) "  ·  📎 receipt" else "", 12f, P.faint))
            row.addView(left, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            val right = LinearLayout(this)
            right.orientation = LinearLayout.VERTICAL
            right.gravity = Gravity.END
            val amt = txt(this, g.moneyOf(e.amount), 15f, if (dim) P.sub else P.ink, true)
            amt.gravity = Gravity.END
            right.addView(amt)
            val dt = txt(this, dateLabel(e.date), 11.5f, P.faint)
            dt.gravity = Gravity.END
            right.addView(dt)
            row.addView(right)
            row.isClickable = true
            row.setOnClickListener { openEntryEditor(g, e) }
            row.setOnLongClickListener {
                confirm("Delete entry?", "“${e.title}” (${g.moneyOf(e.amount)}) will be removed.", "Delete") {
                    g.entries.remove(e)
                    Store.save()
                    refresh()
                }
                true
            }
            card.addView(row)
        }
        col.addView(card)
        return col
    }

    private fun openEntryEditor(g: Group, e: Entry) {
        if (e.kind == "expense") {
            expenseDraft = ExpenseDraft().apply {
                title = e.title; payer = e.payer; amount = e.amount.toString()
                date = e.date; category = e.category; attachment = e.attachment
                selected = e.split.keys.toMutableSet()
                editId = e.id
            }
            nav({ viewExpenseForm(g.id) })
        } else {
            advanceDraft = AdvanceDraft().apply {
                title = e.title; payer = e.payer; paidTo = e.paidTo
                amount = e.amount.toString(); date = e.date; editId = e.id
            }
            nav({ viewAdvanceForm(g.id) })
        }
    }

    private fun fabDial(g: Group): View {
        val wrap = FrameLayout(this)
        val scrim = View(this)
        scrim.setBackgroundColor(0x73000000)
        scrim.visibility = View.GONE
        scrim.setOnClickListener { refresh() }
        wrap.addView(scrim, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        val dial = LinearLayout(this)
        dial.orientation = LinearLayout.VERTICAL
        dial.gravity = Gravity.END
        dial.setPadding(dp(16), 0, dp(16), dp(80))
        val dlp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        dlp.gravity = Gravity.BOTTOM
        dial.layoutParams = dlp

        var open = false
        val subBox = LinearLayout(this)
        subBox.orientation = LinearLayout.VERTICAL
        subBox.gravity = Gravity.END
        subBox.visibility = View.GONE
        subBox.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        val rowsList = mutableListOf<LinearLayout>()

        fun subRow(title: String, sub: String, delay: Long, action: () -> Unit): LinearLayout {
            val r = LinearLayout(this)
            r.orientation = LinearLayout.HORIZONTAL
            r.gravity = Gravity.END or Gravity.CENTER_VERTICAL
            r.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(12)
            }
            val card = LinearLayout(this)
            card.orientation = LinearLayout.VERTICAL
            card.gravity = Gravity.END
            card.minimumWidth = dp(220)
            card.background = roundRect(P.card, 14f, this, P.cardStroke, 1)
            card.setPadding(dp(15), dp(10), dp(15), dp(10))
            val t = txt(this, title, 14.5f, P.ink, true)
            t.gravity = Gravity.END
            val s = txt(this, sub, 11.5f, P.sub)
            s.gravity = Gravity.END
            card.addView(t); card.addView(s)
            card.isClickable = true
            card.setOnClickListener { action() }
            r.addView(card)
            val mini = txt(this, "+", 20f, P.accent, true)
            mini.gravity = Gravity.CENTER
            mini.background = roundRect(P.card, 99f, this, P.accent, 1)
            val mlp = LinearLayout.LayoutParams(dp(44), dp(44))
            mlp.leftMargin = dp(10)
            mini.layoutParams = mlp
            mini.isClickable = true
            mini.setOnClickListener { action() }
            r.addView(mini)
            rowsList.add(r)
            return r
        }

        subBox.addView(subRow("Advance Payment", "Pay in advance for future costs", 60) {
            advanceDraft = AdvanceDraft().apply {
                payer = g.members.firstOrNull()?.id ?: ""
                paidTo = g.members.getOrNull(1)?.id ?: payer
            }
            nav({ viewAdvanceForm(g.id) })
        })
        subBox.addView(subRow("Add Expense", "A purchase made for the group", 0) {
            expenseDraft = ExpenseDraft().apply {
                payer = g.members.firstOrNull()?.id ?: ""
                selected = g.members.map { it.id }.toMutableSet()
            }
            nav({ viewExpenseForm(g.id) })
        })
        dial.addView(subBox)

        val fab = txt(this, "+", 27f, P.onAccent, true)
        fab.gravity = Gravity.CENTER
        fab.background = gradAccent(this, 99f)
        val flp = LinearLayout.LayoutParams(dp(58), dp(58))
        flp.gravity = Gravity.END
        fab.layoutParams = flp
        fab.isClickable = true
        fab.setOnClickListener {
            open = !open
            if (open) {
                subBox.visibility = View.VISIBLE
                scrim.alpha = 0f
                scrim.visibility = View.VISIBLE
                scrim.animate().alpha(1f).setDuration(220).start()
                fab.animate().rotation(45f).setDuration(240)
                    .setInterpolator(android.view.animation.DecelerateInterpolator()).start()
                rowsList.forEachIndexed { i, row ->
                    row.alpha = 0f
                    row.translationY = dpf(26f)
                    row.scaleX = 0.92f
                    row.scaleY = 0.92f
                    row.animate().alpha(1f).translationY(0f).scaleX(1f).scaleY(1f)
                        .setStartDelay(((rowsList.size - 1 - i) * 55).toLong())
                        .setDuration(260)
                        .setInterpolator(android.view.animation.DecelerateInterpolator())
                        .start()
                }
            } else {
                fab.animate().rotation(0f).setDuration(200).start()
                scrim.animate().alpha(0f).setDuration(180).start()
                rowsList.forEach { row ->
                    row.animate().alpha(0f).translationY(dpf(14f)).setDuration(150).start()
                }
                subBox.postDelayed({
                    if (!open) {
                        subBox.visibility = View.GONE
                        scrim.visibility = View.GONE
                    }
                }, 170)
            }
        }
        dial.addView(fab)
        wrap.addView(dial)
        return wrap
    }

    // ---------------- EXPENSE FORM ----------------

    private fun viewExpenseForm(gid: String): View {
        val g = Store.group(gid) ?: return viewHome()
        val d = expenseDraft ?: ExpenseDraft().also { expenseDraft = it }
        if (d.payer.isEmpty()) d.payer = g.members.firstOrNull()?.id ?: ""
        val rootCol = LinearLayout(this)
        rootCol.orientation = LinearLayout.VERTICAL
        rootCol.addView(topBar(null, {
            expenseDraft = null
            back()
        }, "SAVE") { saveExpenseForm(g) })
        val col = pageColumn()

        val rowAmt = mutableMapOf<String, TextView>()
        val customFields = mutableMapOf<String, EditText>()

        fun equalShares(): Map<String, Double> {
            val amt = d.amount.toDoubleOrNull() ?: 0.0
            val sel = g.members.filter { d.selected.contains(it.id) }
            if (sel.isEmpty() || amt <= 0) return sel.associate { it.id to 0.0 }
            val base = round2(amt / sel.size)
            val out = mutableMapOf<String, Double>()
            var acc = 0.0
            sel.forEachIndexed { i, m ->
                if (i == sel.size - 1) {
                    out[m.id] = round2(amt - acc)
                } else {
                    out[m.id] = base
                    acc = round2(acc + base)
                }
            }
            return out
        }
        fun recompute() {
            val sh = equalShares()
            for ((mid, tv) in rowAmt) tv.text = g.moneyOf(sh[mid] ?: 0.0)
        }

        val card = cardBox(this)
        val whatF = fieldBox(this, "e.g Food, Transport etc.")
        whatF.setText(d.title)
        whatF.addTextChangedListener(simpleWatcher { d.title = it })
        card.addView(labelRow("What?", whatF))
        card.addView(vSpace(this, 12))
        val paidSel = selectorRow(g.memberName(d.payer) + "  ▾") {
            pickFromList("Who paid?", g.members.map { it.name }) { w ->
                d.payer = g.members[w].id
                refresh()
            }
        }
        card.addView(labelRow("Who paid?", paidSel))
        card.addView(vSpace(this, 12))
        val amtF = fieldBox(this, "0.00")
        amtF.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        amtF.setText(d.amount)
        amtF.addTextChangedListener(simpleWatcher {
            d.amount = it
            if (!d.custom) recompute()
        })
        val amtRow = LinearLayout(this)
        amtRow.orientation = LinearLayout.HORIZONTAL
        amtRow.gravity = Gravity.CENTER_VERTICAL
        amtRow.addView(amtF, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val curLbl = txt(this, " ${g.currency}", 16f, P.sub, true)
        amtRow.addView(curLbl)
        card.addView(labelRow("How much?", amtRow))
        col.addView(card)
        col.addView(vSpace(this, 12))

        // toolbar: Attachment / Category / Date
        val tools = cardBox(this)
        tools.orientation = LinearLayout.HORIZONTAL
        tools.setPadding(dp(8), dp(12), dp(8), dp(12))
        fun toolBtn(icon: Int, label: String, fn: () -> Unit): LinearLayout {
            val b = LinearLayout(this)
            b.orientation = LinearLayout.VERTICAL
            b.gravity = Gravity.CENTER
            b.setPadding(0, dp(4), 0, dp(4))
            val iv = ImageView(this)
            iv.setImageResource(icon)
            iv.setColorFilter(P.accent)
            val ivLp = LinearLayout.LayoutParams(dp(25), dp(25))
            ivLp.gravity = Gravity.CENTER_HORIZONTAL
            b.addView(iv, ivLp)
            val lb = txt(this, label, 12f, P.ink)
            lb.gravity = Gravity.CENTER
            lb.setPadding(0, dp(6), 0, 0)
            b.addView(lb, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            b.isClickable = true
            b.setOnClickListener { fn() }
            return b
        }
        val attBtn = toolBtn(R.drawable.ic_camera, if (d.attachment.isEmpty()) "Attachment" else "Receipt ✓") {
            if (d.attachment.isEmpty()) {
                pickImage { uri ->
                    if (uri != null) {
                        d.attachment = uri.toString()
                        refresh()
                    }
                }
            } else {
                pickFromList("Receipt", listOf("View receipt", "Change receipt", "Remove receipt")) { w ->
                    when (w) {
                        0 -> {
                            val i = Intent(Intent.ACTION_VIEW)
                            i.setDataAndType(Uri.parse(d.attachment), "image/*")
                            i.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                            try { startActivity(i) } catch (e: Exception) { toast("Couldn't open the receipt") }
                        }
                        1 -> pickImage { uri -> if (uri != null) { d.attachment = uri.toString(); refresh() } }
                        2 -> { d.attachment = ""; refresh() }
                    }
                }
            }
        }
        tools.addView(attBtn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val catBtn = toolBtn(R.drawable.ic_tag, d.category.ifBlank { "Category" }) {
            val items = Store.categories.map { "${catIcon(it)}  $it" } + "＋ Add new category…"
            pickFromList("Category", items) { w ->
                if (w == Store.categories.size) {
                    nav({ viewCategories() })
                } else {
                    d.category = Store.categories[w]
                    refresh()
                }
            }
        }
        tools.addView(catBtn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val dateBtn = toolBtn(R.drawable.ic_cal, dateLabel(d.date)) {
            uiDate(d.date) { ms ->
                d.date = ms
                refresh()
            }
        }
        tools.addView(dateBtn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        col.addView(tools)
        col.addView(vSpace(this, 12))

        // attachment preview
        if (d.attachment.isNotEmpty()) {
            try {
                val ins = contentResolver.openInputStream(Uri.parse(d.attachment))
                val bmp = BitmapFactory.decodeStream(ins)
                ins?.close()
                if (bmp != null) {
                    val iv = ImageView(this)
                    iv.setImageBitmap(bmp)
                    iv.adjustViewBounds = true
                    iv.maxHeight = dp(150)
                    iv.background = roundRect(P.field, 14f, this, P.fieldStroke, 1)
                    col.addView(iv)
                    col.addView(vSpace(this, 12))
                }
            } catch (e: Exception) {
            }
        }

        // split list
        val splitCard = LinearLayout(this)
        splitCard.orientation = LinearLayout.VERTICAL
        splitCard.background = roundRect(P.card, 18f, this, P.cardStroke, 1)
        val head = LinearLayout(this)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        head.background = roundRect(P.field, 0f, this)
        head.setPadding(dp(14), dp(10), dp(14), dp(10))
        val allCb = CheckBox(this)
        allCb.text = "All"
        allCb.textSize = 15f
        allCb.setTextColor(P.ink)
        allCb.buttonTintList = ColorStateList.valueOf(P.accent)
        allCb.isChecked = d.selected.size >= g.members.size
        allCb.setOnCheckedChangeListener { _, checked ->
            if (checked) d.selected = g.members.map { it.id }.toMutableSet() else d.selected.clear()
            refresh()
        }
        head.addView(allCb)
        val hsp = View(this); head.addView(hsp, LinearLayout.LayoutParams(0, 1, 1f))
        val customL = txt(this, "Custom", 14f, P.accent, true)
        customL.paint.isUnderlineText = true
        customL.isClickable = true
        customL.setOnClickListener {
            if (!d.custom) {
                val sh = equalShares()
                for ((mid, v) in sh) d.customAmt[mid] = if (v == 0.0) "" else v.toString()
            }
            d.custom = !d.custom
            refresh()
        }
        head.addView(customL)
        splitCard.addView(head)

        g.members.forEachIndexed { i, m ->
            if (i > 0) splitCard.addView(hLine(this))
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            row.setPadding(dp(14), dp(9), dp(14), dp(9))
            val cb = CheckBox(this)
            cb.buttonTintList = ColorStateList.valueOf(P.accent)
            cb.isChecked = d.selected.contains(m.id)
            cb.setOnCheckedChangeListener { _, checked ->
                if (checked) d.selected.add(m.id) else d.selected.remove(m.id)
                if (!d.custom) recompute()
            }
            row.addView(cb)
            row.addView(txt(this, m.name, 14.5f, P.ink))
            val rsp = View(this); row.addView(rsp, LinearLayout.LayoutParams(0, 1, 1f))
            if (d.custom) {
                val ef = EditText(this)
                ef.hint = "0.00"
                ef.textSize = 14f
                ef.setTextColor(P.ink)
                ef.setHintTextColor(P.faint)
                ef.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                ef.setText(d.customAmt[m.id] ?: "")
                ef.setPadding(dp(8), dp(6), dp(8), dp(6))
                ef.background = roundRect(P.field, 9f, this, P.fieldStroke, 1)
                ef.addTextChangedListener(simpleWatcher { d.customAmt[m.id] = it })
                customFields[m.id] = ef
                row.addView(ef, LinearLayout.LayoutParams(dp(86), ViewGroup.LayoutParams.WRAP_CONTENT))
                row.addView(txt(this, " ${g.currency}", 13f, P.sub))
            } else {
                val tv = txt(this, g.moneyOf(0.0), 14f, P.sub)
                rowAmt[m.id] = tv
                row.addView(tv)
            }
            splitCard.addView(row)
        }
        col.addView(splitCard)

        if (d.editId != null) {
            col.addView(vSpace(this, 16))
            val del = txt(this, "Delete this expense", 14f, P.neg, true)
            del.gravity = Gravity.CENTER
            del.isClickable = true
            del.setOnClickListener {
                confirm("Delete entry?", "This expense will be removed.", "Delete") {
                    val e = g.entries.firstOrNull { it.id == d.editId }
                    if (e != null) g.entries.remove(e)
                    Store.save()
                    expenseDraft = null
                    back()
                }
            }
            col.addView(del)
        }
        rootCol.addView(scrollOf(col))
        if (!d.custom) recompute()
        return rootCol
    }

    private fun saveExpenseForm(g: Group) {
        val d = expenseDraft ?: return
        val amt = d.amount.toDoubleOrNull() ?: 0.0
        if (amt <= 0) {
            toast("Enter the amount")
            return
        }
        if (d.selected.isEmpty()) {
            toast("Select at least one person to split with")
            return
        }
        val split = mutableMapOf<String, Double>()
        if (d.custom) {
            var sum = 0.0
            for (mid in d.selected) {
                val v = d.customAmt[mid]?.toDoubleOrNull() ?: 0.0
                split[mid] = round2(v)
                sum = round2(sum + v)
            }
            if (kotlin.math.abs(sum - amt) > 0.011) {
                toast("Split total (${fmt(sum)}) must equal the amount (${fmt(amt)})")
                return
            }
        } else {
            val sel = g.members.filter { d.selected.contains(it.id) }
            val base = round2(amt / sel.size)
            var acc = 0.0
            sel.forEachIndexed { i, m ->
                if (i == sel.size - 1) split[m.id] = round2(amt - acc)
                else {
                    split[m.id] = base
                    acc = round2(acc + base)
                }
            }
        }
        val title = d.title.trim().ifBlank { d.category.ifBlank { "Expense" } }
        if (d.editId == null) {
            g.entries.add(
                Entry(
                    id = Store.newId(), kind = "expense", title = title,
                    payer = d.payer, amount = round2(amt), date = d.date,
                    category = d.category, attachment = d.attachment, split = split
                )
            )
        } else {
            val e = g.entries.firstOrNull { it.id == d.editId } ?: return
            e.title = title; e.payer = d.payer; e.amount = round2(amt)
            e.date = d.date; e.category = d.category; e.attachment = d.attachment
            e.split = split
        }
        Store.save()
        expenseDraft = null
        back()
    }

    // ---------------- ADVANCE FORM ----------------

    private fun viewAdvanceForm(gid: String): View {
        val g = Store.group(gid) ?: return viewHome()
        val d = advanceDraft ?: AdvanceDraft().also { advanceDraft = it }
        if (d.payer.isEmpty()) d.payer = g.members.firstOrNull()?.id ?: ""
        if (d.paidTo.isEmpty()) d.paidTo = g.members.getOrNull(1)?.id ?: ""
        val rootCol = LinearLayout(this)
        rootCol.orientation = LinearLayout.VERTICAL
        rootCol.addView(topBar(null, {
            advanceDraft = null
            back()
        }, "SAVE") { saveAdvanceForm(g) })
        val col = pageColumn()
        val card = cardBox(this)
        val whatF = fieldBox(this, "e.g Advanced deposit")
        whatF.setText(d.title)
        whatF.addTextChangedListener(simpleWatcher { d.title = it })
        card.addView(labelRow("What?", whatF))
        card.addView(vSpace(this, 14))
        val paidSel = selectorRow(g.memberName(d.payer) + "  ▾") {
            pickFromList("Who paid?", g.members.map { it.name }) { w ->
                d.payer = g.members[w].id
                refresh()
            }
        }
        card.addView(labelRow("Who paid?", paidSel))
        card.addView(vSpace(this, 14))
        val toSel = selectorRow(g.memberName(d.paidTo) + "  ▾") {
            pickFromList("Paid to?", g.members.map { it.name }) { w ->
                d.paidTo = g.members[w].id
                refresh()
            }
        }
        card.addView(labelRow("Paid To?", toSel))
        card.addView(vSpace(this, 14))
        val whenSel = selectorRow(dateLabel(d.date) + "  📅") {
            uiDate(d.date) { ms ->
                d.date = ms
                refresh()
            }
        }
        card.addView(labelRow("When?", whenSel))
        card.addView(vSpace(this, 14))
        val amtF = fieldBox(this, "0.00")
        amtF.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        amtF.setText(d.amount)
        amtF.addTextChangedListener(simpleWatcher { d.amount = it })
        val amtRow = LinearLayout(this)
        amtRow.orientation = LinearLayout.HORIZONTAL
        amtRow.gravity = Gravity.CENTER_VERTICAL
        amtRow.addView(amtF, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        amtRow.addView(txt(this, " ${g.currency}", 16f, P.sub, true))
        card.addView(labelRow("How much?", amtRow))
        col.addView(card)

        if (d.editId != null) {
            col.addView(vSpace(this, 16))
            val del = txt(this, "Delete this entry", 14f, P.neg, true)
            del.gravity = Gravity.CENTER
            del.isClickable = true
            del.setOnClickListener {
                confirm("Delete entry?", "This payment will be removed.", "Delete") {
                    val e = g.entries.firstOrNull { it.id == d.editId }
                    if (e != null) g.entries.remove(e)
                    Store.save()
                    advanceDraft = null
                    back()
                }
            }
            col.addView(del)
        }
        rootCol.addView(scrollOf(col))
        return rootCol
    }

    private fun saveAdvanceForm(g: Group) {
        val d = advanceDraft ?: return
        val amt = d.amount.toDoubleOrNull() ?: 0.0
        if (amt <= 0) {
            toast("Enter the amount")
            return
        }
        if (d.payer == d.paidTo) {
            toast("Payer and receiver can't be the same person")
            return
        }
        val title = d.title.trim().ifBlank { "Advance" }
        if (d.editId == null) {
            g.entries.add(
                Entry(
                    id = Store.newId(), kind = "advance", title = title,
                    payer = d.payer, paidTo = d.paidTo, amount = round2(amt), date = d.date
                )
            )
        } else {
            val e = g.entries.firstOrNull { it.id == d.editId } ?: return
            e.title = title; e.payer = d.payer; e.paidTo = d.paidTo
            e.amount = round2(amt); e.date = d.date
        }
        Store.save()
        advanceDraft = null
        back()
    }

    // ---------------- BALANCE TAB ----------------

    private fun balanceTab(g: Group): View {
        val col = pageColumn()
        val head = cardBox(this)
        val l1 = txt(this, "Expenses: ${g.moneyOf(totalExpenses(g))}", 17f, P.ink, true)
        l1.gravity = Gravity.END
        head.addView(l1)
        val l2 = txt(this, "Advanced ${g.moneyOf(totalAdvanced(g))}   |   Settled ${g.moneyOf(totalSettled(g))}", 13.5f, P.sub)
        l2.gravity = Gravity.END
        l2.setPadding(0, dp(5), 0, 0)
        head.addView(l2)
        val l3 = txt(this, "Created ${dateLabel(g.createdAt)}", 12f, P.faint)
        l3.gravity = Gravity.END
        l3.setPadding(0, dp(3), 0, 0)
        head.addView(l3)
        col.addView(head)
        col.addView(vSpace(this, 12))

        val sum = cardBox(this)
        sum.setPadding(dp(16), dp(12), dp(16), dp(12))
        val sumHead = LinearLayout(this)
        sumHead.orientation = LinearLayout.HORIZONTAL
        sumHead.gravity = Gravity.CENTER_VERTICAL
        sumHead.addView(txt(this, "Summary", 16f, P.ink, true))
        val ssp = View(this); sumHead.addView(ssp, LinearLayout.LayoutParams(0, 1, 1f))
        sumHead.addView(txt(this, if (summaryOpen) "⌃" else "⌄", 16f, P.sub, true))
        sumHead.isClickable = true
        sumHead.setOnClickListener { summaryOpen = !summaryOpen; refresh() }
        sum.addView(sumHead)
        if (summaryOpen) {
            val rows = balances(g)
            rows.forEachIndexed { i, r ->
                if (i > 0) sum.addView(hLine(this))
                val row = LinearLayout(this)
                row.orientation = LinearLayout.HORIZONTAL
                row.gravity = Gravity.CENTER_VERTICAL
                row.setPadding(0, dp(10), 0, dp(10))
                val left = LinearLayout(this)
                left.orientation = LinearLayout.VERTICAL
                left.addView(txt(this, r.member.name, 15f, P.ink, true))
                left.addView(txt(this, "Charged ${fmt(r.charged)}, Paid ${fmt(r.paid)}", 12f, P.faint))
                row.addView(left, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                val net = r.net
                val nt = txt(
                    this,
                    (if (net > 0) "+" else if (net < 0) "−" else "") + g.moneyOf(kotlin.math.abs(net)),
                    14.5f,
                    if (net > 0.004) P.pos else if (net < -0.004) P.neg else P.faint,
                    true
                )
                row.addView(nt)
                sum.addView(row)
            }
        }
        col.addView(sum)
        col.addView(vSpace(this, 12))

        val st = cardBox(this)
        st.setPadding(dp(16), dp(12), dp(16), dp(12))
        val stHead = LinearLayout(this)
        stHead.orientation = LinearLayout.HORIZONTAL
        stHead.gravity = Gravity.CENTER_VERTICAL
        stHead.addView(txt(this, "How to settle?", 16f, P.ink, true))
        val tsp = View(this); stHead.addView(tsp, LinearLayout.LayoutParams(0, 1, 1f))
        stHead.addView(txt(this, if (settleOpen) "⌃" else "⌄", 16f, P.sub, true))
        stHead.isClickable = true
        stHead.setOnClickListener { settleOpen = !settleOpen; refresh() }
        st.addView(stHead)
        if (settleOpen) {
            val moves = settleMoves(g)
            if (moves.isEmpty()) {
                st.addView(txt(this, "All settled up 🎉", 13.5f, P.sub))
            }
            moves.forEachIndexed { i, mv ->
                if (i > 0) st.addView(hLine(this))
                val row = LinearLayout(this)
                row.orientation = LinearLayout.HORIZONTAL
                row.gravity = Gravity.CENTER_VERTICAL
                row.setPadding(0, dp(10), 0, dp(10))
                val left = LinearLayout(this)
                left.orientation = LinearLayout.VERTICAL
                left.addView(txt(this, mv.from.name, 15f, P.ink))
                val sub = txt(this, "should pay to ", 12.5f, P.faint)
                left.addView(sub)
                left.addView(txt(this, mv.to.name, 12.5f, P.accent, true))
                row.addView(left, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                row.addView(txt(this, g.moneyOf(mv.amount), 14.5f, P.ink, true))
                row.isClickable = true
                row.setOnClickListener {
                    confirm(
                        "Record settlement?",
                        "${mv.from.name} pays ${mv.to.name} ${g.moneyOf(mv.amount)} — this will be added as a settled payment.",
                        "Record"
                    ) {
                        g.entries.add(
                            Entry(
                                id = Store.newId(), kind = "settle", title = "Settled up",
                                payer = mv.from.id, paidTo = mv.to.id,
                                amount = mv.amount, date = System.currentTimeMillis()
                            )
                        )
                        Store.save()
                        refresh()
                    }
                }
                st.addView(row)
            }
            if (moves.isNotEmpty()) {
                st.addView(txt(this, "Tap a row when that payment is made to record it.", 11.5f, P.faint))
            }
        }
        col.addView(st)
        return col
    }

    // ---------------- SHARE TAB ----------------

    // Monochrome 3D-style coin badge: accent gradient + drop shadow + top highlight
    private fun badge3d(icon: Int?, text: String?): View {
        val size = dp(66)
        val frame = FrameLayout(this)
        frame.layoutParams = LinearLayout.LayoutParams(size, size)
        val shadow = View(this)
        val shd = GradientDrawable()
        shd.shape = GradientDrawable.OVAL
        shd.setColor(if (P.dark) 0x59000000 else 0x26000000)
        shadow.background = shd
        val shLp = FrameLayout.LayoutParams(size - dp(6), size - dp(6))
        shLp.gravity = Gravity.CENTER
        shLp.topMargin = dp(5)
        shadow.layoutParams = shLp
        frame.addView(shadow)
        val coin = FrameLayout(this)
        val gd = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(P.lighten(P.accent, 0.45f), P.accent, P.accentDark)
        )
        gd.shape = GradientDrawable.OVAL
        gd.setStroke(dp(1), P.lighten(P.accent, 0.55f))
        coin.background = gd
        val cLp = FrameLayout.LayoutParams(size - dp(6), size - dp(6))
        cLp.gravity = Gravity.CENTER
        coin.layoutParams = cLp
        val hl = View(this)
        val hld = GradientDrawable()
        hld.shape = GradientDrawable.OVAL
        hld.setColor(0x38FFFFFF)
        hl.background = hld
        val hlLp = FrameLayout.LayoutParams(dp(30), dp(13))
        hlLp.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        hlLp.topMargin = dp(7)
        hl.layoutParams = hlLp
        coin.addView(hl)
        if (text != null) {
            val tv = txt(this, text, 15f, P.onAccent, true)
            tv.gravity = Gravity.CENTER
            coin.addView(tv, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        } else if (icon != null) {
            val iv = ImageView(this)
            iv.setImageResource(icon)
            iv.setColorFilter(P.onAccent)
            val ivLp = FrameLayout.LayoutParams(dp(30), dp(30))
            ivLp.gravity = Gravity.CENTER
            coin.addView(iv, ivLp)
        }
        frame.addView(coin)
        return frame
    }

    private fun shareTab(g: Group): View {
        val col = pageColumn()
        col.setPadding(dp(16), dp(26), dp(16), dp(20))
        fun tile(icon: Int?, badgeText: String?, label: String, fn: () -> Unit): LinearLayout {
            val t = cardBox(this)
            t.gravity = Gravity.CENTER
            t.setPadding(dp(10), dp(18), dp(10), dp(18))
            t.addView(badge3d(icon, badgeText))
            val lb = txt(this, label, 14.5f, P.ink)
            lb.gravity = Gravity.CENTER
            lb.setPadding(0, dp(12), 0, 0)
            t.addView(lb, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            t.isClickable = true
            t.setOnClickListener { fn() }
            return t
        }
        val r1 = LinearLayout(this)
        r1.orientation = LinearLayout.HORIZONTAL
        val t1 = tile(R.drawable.ic_share, null, "Share") { shareSummary(g) }
        r1.addView(t1, LinearLayout.LayoutParams(0, dp(172), 1f))
        val gap1 = View(this); r1.addView(gap1, LinearLayout.LayoutParams(dp(12), 1))
        val t2 = tile(R.drawable.ic_chart, null, "Categorized") { nav({ viewCategorized(g.id) }) }
        r1.addView(t2, LinearLayout.LayoutParams(0, dp(172), 1f))
        col.addView(r1)
        col.addView(vSpace(this, 12))
        val r2 = LinearLayout(this)
        r2.orientation = LinearLayout.HORIZONTAL
        val t3 = tile(R.drawable.ic_sync, null, "Sync") {
            pickFromList("Backup & restore", listOf("Export backup", "Import backup")) { w ->
                if (w == 0) exportBackup() else importBackup()
            }
        }
        r2.addView(t3, LinearLayout.LayoutParams(0, dp(172), 1f))
        val gap2 = View(this); r2.addView(gap2, LinearLayout.LayoutParams(dp(12), 1))
        val t4 = tile(null, "PDF", "PDF") { makePdf(g) }
        r2.addView(t4, LinearLayout.LayoutParams(0, dp(172), 1f))
        col.addView(r2)
        return col
    }

    private fun shareSummaryText(g: Group): String {
        val sb = StringBuilder()
        sb.append("📊 ${g.title} — Expense Summary\n")
        sb.append("Total expenses: ${g.moneyOf(totalExpenses(g))}\n")
        sb.append("Advanced: ${g.moneyOf(totalAdvanced(g))} · Settled: ${g.moneyOf(totalSettled(g))}\n\n")
        sb.append("Balances:\n")
        for (r in balances(g)) {
            val tag = when {
                r.net > 0.004 -> "gets back ${g.moneyOf(r.net)}"
                r.net < -0.004 -> "owes ${g.moneyOf(-r.net)}"
                else -> "settled"
            }
            sb.append("• ${r.member.name}: $tag\n")
        }
        val moves = settleMoves(g)
        if (moves.isNotEmpty()) {
            sb.append("\nHow to settle:\n")
            for (mv in moves) sb.append("• ${mv.from.name} → ${mv.to.name}: ${g.moneyOf(mv.amount)}\n")
        }
        return sb.toString()
    }

    private fun shareSummary(g: Group) {
        val i = Intent(Intent.ACTION_SEND)
        i.type = "text/plain"
        i.putExtra(Intent.EXTRA_TEXT, shareSummaryText(g))
        startActivity(Intent.createChooser(i, "Share summary"))
    }

    private fun exportBackup() {
        createDoc("groupexpense-backup.json") { uri ->
            if (uri != null) {
                try {
                    contentResolver.openOutputStream(uri)?.use { it.write(Store.exportJson().toByteArray()) }
                    toast("Backup exported")
                } catch (e: Exception) {
                    toast("Export failed")
                }
            }
        }
    }

    private fun importBackup() {
        openDoc { uri ->
            if (uri != null) {
                try {
                    val text = contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: ""
                    if (Store.importJson(text)) {
                        toast("Backup restored")
                        nav({ viewHome() }, clear = true)
                    } else toast("That file isn't a valid backup")
                } catch (e: Exception) {
                    toast("Import failed")
                }
            }
        }
    }

    private fun makePdf(g: Group) {
        try {
            val doc = android.graphics.pdf.PdfDocument()
            val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, 1).create()
            var pageNum = 0
            var page: android.graphics.pdf.PdfDocument.Page? = null
            var canvas: android.graphics.Canvas? = null
            var y = 0f
            val margin = 40f
            val contentW = 595f - margin * 2
            val accent = P.accent
            val accentDark = P.accentDark
            val lightRow = P.lighten(accent, 0.88f)
            val lineCol = android.graphics.Color.rgb(203, 214, 211)
            val inkCol = android.graphics.Color.rgb(28, 40, 37)

            fun footer() {
                val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
                p.color = lineCol
                canvas!!.drawLine(margin, 804f, 595f - margin, 804f, p)
                p.color = accentDark
                p.textSize = 10.5f
                p.typeface = android.graphics.Typeface.DEFAULT_BOLD
                p.textAlign = android.graphics.Paint.Align.CENTER
                canvas!!.drawText("This app was made by Deon", 595f / 2, 821f, p)
                p.textAlign = android.graphics.Paint.Align.RIGHT
                p.typeface = android.graphics.Typeface.DEFAULT
                p.color = android.graphics.Color.GRAY
                p.textSize = 9f
                canvas!!.drawText("Page $pageNum", 595f - margin, 821f, p)
            }
            fun newPage() {
                if (page != null) {
                    footer()
                    doc.finishPage(page!!)
                }
                pageNum++
                page = doc.startPage(pageInfo)
                canvas = page!!.canvas
                y = margin + 6f
            }
            fun heading(text: String) {
                if (y > 716f) newPage()
                val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
                p.color = accentDark
                p.textSize = 14.5f
                p.typeface = android.graphics.Typeface.DEFAULT_BOLD
                canvas!!.drawText(text, margin, y + 14f, p)
                p.color = accent
                canvas!!.drawRect(margin, y + 20f, margin + 34f, y + 23.5f, p)
                y += 36f
            }
            fun table(cols: List<Triple<String, Float, Boolean>>, rows: List<List<String>>, netCol: Int = -1) {
                val rowH = 21f
                fun headerRow() {
                    val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
                    p.color = accent
                    p.style = android.graphics.Paint.Style.FILL
                    canvas!!.drawRect(margin, y, margin + contentW, y + rowH + 2f, p)
                    p.color = android.graphics.Color.WHITE
                    p.textSize = 10.5f
                    p.typeface = android.graphics.Typeface.DEFAULT_BOLD
                    var x = margin
                    for ((title, w, right) in cols) {
                        p.textAlign = if (right) android.graphics.Paint.Align.RIGHT else android.graphics.Paint.Align.LEFT
                        canvas!!.drawText(title, if (right) x + w - 7f else x + 7f, y + 15f, p)
                        x += w
                    }
                    y += rowH + 2f
                }
                if (y + rowH + 4f > 778f) newPage()
                headerRow()
                val border = android.graphics.Paint()
                border.color = lineCol
                border.style = android.graphics.Paint.Style.STROKE
                border.strokeWidth = 0.7f
                val fill = android.graphics.Paint()
                fill.style = android.graphics.Paint.Style.FILL
                val body = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
                body.textSize = 10.5f
                rows.forEachIndexed { ri, cells ->
                    if (y + rowH > 778f) {
                        newPage()
                        headerRow()
                    }
                    if (ri % 2 == 1) {
                        fill.color = lightRow
                        canvas!!.drawRect(margin, y, margin + contentW, y + rowH, fill)
                    }
                    canvas!!.drawRect(margin, y, margin + contentW, y + rowH, border)
                    var x = margin
                    cells.forEachIndexed { ci, cell ->
                        val (_, w, right) = cols[ci]
                        body.color = when {
                            ci == netCol && cell.startsWith("+") -> android.graphics.Color.rgb(8, 145, 100)
                            ci == netCol && cell.startsWith("−") -> android.graphics.Color.rgb(214, 40, 80)
                            else -> inkCol
                        }
                        body.textAlign = if (right) android.graphics.Paint.Align.RIGHT else android.graphics.Paint.Align.LEFT
                        var t = cell
                        val maxW = w - 14f
                        while (t.length > 4 && body.measureText(t) > maxW) t = t.dropLast(1)
                        if (t != cell) t = t.dropLast(1) + "…"
                        canvas!!.drawText(t, if (right) x + w - 7f else x + 7f, y + 14.5f, body)
                        x += w
                    }
                    y += rowH
                }
                y += 16f
            }
            fun noteLine(text: String) {
                val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
                p.color = inkCol
                p.textSize = 11f
                canvas!!.drawText(text, margin, y + 4f, p)
                y += 26f
            }

            newPage()
            // Header band with the app heading
            val band = android.graphics.Paint()
            band.style = android.graphics.Paint.Style.FILL
            band.color = accentDark
            canvas!!.drawRect(0f, 0f, 595f, 98f, band)
            band.color = accent
            canvas!!.drawRect(0f, 98f, 595f, 103f, band)
            val hp = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
            hp.color = android.graphics.Color.WHITE
            hp.textSize = 19f
            hp.typeface = android.graphics.Typeface.DEFAULT_BOLD
            canvas!!.drawText("Group Xpense - track & split", margin, 45f, hp)
            hp.textSize = 13.5f
            hp.typeface = android.graphics.Typeface.DEFAULT
            canvas!!.drawText(g.title, margin, 72f, hp)
            hp.textSize = 10.5f
            hp.textAlign = android.graphics.Paint.Align.RIGHT
            canvas!!.drawText(fullDate(System.currentTimeMillis()), 595f - margin, 45f, hp)
            hp.textAlign = android.graphics.Paint.Align.LEFT
            y = 122f
            // Overview strip
            val strip = android.graphics.Paint()
            strip.style = android.graphics.Paint.Style.FILL
            strip.color = lightRow
            canvas!!.drawRoundRect(margin, y, margin + contentW, y + 36f, 8f, 8f, strip)
            val op = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
            op.color = inkCol
            op.textSize = 11f
            op.typeface = android.graphics.Typeface.DEFAULT_BOLD
            val overview = "Total expenses  ${g.moneyOf(totalExpenses(g))}      Advanced  ${g.moneyOf(totalAdvanced(g))}      Settled  ${g.moneyOf(totalSettled(g))}"
            canvas!!.drawText(overview, margin + 12f, y + 23f, op)
            y += 56f
            val meta = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
            meta.color = android.graphics.Color.GRAY
            meta.textSize = 10.5f
            canvas!!.drawText("${g.members.size} members · Created ${fullDate(g.createdAt)} · Currency ${g.currencyCode}", margin, y, meta)
            y += 22f

            heading("Expenses")
            val expRows = g.entries.sortedByDescending { it.date }.map { e ->
                val cat = when (e.kind) {
                    "expense" -> e.category.ifBlank { "—" }
                    "advance" -> "Advance"
                    else -> "Settlement"
                }
                listOf(dateLabel(e.date), e.title, g.memberName(e.payer), cat, fmt(e.amount))
            }
            if (expRows.isEmpty()) {
                noteLine("No entries yet.")
            } else {
                table(
                    listOf(
                        Triple("Date", 78f, false),
                        Triple("Item", 168f, false),
                        Triple("Paid by", 108f, false),
                        Triple("Category", 79f, false),
                        Triple("Amount", 82f, true)
                    ), expRows
                )
            }

            heading("Summary")
            val sumRows = balances(g).map { r ->
                val net = r.net
                val netTxt = (if (net > 0) "+" else if (net < 0) "−" else "") + fmt(kotlin.math.abs(net))
                listOf(r.member.name, fmt(r.charged), fmt(r.paid), netTxt)
            }
            table(
                listOf(
                    Triple("Member", 185f, false),
                    Triple("Charged (${g.currency})", 110f, true),
                    Triple("Paid (${g.currency})", 110f, true),
                    Triple("Net (${g.currency})", 110f, true)
                ), sumRows, netCol = 3
            )

            heading("How to settle")
            val moves = settleMoves(g)
            if (moves.isEmpty()) {
                noteLine("All settled up — nothing to pay.")
            } else {
                val mvRows = moves.map { mv -> listOf(mv.from.name, mv.to.name, fmt(mv.amount)) }
                table(
                    listOf(
                        Triple("From", 200f, false),
                        Triple("To", 200f, false),
                        Triple("Amount (${g.currency})", 115f, true)
                    ), mvRows
                )
            }

            footer()
            doc.finishPage(page!!)
            val dir = java.io.File(cacheDir, "shared")
            dir.mkdirs()
            val f = java.io.File(dir, g.title.replace(Regex("[^A-Za-z0-9]+"), "-") + "-report.pdf")
            f.outputStream().use { doc.writeTo(it) }
            doc.close()
            val uri = androidx.core.content.FileProvider.getUriForFile(this, "com.deon.groupexpense.files", f)
            val i = Intent(Intent.ACTION_SEND)
            i.type = "application/pdf"
            i.putExtra(Intent.EXTRA_STREAM, uri)
            i.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            startActivity(Intent.createChooser(i, "Share PDF report"))
        } catch (e: Exception) {
            toast("PDF failed: ${e.message}")
        }
    }

    // ---------------- CATEGORIZED ----------------

    private fun viewCategorized(gid: String): View {
        val g = Store.group(gid) ?: return viewHome()
        val rootCol = LinearLayout(this)
        rootCol.orientation = LinearLayout.VERTICAL
        rootCol.addView(topBar("Categorized", { back() }, null, null))
        val col = pageColumn()
        val expenses = g.entries.filter { it.kind == "expense" }
        val total = totalExpenses(g)
        val head = cardBox(this)
        head.addView(txt(this, "Total expenses", 12.5f, P.sub, true))
        head.addView(txt(this, g.moneyOf(total), 24f, P.ink, true))
        head.addView(txt(this, "${expenses.size} expenses · ${g.title}", 12.5f, P.faint))
        col.addView(head)
        col.addView(vSpace(this, 12))
        if (expenses.isEmpty()) {
            col.addView(txt(this, "No expenses yet in this group.", 13.5f, P.sub))
        } else {
            val byCat = expenses.groupBy { it.category.ifBlank { "Uncategorized" } }
                .mapValues { (_, v) -> round2(v.sumOf { it.amount }) to v.size }
                .entries.sortedByDescending { it.value.first }
            val card = cardBox(this)
            card.setPadding(dp(16), dp(8), dp(16), dp(8))
            byCat.forEachIndexed { i, (cat, pair) ->
                if (i > 0) card.addView(hLine(this))
                val (sum, count) = pair
                val pct = if (total > 0) (sum / total * 100) else 0.0
                val row = LinearLayout(this)
                row.orientation = LinearLayout.VERTICAL
                row.setPadding(0, dp(11), 0, dp(11))
                val top = LinearLayout(this)
                top.orientation = LinearLayout.HORIZONTAL
                top.addView(txt(this, "${catIcon(cat)}  $cat", 15f, P.ink, true))
                val sp = View(this); top.addView(sp, LinearLayout.LayoutParams(0, 1, 1f))
                top.addView(txt(this, g.moneyOf(sum), 14.5f, P.ink, true))
                row.addView(top)
                row.addView(txt(this, "$count expense${if (count == 1) "" else "s"} · ${String.format("%.1f", pct)}%", 12f, P.faint))
                val bar = LinearLayout(this)
                bar.orientation = LinearLayout.HORIZONTAL
                bar.setPadding(0, dp(7), 0, 0)
                val fg = View(this)
                fg.background = gradAccent(this, 99f)
                bar.addView(fg, LinearLayout.LayoutParams(0, dp(6), pct.toFloat().coerceAtLeast(0.5f)))
                val bgv = View(this)
                bgv.background = roundRect(P.field, 99f, this)
                bar.addView(bgv, LinearLayout.LayoutParams(0, dp(6), (100 - pct).toFloat().coerceAtLeast(0.5f)))
                row.addView(bar)
                card.addView(row)
            }
            col.addView(card)
        }
        rootCol.addView(scrollOf(col))
        return rootCol
    }

    // ---------------- CATEGORIES ----------------

    private fun viewCategories(): View {
        val rootCol = LinearLayout(this)
        rootCol.orientation = LinearLayout.VERTICAL
        rootCol.addView(topBar("Categories", { back() }, null, null))
        val col = pageColumn()
        val addCard = cardBox(this)
        val addRow = LinearLayout(this)
        addRow.orientation = LinearLayout.HORIZONTAL
        addRow.gravity = Gravity.CENTER_VERTICAL
        val nameF = fieldBox(this, "New category name")
        addRow.addView(nameF, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val addB = primaryBtn(this, "Add")
        addB.textSize = 13.5f
        val alp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        alp.leftMargin = dp(10)
        addB.layoutParams = alp
        addB.setOnClickListener {
            val n = nameF.text.toString().trim()
            if (n.isEmpty()) toast("Type a category name")
            else if (Store.categories.any { it.equals(n, true) }) toast("Category already exists")
            else {
                Store.categories.add(n)
                Store.save()
                refresh()
            }
        }
        addRow.addView(addB)
        addCard.addView(addRow)
        col.addView(addCard)
        col.addView(vSpace(this, 12))
        val card = cardBox(this)
        card.setPadding(dp(16), dp(6), dp(16), dp(6))
        Store.categories.forEachIndexed { i, c ->
            if (i > 0) card.addView(hLine(this))
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            row.setPadding(0, dp(11), 0, dp(11))
            row.addView(txt(this, catIcon(c), 15f, P.ink))
            val nm = txt(this, c, 15f, P.ink)
            nm.setPadding(dp(10), 0, 0, 0)
            row.addView(nm, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            if (Store.DEFAULT_CATEGORIES.contains(c)) {
                row.addView(txt(this, "Default", 11.5f, P.faint))
            } else {
                val del = txt(this, "✕", 13f, P.faint)
                del.setPadding(dp(8), dp(6), dp(8), dp(6))
                del.isClickable = true
                del.setOnClickListener {
                    Store.categories.remove(c)
                    Store.save()
                    refresh()
                }
                row.addView(del)
            }
            card.addView(row)
        }
        col.addView(card)
        rootCol.addView(scrollOf(col))
        return rootCol
    }

    // ---------------- ARCHIVE ----------------

    private fun viewArchive(): View {
        val rootCol = LinearLayout(this)
        rootCol.orientation = LinearLayout.VERTICAL
        rootCol.addView(topBar("Archived Groups", { back() }, null, null))
        val col = pageColumn()
        val archived = Store.groups.filter { it.archived }
        if (archived.isEmpty()) {
            col.addView(txt(this, "No archived groups.", 14f, P.sub))
        }
        for (grp in archived) {
            val card = cardBox(this)
            card.addView(txt(this, grp.title, 16f, P.ink, true))
            card.addView(txt(this, grp.desc.ifBlank { "${grp.members.size} members" }, 12.5f, P.sub))
            card.addView(vSpace(this, 10))
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            val un = ghostBtn(this, "Unarchive")
            un.setOnClickListener {
                grp.archived = false
                Store.save()
                refresh()
            }
            row.addView(un)
            val del = txt(this, "Delete", 14f, P.neg, true)
            del.setPadding(dp(18), dp(10), dp(8), dp(10))
            del.isClickable = true
            del.setOnClickListener {
                confirm("Delete group?", "“${grp.title}” will be permanently removed.", "Delete") {
                    Store.groups.remove(grp)
                    Store.save()
                    refresh()
                }
            }
            row.addView(del)
            card.addView(row)
            col.addView(card)
            col.addView(vSpace(this, 10))
        }
        rootCol.addView(scrollOf(col))
        return rootCol
    }

    // ---------------- CONVERTER ----------------

    private var convFrom = CURRENCIES[1] // USD
    private var convTo = CURRENCIES[0] // INR

    private fun viewConverter(): View {
        val rootCol = LinearLayout(this)
        rootCol.orientation = LinearLayout.VERTICAL
        rootCol.addView(topBar("Currency Converter", { back() }, null, null))
        val col = pageColumn()
        val card = cardBox(this)
        card.addView(txt(this, "AMOUNT", 11f, P.sub, true))
        card.addView(vSpace(this, 6))
        val result = txt(this, "", 24f, P.accent, true)
        val amtF = fieldBox(this, "0.00")
        amtF.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        fun recompute() {
            val amt = amtF.text.toString().toDoubleOrNull() ?: 0.0
            val inr = amt * convFrom.rateInr
            val out = if (convTo.rateInr == 0.0) 0.0 else inr / convTo.rateInr
            result.text = "= ${money(convTo.symbol, round2(out))}"
        }
        amtF.addTextChangedListener(simpleWatcher { recompute() })
        card.addView(amtF)
        card.addView(vSpace(this, 14))
        val fromSel = selectorRow("${convFrom.symbol}  ${convFrom.code} — ${convFrom.name}  ▾") {
            pickFromList("From", CURRENCIES.map { "${it.symbol}  ${it.code} — ${it.name}" }) { w ->
                convFrom = CURRENCIES[w]
                refresh()
            }
        }
        card.addView(labelRow("From", fromSel))
        card.addView(vSpace(this, 10))
        val swap = ghostBtn(this, "⇅  Swap")
        swap.setOnClickListener {
            val t = convFrom; convFrom = convTo; convTo = t
            refresh()
        }
        val swapRow = LinearLayout(this)
        swapRow.gravity = Gravity.CENTER
        swapRow.addView(swap)
        card.addView(swapRow)
        card.addView(vSpace(this, 10))
        val toSel = selectorRow("${convTo.symbol}  ${convTo.code} — ${convTo.name}  ▾") {
            pickFromList("To", CURRENCIES.map { "${it.symbol}  ${it.code} — ${it.name}" }) { w ->
                convTo = CURRENCIES[w]
                refresh()
            }
        }
        card.addView(labelRow("To", toSel))
        card.addView(vSpace(this, 16))
        card.addView(result)
        card.addView(vSpace(this, 6))
        card.addView(txt(this, "Indicative rates (1 ${convFrom.code} ≈ ${fmt(convFrom.rateInr)} INR). For planning only.", 11.5f, P.faint))
        col.addView(card)
        rootCol.addView(scrollOf(col))
        recompute()
        return rootCol
    }

    // ---------------- SETTINGS ----------------

    private fun hexOf(c: Int): String = String.format("#%06X", 0xFFFFFF and c)

    private fun uiColorPicker() {
        val content = LinearLayout(this)
        content.orientation = LinearLayout.VERTICAL
        content.addView(txt(this, "Pick a colour", 17.5f, P.ink, true))
        content.addView(vSpace(this, 12))
        val picker = SvPickerView(this)
        picker.setColor(Store.accent)
        content.addView(picker, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(220)))
        content.addView(vSpace(this, 12))
        val prevRow = LinearLayout(this)
        prevRow.orientation = LinearLayout.HORIZONTAL
        prevRow.gravity = Gravity.CENTER_VERTICAL
        val prevD = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Store.accent)
            setStroke(dp(1), P.faint)
        }
        val prev = View(this)
        prev.background = prevD
        prevRow.addView(prev, LinearLayout.LayoutParams(dp(30), dp(30)))
        val hexT = txt(this, hexOf(Store.accent), 14.5f, P.ink, true)
        hexT.setPadding(dp(10), 0, 0, 0)
        prevRow.addView(hexT)
        content.addView(prevRow)
        picker.onChange = {
            prevD.setColor(picker.color())
            hexT.text = hexOf(picker.color())
        }
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.END
        row.setPadding(0, dp(16), 0, 0)
        lateinit var dlg: android.app.Dialog
        val cancel = ghostBtn(this, "Cancel")
        cancel.setOnClickListener { dlg.dismiss() }
        row.addView(cancel)
        val ok = primaryBtn(this, "SET COLOUR")
        ok.textSize = 13f
        val olp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        olp.leftMargin = dp(10)
        ok.layoutParams = olp
        ok.setOnClickListener {
            Store.accent = picker.color()
            Store.save()
            P.apply(this, Store.mode, Store.accent)
            applyChrome()
            dlg.dismiss()
            refresh()
        }
        row.addView(ok)
        content.addView(row)
        dlg = themedDialog(content)
        dlg.show()
    }

    private fun profilePhotoView(sizeDp: Int): View {
        if (Store.profilePhoto.isNotEmpty()) {
            try {
                val ins = contentResolver.openInputStream(Uri.parse(Store.profilePhoto))
                val bmp = BitmapFactory.decodeStream(ins)
                ins?.close()
                if (bmp != null) {
                    val iv = ImageView(this)
                    iv.setImageBitmap(circleBitmap(bmp))
                    iv.layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp))
                    return iv
                }
            } catch (e: Exception) {
            }
        }
        return avatarView(this, Store.profileName.ifBlank { "You" }, sizeDp)
    }

    private fun circleBitmap(src: android.graphics.Bitmap): android.graphics.Bitmap {
        val size = minOf(src.width, src.height)
        val sx = (src.width - size) / 2
        val sy = (src.height - size) / 2
        val squared = android.graphics.Bitmap.createBitmap(src, sx, sy, size, size)
        val out = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
        val cv = android.graphics.Canvas(out)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        paint.shader = android.graphics.BitmapShader(squared, android.graphics.Shader.TileMode.CLAMP, android.graphics.Shader.TileMode.CLAMP)
        cv.drawCircle(size / 2f, size / 2f, size / 2f, paint)
        return out
    }

    private fun viewSettings(gid: String?): View {
        val rootCol = LinearLayout(this)
        rootCol.orientation = LinearLayout.VERTICAL
        rootCol.addView(topBar("Settings", { back() }, null, null))
        val col = pageColumn()
        val g = gid?.let { Store.group(it) }

        if (g != null) {
            // GROUP SETTINGS (from the More tab): group editing only
            col.addView(txt(this, "THIS GROUP", 12f, P.sub, true))
            col.addView(vSpace(this, 8))
            val gc = cardBox(this)
            gc.setPadding(dp(16), dp(6), dp(16), dp(6))
            fun sRow(label: String, color: Int, fn: () -> Unit): LinearLayout {
                val r = LinearLayout(this)
                r.orientation = LinearLayout.HORIZONTAL
                r.setPadding(0, dp(13), 0, dp(13))
                r.addView(txt(this, label, 15f, color))
                r.isClickable = true
                r.setOnClickListener { fn() }
                return r
            }
            fun editDraft() = GroupDraft().apply {
                title = g.title; desc = g.desc
                currency = CURRENCIES.firstOrNull { it.code == g.currencyCode } ?: CURRENCIES[0]
                members = g.members.map { it.copy() }.toMutableList()
                editId = g.id
            }
            gc.addView(sRow("✏️  Edit group details", P.ink) {
                groupDraft = editDraft()
                nav({ viewGroupForm() })
            })
            gc.addView(hLine(this))
            gc.addView(sRow("👥  Edit members", P.ink) {
                groupDraft = editDraft()
                nav({ viewGroupForm() })
            })
            gc.addView(hLine(this))
            gc.addView(sRow(if (g.archived) "🗄  Unarchive group" else "🗄  Archive group", P.ink) {
                g.archived = !g.archived
                Store.save()
                toast(if (g.archived) "Group archived" else "Group unarchived")
                back()
            })
            gc.addView(hLine(this))
            gc.addView(sRow("🗑  Delete group", P.neg) {
                confirm("Delete group?", "“${g.title}” and all its expenses will be removed.", "Delete") {
                    Store.groups.remove(g)
                    Store.save()
                    nav({ viewHome() }, clear = true)
                }
            })
            col.addView(gc)
            col.addView(vSpace(this, 18))
            val appBtn = primaryBtn(this, "🎨  App themes and settings")
            appBtn.setOnClickListener { nav({ viewSettings(null) }) }
            col.addView(appBtn)
            rootCol.addView(scrollOf(col))
            return rootCol
        }

        // APP SETTINGS: profile first, then themes, then data
        col.addView(txt(this, "PROFILE", 12f, P.sub, true))
        col.addView(vSpace(this, 8))
        val pc = cardBox(this)
        val photoRow = LinearLayout(this)
        photoRow.orientation = LinearLayout.HORIZONTAL
        photoRow.gravity = Gravity.CENTER_VERTICAL
        photoRow.addView(profilePhotoView(64))
        val pTexts = LinearLayout(this)
        pTexts.orientation = LinearLayout.VERTICAL
        pTexts.setPadding(dp(14), 0, 0, 0)
        pTexts.addView(txt(this, Store.profileName.ifBlank { "Your name" }, 16.5f, P.ink, true))
        val chPhoto = txt(this, "Change photo", 13.5f, P.accent, true)
        chPhoto.setPadding(0, dp(5), 0, 0)
        chPhoto.isClickable = true
        chPhoto.setOnClickListener {
            pickImage { uri ->
                if (uri != null) {
                    Store.profilePhoto = uri.toString()
                    Store.save()
                    refresh()
                }
            }
        }
        pTexts.addView(chPhoto)
        if (Store.profilePhoto.isNotEmpty()) {
            val rmPhoto = txt(this, "Remove photo", 12f, P.faint)
            rmPhoto.setPadding(0, dp(3), 0, 0)
            rmPhoto.isClickable = true
            rmPhoto.setOnClickListener {
                Store.profilePhoto = ""
                Store.save()
                refresh()
            }
            pTexts.addView(rmPhoto)
        }
        photoRow.addView(pTexts)
        pc.addView(photoRow)
        pc.addView(vSpace(this, 14))
        pc.addView(txt(this, "NAME", 11f, P.sub, true))
        pc.addView(vSpace(this, 6))
        val nameF = fieldBox(this, "Your name")
        nameF.setText(Store.profileName)
        pc.addView(nameF)
        pc.addView(vSpace(this, 10))
        val saveP = primaryBtn(this, "Save")
        saveP.setOnClickListener {
            Store.profileName = nameF.text.toString().trim()
            Store.save()
            toast("Saved")
            refresh()
        }
        pc.addView(saveP)
        col.addView(pc)
        col.addView(vSpace(this, 16))

        col.addView(txt(this, "APPEARANCE", 12f, P.sub, true))
        col.addView(vSpace(this, 8))
        val ap = cardBox(this)
        ap.addView(txt(this, "Mode", 14.5f, P.ink, true))
        ap.addView(vSpace(this, 9))
        val modeRow = LinearLayout(this)
        modeRow.orientation = LinearLayout.HORIZONTAL
        for (m in listOf("DARK", "LIGHT", "SYSTEM")) {
            val selected = Store.mode == m
            val chip = txt(this, m.lowercase().replaceFirstChar { it.uppercase() }, 13.5f, if (selected) P.onAccent else P.ink, true)
            chip.gravity = Gravity.CENTER
            chip.background = if (selected) gradAccent(this, 99f) else roundRect(P.field, 99f, this, P.fieldStroke, 1)
            chip.setPadding(dp(16), dp(9), dp(16), dp(9))
            chip.isClickable = true
            chip.setOnClickListener {
                Store.mode = m
                Store.save()
                P.apply(this, Store.mode, Store.accent)
                applyChrome()
                refresh()
            }
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.rightMargin = dp(8)
            chip.layoutParams = lp
            modeRow.addView(chip)
        }
        ap.addView(modeRow)
        ap.addView(vSpace(this, 16))
        ap.addView(txt(this, "Theme colour", 14.5f, P.ink, true))
        ap.addView(vSpace(this, 9))
        val hsv2 = android.widget.HorizontalScrollView(this)
        hsv2.isHorizontalScrollBarEnabled = false
        val swRow = LinearLayout(this)
        swRow.orientation = LinearLayout.HORIZONTAL
        swRow.setPadding(dp(2), dp(5), dp(2), dp(8))
        for (c in ACCENTS) {
            val selected = Store.accent == c
            val cLum = 0.299 * Color.red(c) + 0.587 * Color.green(c) + 0.114 * Color.blue(c)
            val sw = txt(this, if (selected) "✓" else "", 14f, if (cLum < 100) 0xFFF8FAFC.toInt() else 0xFF03211B.toInt(), true)
            sw.gravity = Gravity.CENTER
            sw.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(c)
                setStroke(dp(if (selected) 2 else 1), if (selected) P.ink else P.faint)
            }
            val lp = LinearLayout.LayoutParams(dp(40), dp(40))
            lp.rightMargin = dp(10)
            sw.layoutParams = lp
            sw.isClickable = true
            sw.setOnClickListener {
                Store.accent = c
                Store.save()
                P.apply(this, Store.mode, Store.accent)
                applyChrome()
                refresh()
            }
            swRow.addView(sw)
        }
        // Colour picker tile: any custom colour
        val custom = !ACCENTS.contains(Store.accent)
        val pk = txt(this, "+", 17f, 0xFFFFFFFF.toInt(), true)
        pk.gravity = Gravity.CENTER
        pk.setShadowLayer(3f, 0f, 1f, 0x88000000.toInt())
        pk.background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(0xFFEF4444.toInt(), 0xFFF59E0B.toInt(), 0xFF84CC16.toInt(), 0xFF06B6D4.toInt(), 0xFF8B5CF6.toInt(), 0xFFEC4899.toInt())
        ).apply {
            shape = GradientDrawable.OVAL
            setStroke(dp(if (custom) 2 else 1), if (custom) Store.accent else P.faint)
        }
        val pkLp = LinearLayout.LayoutParams(dp(40), dp(40))
        pk.layoutParams = pkLp
        pk.isClickable = true
        pk.setOnClickListener { uiColorPicker() }
        swRow.addView(pk)
        hsv2.addView(swRow)
        ap.addView(hsv2)
        col.addView(ap)
        col.addView(vSpace(this, 16))

        col.addView(txt(this, "DATA", 12f, P.sub, true))
        col.addView(vSpace(this, 8))
        val dc = cardBox(this)
        dc.setPadding(dp(16), dp(6), dp(16), dp(6))
        val er = txt(this, "⬆  Export backup", 15f, P.ink)
        er.setPadding(0, dp(13), 0, dp(13))
        er.isClickable = true
        er.setOnClickListener { exportBackup() }
        dc.addView(er)
        dc.addView(hLine(this))
        val ir = txt(this, "⬇  Import backup", 15f, P.ink)
        ir.setPadding(0, dp(13), 0, dp(13))
        ir.isClickable = true
        ir.setOnClickListener {
            confirm("Restore backup?", "Your current groups will be replaced by the backup.", "Restore") {
                importBackup()
            }
        }
        dc.addView(ir)
        col.addView(dc)
        col.addView(vSpace(this, 20))

        val verName = try { packageManager.getPackageInfo(packageName, 0).versionName } catch (e: Exception) { "" }
        col.addView(txt(this, "Group Xpense v$verName", 12.5f, P.faint))
        col.addView(vSpace(this, 6))
        val credit = txt(this, "This app was made by Deon", 13.5f, P.accent)
        credit.paint.isUnderlineText = true
        credit.isClickable = true
        credit.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://deepakdeon.github.io")))
        }
        col.addView(credit)
        rootCol.addView(scrollOf(col))
        return rootCol
    }
}

