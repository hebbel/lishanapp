package dk.lishan.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import dk.lishan.app.data.DeckRepository
import dk.lishan.app.data.LishanDatabase
import dk.lishan.app.ui.LishanApp
import dk.lishan.app.ui.LishanViewModel
import dk.lishan.app.ui.theme.LishanTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repository = DeckRepository(LishanDatabase.getInstance(this).deckDao())
        setContent {
            LishanTheme {
                // `viewModel` giver den samme ViewModel tilbage, også efter at telefonen er drejet.
                LishanApp(viewModel = viewModel(factory = LishanViewModel.factory(repository)))
            }
        }
    }
}
