package dk.lishan.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import dk.lishan.app.data.DeckRepository
import dk.lishan.app.data.LishanDatabase
import dk.lishan.app.data.remote.FakeLishanApi
import dk.lishan.app.ui.LishanApp
import dk.lishan.app.ui.LishanViewModel
import dk.lishan.app.ui.theme.LishanTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Indtil serverens API er klar, bruges et falsk API med et demokursus.
        val repository = DeckRepository(LishanDatabase.getInstance(this), FakeLishanApi())
        setContent {
            LishanTheme {
                // `viewModel` giver den samme ViewModel tilbage, også efter at telefonen er drejet.
                LishanApp(viewModel = viewModel(factory = LishanViewModel.factory(repository)))
            }
        }
    }
}
