package com.digiresa.app

import android.Manifest
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.core.view.updatePadding
import com.getcapacitor.BridgeActivity
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class MainActivity : BridgeActivity() {

    private var headerBar: LinearLayout? = null
    private var linkButton: Button? = null
    private val exec = Executors.newSingleThreadExecutor()

    private val registerURL    = "https://beta.digiresa.com/os/register.php"
    private val checkURL       = "https://beta.digiresa.com/os/check_device_binding.php"
    private val requestGuidURL = "https://beta.digiresa.com/os/request_guid_api.php"
    private val linkURL        = "https://beta.digiresa.com/os/link_device_api.php"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        installHeader()
        installLinkButton()
        askPostNotificationsIfNeeded()

        // Splash/Intro (à chaque ouverture comme iOS)
        startActivity(Intent(this, IntroActivity::class.java))
    }

    override fun onResume() {
        super.onResume()
        updateUiFromBinding()
    }

    /** Header violet + logo + cloche */
    private fun installHeader() {
        val root = findViewById<FrameLayout>(android.R.id.content)

        // barre
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#660E7A"))
            elevation = 8f
            setPadding(dp(12), statusBarHeight() + dp(4), dp(12), dp(8))
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // logo (mets res/drawable/logo.png)
        val logo = ImageView(this).apply {
            setImageResource(R.drawable.logo)
            adjustViewBounds = true
            layoutParams = LinearLayout.LayoutParams(dp(120), dp(28))
        }

        val space = Space(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, 0, 1f)
        }

   // cloche
