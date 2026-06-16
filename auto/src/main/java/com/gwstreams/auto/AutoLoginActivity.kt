package com.gwstreams.auto

import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.gwstreams.app.data.repo.XtreamRepository
import kotlinx.coroutines.launch

/**
 * Simple phone-side login. Saves credentials so the Android Auto media service
 * can authenticate on its own in the car. Built with classic views to keep the
 * auto module free of a Compose dependency.
 */
class AutoLoginActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pad = (24 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(pad, pad, pad, pad)
            setBackgroundColor(0xFF0A0E14.toInt())
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val title = TextView(this).apply {
            text = "Great White Streams — Car"
            textSize = 24f
            setTextColor(0xFFF2F6FA.toInt())
        }
        val subtitle = TextView(this).apply {
            text = "Sign in once. Audio then plays on your car screen via Android Auto."
            textSize = 14f
            setTextColor(0xFFA6B3C2.toInt())
            setPadding(0, 8, 0, pad)
        }

        val hostField = EditText(this).apply { hint = "Server host (https://host:port)"; setTextColor(0xFFF2F6FA.toInt()) }
        val userField = EditText(this).apply { hint = "Username"; setTextColor(0xFFF2F6FA.toInt()) }
        val passField = EditText(this).apply { hint = "Password"; setTextColor(0xFFF2F6FA.toInt()) }

        val status = TextView(this).apply {
            setTextColor(0xFFFF6B6B.toInt())
            setPadding(0, pad, 0, 0)
        }

        val button = Button(this).apply {
            text = "Save & connect"
            setBackgroundColor(0xFF2DE2C4.toInt())
            setTextColor(0xFF0A0E14.toInt())
        }

        button.setOnClickListener {
            val host = hostField.text.toString().trim()
            val user = userField.text.toString().trim()
            val pass = passField.text.toString().trim()
            if (host.isBlank() || user.isBlank() || pass.isBlank()) {
                status.text = "Fill in all fields."
                return@setOnClickListener
            }
            status.setTextColor(0xFFA6B3C2.toInt())
            status.text = "Connecting…"
            lifecycleScope.launch {
                val repo = XtreamRepository()
                val result = repo.login(host, user, pass)
                result.fold(
                    onSuccess = {
                        CredentialStore(this@AutoLoginActivity).save(repo.normalizeHost(host), user, pass)
                        Toast.makeText(this@AutoLoginActivity,
                            "Connected. Open Great White Streams in Android Auto.",
                            Toast.LENGTH_LONG).show()
                        status.setTextColor(0xFF2DE2C4.toInt())
                        status.text = "Connected. You can close this and use your car screen."
                    },
                    onFailure = { e ->
                        status.setTextColor(0xFFFF6B6B.toInt())
                        status.text = e.message ?: "Login failed."
                    }
                )
            }
        }

        root.addView(title)
        root.addView(subtitle)
        root.addView(hostField)
        root.addView(userField)
        root.addView(passField)
        root.addView(button)
        root.addView(status)
        setContentView(root)
    }
}
