package dk.lishan.app.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migrations flytter en eksisterende database fra én version til den næste uden at miste data.
 * Room kører dem automatisk i rækkefølge, når appen åbner en database med en ældre version.
 *
 * Hver gang `version` i [LishanDatabase] tælles op, skal der skrives en ny migration her,
 * og den skal tilføjes i `addMigrations(...)`. SQL'en til nye tabeller kan kopieres fra
 * `createSql` i skemafilerne under `app/schemas/`.
 */

/**
 * 1 → 2: Kortenes `front`/`back` flyttes til den nye tabel `card_sides` som side 1 og 2.
 *
 * SQLite på ældre Android-versioner kan ikke fjerne kolonner, så `flashcards` bygges om:
 * ny tabel, kopiér data, slet den gamle, omdøb den nye.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // 1. Gem kortenes tekst, før den gamle tabel slettes.
        db.execSQL("CREATE TABLE `sides_tmp` AS SELECT `id` AS `cardId`, `front`, `back` FROM `flashcards`")

        // 2. Byg flashcards om uden front/back.
        db.execSQL(
            "CREATE TABLE `flashcards_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`deckId` INTEGER NOT NULL, FOREIGN KEY(`deckId`) REFERENCES `decks`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        db.execSQL("INSERT INTO `flashcards_new` (`id`, `deckId`) SELECT `id`, `deckId` FROM `flashcards`")
        db.execSQL("DROP TABLE `flashcards`")
        db.execSQL("ALTER TABLE `flashcards_new` RENAME TO `flashcards`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_flashcards_deckId` ON `flashcards` (`deckId`)")

        // 3. Opret card_sides og fyld den med den gemte tekst. Tomme sider springes over.
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `card_sides` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`cardId` INTEGER NOT NULL, `position` INTEGER NOT NULL, `text` TEXT NOT NULL, " +
                "FOREIGN KEY(`cardId`) REFERENCES `flashcards`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_card_sides_cardId_position` " +
                "ON `card_sides` (`cardId`, `position`)"
        )
        db.execSQL(
            "INSERT INTO `card_sides` (`cardId`, `position`, `text`) " +
                "SELECT `cardId`, 1, `front` FROM `sides_tmp` WHERE TRIM(`front`) != ''"
        )
        db.execSQL(
            "INSERT INTO `card_sides` (`cardId`, `position`, `text`) " +
                "SELECT `cardId`, 2, `back` FROM `sides_tmp` WHERE TRIM(`back`) != ''"
        )

        // 4. Ryd op.
        db.execSQL("DROP TABLE `sides_tmp`")
    }
}

/**
 * 2 → 3: Ny tabel til labels på et decks sider. Eksisterende decks får ingen labels;
 * de kan tilføjes bagefter under "Rediger".
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `deck_side_labels` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`deckId` INTEGER NOT NULL, `position` INTEGER NOT NULL, `label` TEXT NOT NULL, " +
                "FOREIGN KEY(`deckId`) REFERENCES `decks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_deck_side_labels_deckId_position` " +
                "ON `deck_side_labels` (`deckId`, `position`)"
        )
    }
}

/**
 * 3 → 4: Kort får noter og kommentar. Kolonner kan tilføjes direkte (i modsætning til at
 * fjerne dem), så tabellen skal ikke bygges om. Eksisterende kort får tomme felter.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `flashcards` ADD COLUMN `notes` TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE `flashcards` ADD COLUMN `comment` TEXT NOT NULL DEFAULT ''")
    }
}

/**
 * 4 → 5: Mapper. Ny tabel folders, og decks får kolonnen folderId, der peger på en mappe.
 * Alle eksisterende decks får folderId = NULL, dvs. de ligger direkte på forsiden.
 *
 * decks bygges ikke om (som i 1 → 2): at slette den gamle tabel kunne slette kort, sider og
 * labels med via CASCADE. SQLite tillader i stedet at tilføje en kolonne med fremmednøgle,
 * når den ikke har en standardværdi (den bliver så NULL).
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `folders` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, `courseId` INTEGER)"
        )
        db.execSQL(
            "ALTER TABLE `decks` ADD COLUMN `folderId` INTEGER " +
                "REFERENCES `folders`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_decks_folderId` ON `decks` (`folderId`)")
    }
}