val bell = ImageButton(this).apply {
    setImageResource(R.drawable.ic_bell) // vector drawable (voir plus bas)
    background = null
    setColorFilter(Color.WHITE)
setOnClickListener { openLinkFlow() }
}

        bar.addView(logo)
        bar.addView(space)
        bar.addView(bell)
        root.addView(bar)

        headerBar = bar

        // décale la WebView sous le header (padding)
        bridge?.webView?.post {
            val h = bar.measuredHeight.takeIf { it > 0 } ?: dp(56) + statusBarHeight()
            bridge?.webView?.updatePadding(top = h)
        }
    }

    /** Bouton “Lier” sous le header */
    private fun installLinkButton() {
        val root = findViewById<FrameLayout>(android.R.id.content)
        val btn = Button(this).apply {
            text = "Lier"
            setAllCaps(false)
            alpha = 0.92f
            // au lieu de mtrl_btn_filled_background :
            setBackgroundColor(Color.parseColor("#801299")) // violet DigiResa
            setTextColor(Color.WHITE)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.TOP or Gravity.END
                topMargin = (headerBar?.height ?: dp(56)) + statusBarHeight() + dp(8)
                rightMargin = dp(12)
            }
            setOnClickListener { openLinkFlow() }
            visibility = View.GONE
        }

        root.addView(btn)
        linkButton = btn
    }

    /** Permissions Android 13+ */
    private fun askPostNotificationsIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
        }
    }

    /** Ouvre la feuille paramètres (simple Toast ici, plug tes écrans natifs si besoin) */
    private fun openSettingsSheet() {
        Toast.makeText(this, "Ouvrir Paramètres", Toast.LENGTH_SHORT).show()
        // TODO: implémenter un écran natif si tu veux (liste de liens, etc.)
    }

    /** Flow “Obtenir GUID” -> “Coller GUID” -> link */
    private fun openLinkFlow() {
        // 1. demander 4 chiffres
        val inputLast4 = EditText(this).apply { hint = "1234"; inputType = android.text.InputType.TYPE_CLASS_NUMBER }
        AlertDialogBuilder(this)
            .title("Obtenir le GUID")
            .message("Entrer les 4 derniers chiffres du téléphone admin.")
            .setView(inputLast4)
            .negative("Annuler"){}
            .positive("Envoyer SMS") {
                val last4 = inputLast4.text.toString()
                requestGuidByLast4(last4) { ok, err ->
                    runOnUiThread {
                        AlertDialogBuilder(this)
                            .title(if (ok) "OK" else "Erreur")
                            .message(if (ok) "SMS envoyé avec le GUID." else (err ?: "Échec envoi SMS"))
                            .positive("Continuer") {
                                // 2. coller GUID
                                val inputGuid = EditText(this).apply { hint = "xxxxxxxx-xxxx-...." }
                                AlertDialogBuilder(this)
                                    .title("Coller le GUID")
                                    .setView(inputGuid)
                                    .negative("Annuler"){}
                                    .positive("Lier") {
                                        linkDevice(inputGuid.text.toString()) { ok2, err2 ->
                                            runOnUiThread {
                                                Toast.makeText(
                                                    this,
                                                    if (ok2) "Restaurant ajouté." else (err2 ?: "Lien impossible"),
                                                    Toast.LENGTH_LONG
                                                ).show()
                                                if (ok2) updateUiFromBinding()
                                            }
                                        }
                                    }.show()
                            }.show()
                    }
                }
            }.show()
    }

    /** Vérifie le bind serveur pour afficher/masquer bouton + cloche (ici bouton) */
    private fun updateUiFromBinding() {
        val token = FcmStore.token ?: return
        postJson(checkURL, JSONObject().put("token", token)) { ok, body ->
            if (!ok || body == null) return@postJson
            val success = body.optBoolean("success", false)
            val bound = body.optBoolean("bound", false)
            val showLinkButton = body.optBoolean("showLinkButton", !bound)
            runOnUiThread {
                linkButton?.visibility = if (success && !bound && showLinkButton) View.VISIBLE else View.GONE
            }
        }
    }

    /** API helpers **/

    private fun requestGuidByLast4(last4: String, cb: (Boolean, String?) -> Unit) {
        val json = JSONObject().put("phone_last4", last4)
        postJson(requestGuidURL, json) { ok, body ->
            val err = body?.optString("error")
            cb(ok && body?.optBoolean("success") == true, if (ok) err else "NET")
        }
    }

    private fun linkDevice(guid: String, cb: (Boolean, String?) -> Unit) {
        val token = FcmStore.token ?: return cb(false, "Token indisponible")
        val json = JSONObject().put("token", token).put("guid", guid).put("notif_type", "all")
        postJson(linkURL, json) { ok, body ->
            val success = ok && body?.optBoolean("success") == true
            cb(success, if (success) null else body?.optString("error") ?: "Lien impossible")
        }
    }

    private fun postJson(urlStr: String, json: JSONObject, cb: (Boolean, JSONObject?) -> Unit) {
        exec.execute {
            try {
                val url = URL(urlStr)
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json")
                    doOutput = true
                }
                conn.outputStream.use { it.write(json.toString().toByteArray(Charsets.UTF_8)) }
                val code = conn.responseCode
                val txt = (if (code in 200..299) conn.inputStream else conn.errorStream)
                    ?.bufferedReader()?.readText()
                val obj = if (!txt.isNullOrEmpty()) JSONObject(txt) else null
                cb(code in 200..299, obj)
            } catch (e: Exception) {
                cb(false, null)
            }
        }
    }

    private fun dp(v: Int) = (resources.displayMetrics.density * v).toInt()
    private fun statusBarHeight(): Int {
        val id = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) else 0
    }
}

/** mini helper AlertDialog fluide */
class AlertDialogBuilder(private val ctx: MainActivity) {
    private val builder = androidx.appcompat.app.AlertDialog.Builder(ctx)
    fun title(t: String) = apply { builder.setTitle(t) }
    fun message(m: String) = apply { builder.setMessage(m) }
    fun setView(v: View) = apply { builder.setView(v) }
    fun positive(text: String, onClick: () -> Unit) = apply {
        builder.setPositiveButton(text) { _, _ -> onClick() }
    }
    fun negative(text: String, onClick: () -> Unit) = apply {
        builder.setNegativeButton(text) { _, _ -> onClick() }
    }
    fun show() = builder.show()
}
