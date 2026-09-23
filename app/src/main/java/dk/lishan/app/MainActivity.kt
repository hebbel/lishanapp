package dk.lishan.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dk.lishan.app.data.LishanDatabase
import dk.lishan.app.ui.LishanApp
import dk.lishan.app.ui.theme.LishanTheme

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
