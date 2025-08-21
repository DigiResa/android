package com.digiresa.app
import android.content.Intent

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class HomeActivity : AppCompatActivity() {

    private lateinit var linkButton: FloatingActionButton
    private lateinit var settingsButton: FloatingActionButton

    override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_home)

    linkButton = findViewById(R.id.linkButton)
    settingsButton = findViewById(R.id.settingsButton)

    linkButton.setOnClickListener {
        openLinkModal()
    }
        settingsButton.setOnClickListener {
            val i = Intent(this, EditTypesActivity::class.java).apply {
                putExtra(EditTypesActivity.EXTRA_RESTAURANT_ID, 123) // ⚠️ mettre le vrai ID
                putExtra(EditTypesActivity.EXTRA_RESTAURANT_NAME, "Mon resto") // ⚠️ idem
                putExtra(
                    EditTypesActivity.EXTRA_SELECTED,
                    arrayOf("demandes","reservation") // ⚠️ données réelles du backend
                )
            }
            startActivity(i)
        }




    // 👉 Vérifie si lancé via une notif
    if (intent.getBooleanExtra("openLinkModal", false)) {
        openLinkModal() // ouvre direct ton modal GUID
    }
}


    override fun onResume() {
        super.onResume()
        refreshBindingUI()
    }

    private fun refreshBindingUI() {
        val token = FcmStore.token ?: ""
        if (token.isEmpty()) {
            linkButton.hide()
            settingsButton.hide()
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val json = JSONObject().put("token", token)
                val conn = (URL("https://beta.digiresa.com/os/check_device_binding.php").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json")
                    doOutput = true
                }
                conn.outputStream.use { it.write(json.toString().toByteArray()) }
                val response = conn.inputStream.bufferedReader().readText()
                conn.disconnect()

                val obj = JSONObject(response)
                val bound = obj.optBoolean("bound", false)
                val showLinkButton = obj.optBoolean("showLinkButton", !bound)

                withContext(Dispatchers.Main) {
                    if (bound) {
                        settingsButton.show()
                        linkButton.hide()
                    } else {
                        settingsButton.hide()
                        if (showLinkButton) linkButton.show() else linkButton.hide()
                    }
                }
            } catch (e: Exception) {
                // En cas d'erreur silencieuse
                withContext(Dispatchers.Main) {
                    linkButton.hide()
                    settingsButton.hide()
                }
            }
        }
    }

    private fun openSettings() {
        AlertDialog.Builder(this)
            .setTitle("Paramètres")
            .setMessage("Écran paramètres ici.")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun openLinkModal() {
        val input = android.widget.EditText(this).apply {
            hint = "1234"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }
        AlertDialog.Builder(this)
            .setTitle("Obtenir le GUID")
            .setMessage("Entre les 4 derniers chiffres du téléphone admin.")
            .setView(input)
            .setNegativeButton("Annuler", null)
            .setPositiveButton("Envoyer SMS") { _, _ ->
                val last4 = input.text.toString()
                // TODO: implémenter requestGuidByLast4
                Toast.makeText(this, "SMS demandé pour $last4", Toast.LENGTH_SHORT).show()
                askGuidAndLink()
            }
            .show()
    }

    private fun askGuidAndLink() {
        val input = android.widget.EditText(this).apply {
            hint = "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
            inputType = android.text.InputType.TYPE_CLASS_TEXT
        }
        AlertDialog.Builder(this)
            .setTitle("Coller le GUID")
            .setMessage("Colle le GUID reçu par SMS.")
            .setView(input)
            .setNegativeButton("Annuler", null)
            .setPositiveButton("Lier") { _, _ ->
                val guid = input.text.toString().trim()
                // TODO: implémenter linkDeviceWithGUID
                Toast.makeText(this, "GUID $guid lié (simulation)", Toast.LENGTH_SHORT).show()
                refreshBindingUI()
            }
            .show()
    }
}
