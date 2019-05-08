package com.raumfeld.wamp.examples.android

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.raumfeld.wamp.RandomIdGenerator
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        textField.text = "" + RandomIdGenerator().newRandomId()
    }
}
