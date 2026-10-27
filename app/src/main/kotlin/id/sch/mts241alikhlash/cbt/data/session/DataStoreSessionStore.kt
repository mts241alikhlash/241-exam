package id.sch.mts241alikhlash.cbt.data.session

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.sessionDataStore by preferencesDataStore(name = "exam_session")

class DataStoreSessionStore(private val context: Context) : SessionStore {

    override suspend fun load(): String? =
        context.sessionDataStore.data.first()[SESSION_ID]

    override suspend fun save(id: String) {
        context.sessionDataStore.edit { preferences ->
            preferences[SESSION_ID] = id
        }
    }

    override suspend fun clear() {
        context.sessionDataStore.edit { preferences ->
            preferences.remove(SESSION_ID)
        }
    }

    private companion object {
        val SESSION_ID = stringPreferencesKey("session_id")
    }
}
