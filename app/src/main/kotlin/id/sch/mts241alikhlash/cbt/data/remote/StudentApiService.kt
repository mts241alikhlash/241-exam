package id.sch.mts241alikhlash.cbt.data.remote

import id.sch.mts241alikhlash.cbt.data.model.MyClassroomDto
import id.sch.mts241alikhlash.cbt.data.model.StudentMeDto
import retrofit2.http.GET

interface StudentApiService {
    @GET("students/me")
    suspend fun me(): StudentMeDto

    @GET("students/me/classroom")
    suspend fun classroom(): MyClassroomDto?
}
