package com.autoglm.helper.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.autoglm.helper.R
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_shell)

        val nav = findViewById<BottomNavigationView>(R.id.bottomNav)
        nav.setOnItemSelectedListener {
            when (it.itemId) {
                R.id.menu_run -> switch(RunFragment())
                R.id.menu_commands -> switch(CommandFragment())
                R.id.menu_settings -> switch(SettingsFragment())
            }
            true
        }
        if (savedInstanceState == null) {
            switch(RunFragment())
        }
    }

    private fun switch(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }
}
