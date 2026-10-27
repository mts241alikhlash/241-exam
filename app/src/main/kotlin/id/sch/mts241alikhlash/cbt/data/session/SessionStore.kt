package id.sch.mts241alikhlash.cbt.data.session

interface SessionStore {
    suspend fun load(): String?
    suspend fun save(id: String)
    suspend fun clear()
}
