package id.sch.mts241alikhlash.cbt.data.session

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.sessionDataStore by preferencesDataStore(name = "exam_session")

class DataStoreSessionStore(private val context: Context) : SessionStore {

    override suspend fun load(): String? =
        context.sessionDataStore.data.first()[SESSION_ID]

    override suspend fun loadDeadline(): Long? =
        context.sessionDataStore.data.first()[DEADLINE]

    override suspend fun save(id: String, deadline: Long) {
        context.sessionDataStore.edit { preferences ->
            preferences[SESSION_ID] = id
            preferences[DEADLINE] = deadline
        }
    }

    override suspend fun clear() {
        context.sessionDataStore.edit { preferences ->
            preferences.remove(SESSION_ID)
            preferences.remove(DEADLINE)
        }
    }

    private companion object {
        val SESSION_ID = stringPreferencesKey("session_id")
        val DEADLINE = longPreferencesKey("exam_deadline")
    }
}
