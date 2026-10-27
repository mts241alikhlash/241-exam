package id.sch.mts241alikhlash.cbt.data.session

class SessionRepository(private val store: SessionStore) {

    suspend fun current(): String? = store.load()

    suspend fun start(id: String) {
        val trimmed = id.trim()
        require(trimmed.isNotEmpty()) { "Session id must not be blank" }
        store.save(trimmed)
    }

    suspend fun finish() {
        store.clear()
    }
}
