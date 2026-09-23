package com.example.lishan.ui

import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList

/**
 * Fortæller `rememberSaveable`, hvordan en state-liste af tekst gemmes: som en almindelig liste,
 * der genskabes som en state-liste. Bruges til formularer med en variabel mængde tekstfelter.
 */
val StringListSaver = listSaver<SnapshotStateList<String>, String>(
    save = { it.toList() },
    restore = { it.toMutableStateList() },
)
