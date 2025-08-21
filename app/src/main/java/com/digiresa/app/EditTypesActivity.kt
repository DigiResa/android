package com.digiresa.app

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class EditTypesActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_RESTAURANT_ID = "restaurantId"
        const val EXTRA_RESTAURANT_NAME = "restaurantName"
        const val EXTRA_SELECTED = "selected" // String[] (types déjà cochés)
        private val ALL_TYPES = listOf("demandes","reservation","auto","annulation","palier")
    }

    private var restaurantId: Int = 0
    private lateinit var restaurantName: String
    private val selected = linkedSetOf<String>()

    private lateinit var listView: ListView
    private lateinit var adapter: TypesAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.title = intent.getStringExtra(EXTRA_RESTAURANT_NAME) ?: "Types"
        setContentView(R.layout.activity_edit_types)

        restaurantId = intent.getIntExtra(EXTRA_RESTAURANT_ID, 0)
        restaurantName = intent.getStringExtra(EXTRA_RESTAURANT_NAME) ?: ""
        (intent.getStringArrayExtra(EXTRA_SELECTED) ?: emptyArray()).forEach { selected.add(it) }

        listView = findViewById(R.id.typesList)
        adapter = TypesAdapter(ALL_TYPES, selected) { type, isOn ->
            if (isOn) selected.add(type) else selected.remove(type)
        }
        listView.adapter = adapter
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, 1, 0, "Tout")
        menu.add(0, 2, 1, "Enregistrer")
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            1 -> { // Tout
                selected.clear()
                selected.addAll(ALL_TYPES)
                adapter.notifyDataSetChanged()
                return true
            }
            2 -> { // Enregistrer
                save()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun save() {
        // garde uniquement les types connus
        val cleaned = selected.intersect(ALL_TYPES.toSet()).toList()
        val typesToSend = if (cleaned.size == ALL_TYPES.size) listOf("all") else cleaned

        CoroutineScope(Dispatchers.IO).launch {
            val ok = postTypes(restaurantId, typesToSend)
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    this@EditTypesActivity,
                    if (ok) "Sauvé" else "Échec",
                    Toast.LENGTH_SHORT
                ).show()
                if (ok) finish()
            }
        }
    }

    private fun postTypes(restaurantId: Int, types: List<String>): Boolean {
        return try {
            // adapte l’URL si besoin
            val url = URL("https://beta.digiresa.com/os/update_notif_types.php")
            val payload = JSONObject()
                .put("restaurant_id", restaurantId)
                .put("types", JSONArray(types))
                .put("token", FcmStore.token ?: "")

            val c = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                doOutput = true
            }
            c.outputStream.use { it.write(payload.toString().toByteArray()) }
            val body = c.inputStream.bufferedReader().readText()
            val obj = JSONObject(body)
            c.disconnect()
            obj.optBoolean("success", false)
        } catch (_: Exception) { false }
    }
}

/** Adapter simple pour lignes avec Switch */
private class TypesAdapter(
    private val items: List<String>,
    private val selected: Set<String>,
    private val onToggle: (type: String, isOn: Boolean) -> Unit
) : BaseAdapter() {

    override fun getCount() = items.size
    override fun getItem(position: Int) = items[position]
    override fun getItemId(position: Int) = position.toLong()

    override fun getView(position: Int, convertView: android.view.View?, parent: android.view.ViewGroup): android.view.View {
        val v = convertView ?: android.view.LayoutInflater.from(parent.context)
            .inflate(R.layout.row_type_switch, parent, false)

        val type = items[position]
        val title = v.findViewById<TextView>(R.id.typeTitle)
        val sw = v.findViewById<Switch>(R.id.typeSwitch)

        title.text = type.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        sw.setOnCheckedChangeListener(null)
        sw.isChecked = selected.contains(type)
        sw.setOnCheckedChangeListener { _, isOn -> onToggle(type, isOn) }

        return v
    }
}
