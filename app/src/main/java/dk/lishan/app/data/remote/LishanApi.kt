package dk.lishan.app.data.remote

/**
 * Det, appen kan spørge Lishan-serveren om. Et interface, så den rigtige server og
 * [FakeLishanApi] kan skiftes ud, uden at resten af appen mærker forskel.
 *
 * Alle funktioner kan kaste en undtagelse, fx når der ikke er net. Den, der kalder, skal
 * håndtere det og beholde de data, appen allerede har.
 */
interface LishanApi {
    suspend fun getCourses(): List<CourseDto>

    suspend fun getLessons(courseId: Long): List<LessonDto>

    suspend fun getLessonCards(courseId: Long, lessonId: String): LessonCardsDto
}
