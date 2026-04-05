package com.rocketlauncher.data.github

import retrofit2.http.GET
import retrofit2.http.Headers

interface GitHubApi {
    @GET("repos/vpaull11/RocketLauncher/releases/latest")
    @Headers("Accept: application/vnd.github+json")
    suspend fun getLatestRelease(): GitHubReleaseDto
}
