package com.example.lishan

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.lishan.data.LishanDatabase
import com.example.lishan.ui.LishanApp
import com.example.lishan.ui.theme.LishanTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val dao = LishanDatabase.getInstance(this).deckDao()
        setContent {
            LishanTheme {
                LishanApp(dao = dao)
            }
        }
    }
}
