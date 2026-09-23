package com.example.lishan

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.lishan.model.Flashcard
import com.example.lishan.ui.flashcard.FlashcardView
import com.example.lishan.ui.theme.LishanTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LishanTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    // Midlertidigt: viser ét kort direkte, indtil appen får en rigtig startside.
                    FlashcardView(
                        card = Flashcard(front = "hund", back = "dog"),
                        modifier = Modifier
                            .padding(innerPadding)
                            .padding(16.dp)
                    )
                }
            }
        }
    }
}

