package com.example.projectemicroserveis

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

interface ApiService {
    @GET("/processos")
    fun getProcessos(@Query("contrassenya") contrassenya: String): Call<List<Proces>>
    @GET("/logs")
    fun getLogs(@Query("contrassenya") contrassenya: String): Call<List<String>>
}

object RetrofitInstance {
    private const val BASE_URL = "http://tr2g6.dam.inspedralbes.cat:22555"

    val api: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}