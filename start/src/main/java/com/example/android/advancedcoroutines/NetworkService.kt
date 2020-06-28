package com.example.android.advancedcoroutines

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET

class NetworkService {

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://raw.githubusercontent.com/")
        .client(OkHttpClient())
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val sunflowerService = retrofit.create(SunflowerService::class.java)

    suspend fun allPlants(): List<Plant> = withContext(Dispatchers.Default) {
        val result = sunflowerService.getAllPlants()

        //Return a list of all Plants with the elements randomly shuffled:
        result.shuffled()
    }

    suspend fun plantsByGrowZone(growZone: GrowZone) = withContext(Dispatchers.Default) {
        val result = sunflowerService.getAllPlants()

        //Return a list of Plants containing only those plants matching the given growzone
        result.filter { it.growZoneNumber == growZone.number }.shuffled()
    }

    suspend fun sponsoredPlantsId(): List<String> = withContext(Dispatchers.Default) {
        val result = sunflowerService.sponsoredPlants()

        //transform list of Plants to list of ID as strings:
        result.map { plant -> plant.plantId }
    }
}

interface SunflowerService {
    @GET("googlecodelabs/kotlin-coroutines/master/advanced-coroutines-codelab/sunflower/src/main/assets/plants.json")
    suspend fun getAllPlants() : List<Plant>

    @GET("googlecodelabs/kotlin-coroutines/master/advanced-coroutines-codelab/sunflower/src/main/assets/custom_plant_sort_order.json")
    suspend fun sponsoredPlants() : List<Plant>
}