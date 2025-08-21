package com.digiresa.app

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import kotlin.concurrent.schedule

class IntroActivity : AppCompatActivity() {
    private val updatesURL = "https://beta.digiresa.com/os/maj.php"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#8C10A3"))
            setPadding(dp(16), dp(52), dp(16), dp(16))
        }

        val logo = ImageView(this).apply {
            setImageResource(R.drawable.logo)
            adjustViewBounds = true
            layoutParams = LinearLayout.LayoutParams(dp(200), dp(200)).apply {
                gravity = android.view.Gravity.CENTER_HORIZONTAL
                topMargin = dp(24)
            }
        }

        val header = TextView(this).apply {
            text = "DERNIÈRES MÀJ"
            setTextColor(Color.WHITE)
            textSize = 20f
            setPadding(0, dp(8), 0, dp(8))
            gravity = android.view.Gravity.CENTER
        }

        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            gravity = android.view.Gravity.CENTER_HORIZONTAL
        }

        val skip = Button(this).apply {
            text = "Skip"
            setAllCaps(false)
            setTextColor(Color.WHITE)
            backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#33FFFFFF"))
            layoutParams = LinearLayout.LayoutParams(dp(120), dp(40)).apply {
                gravity = android.view.Gravity.CENTER_HORIZONTAL
                topMargin = dp(24)
            }
            setOnClickListener { finish() }
        }

        root.addView(logo)
        root.addView(header)
        root.addView(list)
        root.addView(skip)
        setContentView(root)

        // Auto-close après 10s
        Timer().schedule(10000) { runOnUiThread { if (!isFinishing) finish() } }

        // Fetch updates
        Thread {
            try {
                val conn = URL(updatesURL).openConnection() as HttpURLConnection
                val text = conn.inputStream.bufferedReader().readText()
                val obj = JSONObject(text)
                val arr = obj.optJSONArray("updates") ?: return@Thread
                runOnUiThread {
                    repeat(minOf(3, arr.length())) { i ->
                        val it = arr.getJSONObject(i)
                        val title = it.optString("title")
                        val desc = it.optString("description")
                        val created = it.optString("created_at")
                        list.addView(updateCard(title, desc, prettyDate(created)))
                    }
                }
            } catch (_: Exception) { /* ignore */ }
        }.start()
    }

    private fun updateCard(title: String, desc: String, dateRight: String): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = resources.getDrawable(R.drawable.card_bg, theme)
            layoutParams = LinearLayout.LayoutParams(dp(300), LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(8)
                gravity = android.view.Gravity.CENTER_HORIZONTAL
            }
            alpha = 0f
            scaleX = .9f; scaleY = .9f
            animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(300).start()
        }

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val titleView = TextView(this).apply {
            text = title
            setTextColor(Color.WHITE)
            textSize = 16f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val dateView = TextView(this).apply {
            text = dateRight
            setTextColor(Color.parseColor("#CCFFFFFF"))
            textSize = 12f
        }
        row.addView(titleView); row.addView(dateView)

        val descView = TextView(this).apply {
            text = desc
            setTextColor(Color.parseColor("#F2FFFFFF"))
            textSize = 14f
        }

        card.addView(row)
        card.addView(descView)
        return card
    }

    private fun prettyDate(createdAt: String): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.FRANCE)
        val d = runCatching { fmt.parse(createdAt) }.getOrNull() ?: return ""
        val cal = Calendar.getInstance()
        val today = Calendar.getInstance()
        cal.time = d
        return when {
            isSameDay(cal, today) -> "Aujourd’hui"
            isSameDay(cal, today.apply { add(Calendar.DAY_OF_YEAR, -1) }) -> "Hier"
            isSameDay(cal, Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -2) }) -> "Avant-hier"
            else -> SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE).format(d)
        }
    }
    private fun isSameDay(a: Calendar, b: Calendar) =
        a.get(Calendar.YEAR)==b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR)==b.get(Calendar.DAY_OF_YEAR)

    private fun dp(v: Int) = (resources.displayMetrics.density * v).toInt()
}
