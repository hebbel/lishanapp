package dk.lishan.app.ui.courses

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dk.lishan.app.R
import dk.lishan.app.data.remote.CourseDto
import dk.lishan.app.data.remote.CourseSideDto
import dk.lishan.app.data.remote.LanguageDto
import dk.lishan.app.ui.theme.LishanTheme

/** Om brugeren er logget ind hos Lishan-serveren. */
sealed interface LoginState {
    /** Det falske API kræver ikke login. */
    data object NotRequired : LoginState
    data object LoggedOut : LoginState
    /** Login-siden er åben i browseren. */
    data object LoggingIn : LoginState
    /** [name] er `null`, indtil navnet er hentet fra serveren. */
    data class LoggedIn(val name: String?) : LoginState
    data class Failed(val message: String) : LoginState
}

/** Hvor langt appen er med at hente listen over kurser fra serveren. */
sealed interface CoursesState {
    data object Loading : CoursesState
    data class Loaded(val courses: List<CourseDto>) : CoursesState
    data class Failed(val message: String) : CoursesState
}

/**
 * Listen over kurser på serveren. Et tryk forbinder til kurset: det bliver til en mappe på
 * forsiden med kursets lektioner (endnu ikke hentet).
 *
 * Kræver serveren login, vises først en knap til at logge ind ([onLogin] åbner Lishans login i
 * browseren); når brugeren er logget ind, vises navnet, "Log ud" og kurserne.
 *
 * @param connectedCourseIds kurser, der allerede er forbundet; de vises med "Forbundet".
 * @param connectingCourseId kurset, der er ved at blive forbundet, hvis nogen (vises med en snurretop).
 * @param message en besked, fx hvis forbindelsen fejlede.
 */
@Composable
fun CoursesScreen(
    state: CoursesState,
    connectedCourseIds: Set<Long>,
    connectingCourseId: Long?,
    message: String?,
    loginState: LoginState,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
    onConnect: (CourseDto) -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
                Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Tilbage til forsiden")
            }
            Text(
                "Forbind kursus",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        message?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp))
        }

        when (loginState) {
            LoginState.LoggedOut, is LoginState.Failed -> {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text("Log ind med dit Lishan-login for at se dine kurser.")
                    if (loginState is LoginState.Failed) {
                        Text(loginState.message, color = MaterialTheme.colorScheme.error)
                    }
                    Button(onClick = onLogin) { Text("Log ind") }
                }
                return@Column
            }
            LoginState.LoggingIn -> {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    CircularProgressIndicator()
                    Text("Logger ind …")
                    // Lukkede brugeren browseren uden at logge ind, kan de prøve igen herfra.
                    TextButton(onClick = onLogin) { Text("Prøv igen") }
                }
                return@Column
            }
            is LoginState.LoggedIn -> Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Logget ind som ${loginState.name ?: "…"}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onLogout) { Text("Log ud") }
            }
            LoginState.NotRequired -> {}
        }

        when (state) {
            CoursesState.Loading -> Column(
                modifier = Modifier.fillMaxWidth().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                CircularProgressIndicator()
                Text("Henter kurser …")
            }

            is CoursesState.Failed -> Column(
                modifier = Modifier.fillMaxWidth().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(state.message)
                Button(onClick = onRetry) { Text("Prøv igen") }
            }

            is CoursesState.Loaded -> LazyColumn {
                if (state.courses.isEmpty()) {
                    item { Text("Du har ikke adgang til nogen kurser.", modifier = Modifier.padding(16.dp)) }
                }
                items(state.courses, key = { it.id }) { course ->
                    val connected = course.id in connectedCourseIds
                    ListItem(
                        headlineContent = { Text(course.name) },
                        supportingContent = {
                            Text(if (connected) "Forbundet – tryk for at åbne" else "Tryk for at forbinde")
                        },
                        leadingContent = { Icon(painterResource(R.drawable.ic_folder), contentDescription = null) },
                        trailingContent = {
                            if (course.id == connectingCourseId) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            }
                        },
                        modifier = Modifier.clickable(enabled = connectingCourseId == null) { onConnect(course) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun CoursesScreenPreview() {
    LishanTheme {
        CoursesScreen(
            state = CoursesState.Loaded(
                listOf(
                    CourseDto(71, "Arabisk (demo)", LanguageDto(1, "ARA"), listOf(CourseSideDto(1, "Dansk"))),
                    CourseDto(72, "Irakisk", LanguageDto(12, "IA"), emptyList()),
                )
            ),
            connectedCourseIds = setOf(71),
            connectingCourseId = null,
            message = null,
            loginState = LoginState.LoggedIn("Demobruger"),
            onLogin = {},
            onLogout = {},
            onConnect = {},
            onRetry = {},
            onBack = {},
        )
    }
}
