package com.roro.terminal

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import com.rk.terminal.api.ReTerminal

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val button = Button(this).apply {
            text = getString(R.string.open_terminal)
            setOnClickListener {
                ReTerminal.openFull(this@MainActivity)
            }
        }

        val root = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
            addView(
                button,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        }

        setContentView(root)
    }
}
