package dk.lishan.app.data.remote

/**
 * Det, appen kan spørge Lishan-serveren om. Et interface, så den rigtige server og
 * [FakeLishanApi] kan skiftes ud, uden at resten af appen mærker forskel.
 *
 * Alle funktioner kan kaste en undtagelse, fx når der ikke er net ([java.io.IOException]), når
 * brugeren ikke er logget ind ([NotLoggedInException]), eller når serveren svarer med en fejl
 * ([ApiException]). Den, der kalder, skal håndtere det og beholde de data, appen allerede har.
 */
interface LishanApi {
    suspend fun getCourses(): List<CourseDto>

    suspend fun getLessons(courseId: Long): List<LessonDto>

    suspend fun getLessonCards(courseId: Long, lessonId: String): LessonCardsDto

    /** Den indloggede bruger. */
    suspend fun getCurrentUser(): UserDto

    /** Logger ud på serveren (tokens tilbagekaldes) og glemmer login'et i appen. */
    suspend fun logout()
}

/** Brugeren er ikke logget ind, eller login'et er udløbet. Brugeren skal logge ind (igen). */
class NotLoggedInException : Exception("Ikke logget ind")

/** Serveren svarede med en fejl. [code] er serverens fejlkode, fx `not_found` (se docs/app-api.md). */
class ApiException(val status: Int, val code: String) : java.io.IOException("Serverfejl $status: $code")
