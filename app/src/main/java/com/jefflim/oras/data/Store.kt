package com.jefflim.oras.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "oras")

/**
 * The whole app state is one JSON blob under one key. Writes are therefore atomic:
 * there is no window where kids exist but the rules that govern them do not.
 */
class Store(private val context: Context) {

    private val key = stringPreferencesKey("state")
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    val flow: Flow<AppState> = context.dataStore.data.map { prefs ->
        prefs[key]?.let {
            runCatching { json.decodeFromString(AppState.serializer(), it) }
                .getOrDefault(AppState())
        } ?: AppState()
    }

    suspend fun write(state: AppState) {
        val encoded = json.encodeToString(AppState.serializer(), state)
        context.dataStore.edit { it[key] = encoded }
    }
}
