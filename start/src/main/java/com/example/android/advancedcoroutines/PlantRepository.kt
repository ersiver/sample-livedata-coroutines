package com.example.android.advancedcoroutines

import androidx.annotation.AnyThread
import androidx.lifecycle.LiveData
import androidx.lifecycle.liveData
import androidx.lifecycle.map
import androidx.lifecycle.switchMap
import com.example.android.advancedcoroutines.util.CacheOnSuccess
import com.example.android.advancedcoroutines.utils.ComparablePair
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repository module for handling data operations.
 *
 * This PlantRepository exposes two UI-observable database queries [plants] and
 * [getPlantsWithGrowZone].
 *
 * To update the plants cache, call [tryUpdateRecentPlantsForGrowZoneCache] or
 * [tryUpdateRecentPlantsCache].
 */
class PlantRepository private constructor(
    private val plantDao: PlantDao,
    private val plantService: NetworkService,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default
) {

    /**
     * Fetch a list of [Plant]s from the database.
     *
     * Here we have a suspend function to fetch a LiveData list of plants from the database,
     * while also calling a suspend function to get the sponsored plants id list.
     * We combine these two values to sort the list of plants and return the LiveData list of plants
     * in a custom order.
     *
     * The LiveData builder allows us to calculate values asynchronously, as liveData is backed by coroutines.
     */
    val plants: LiveData<List<Plant>> = liveData<List<Plant>> {
        val plantsLiveData = plantDao.getPlants()

        //Request sponsored plants ids. With getOrAwait() the result will be cached after 1st call.
        val sponsoredPlantsIdList = sponsoredPlantsIdCached.getOrAwait()

        //Combine plantsLiveData and sponsoredPlantsIdList. Applying transformation on the plantsLiveData value,
        // to sort the list. Add it as as source to emitSource(). Each call to emitSource() removes the previously-added source.
        emitSource(plantsLiveData.map { plantList ->
            plantList.applySort(sponsoredPlantsIdList)
        })
    }

    /**
     * Fetch a list of [Plant]s from the database that matches a given [GrowZone].
     * Returns custom-sorted list of plants wrapped in LiveData.
     */
    fun getPlantsWithGrowZone(growZone: GrowZone) =
        //Fetch plants with certain growZone from database.
        plantDao.getPlantsWithGrowZoneNumber(growZone.number)
            .switchMap { plantList ->
                liveData {
                    //Request sponsored plants ids. With getOrAwait() the result will be cached after 1st call.
                    val sponsoredPlantsIdList = sponsoredPlantsIdCached.getOrAwait()
                    emit(plantList.applyMainSafeSort(sponsoredPlantsIdList))
                }
            }


    // Rearrange the list of plants, placing sponsored plants on the top of the list.
    private fun List<Plant>.applySort(sponsoredPlantsId: List<String>): List<Plant> {
        //sortedBy() returns a plants list sorted according to a ComparablePair.
        return sortedBy { plant ->
            val plantIndex = sponsoredPlantsId.indexOf(plant.plantId).let { index ->
                if (index > -1) index else Int.MAX_VALUE
            }
            ComparablePair(plantIndex, plant.name)
            // ComparabePair will check indices first and sort from the greatest value.
            // If the values are the same, then the names are compared and sorted alphabetically.
            // All sponsored plants are given index of value max_value, and all the other plants
            // are given index of value -1.
        }
    }

    @AnyThread
    suspend fun List<Plant>.applyMainSafeSort(sponsoredPlantsId: List<String>) =
        withContext(defaultDispatcher) {
            this@applyMainSafeSort.applySort(sponsoredPlantsId)
        }

    /**
     * In-memory cache for the custom sort order. It will fallback to an empty list if there's a network error.
     *
     * Allows listing certain items first, and then the rest in alphabetical order.
     * Gives the ability to change the sort order dynamically (just replace JSON).
     */
    private var sponsoredPlantsIdCached = CacheOnSuccess(onErrorFallback = { listOf<String>() }) {
        plantService.sponsoredPlantsId()
    }


    /**
     * Returns true if we should make a network request.
     */
    private fun shouldUpdatePlantsCache(): Boolean {
        // suspending function, so you can e.g. check the status of the database here
        return true
    }

    /**
     * Update the plants cache.
     *
     * This function may decide to avoid making a network requests on every call based on a
     * cache-invalidation policy.
     */
    suspend fun tryUpdateRecentPlantsCache() {
        if (shouldUpdatePlantsCache()) fetchRecentPlants()
    }

    //Fetch a new list of plants from the network, and append them to [plantDao]
    private suspend fun fetchRecentPlants() {
        val plants = plantService.allPlants()
        plantDao.insertAll(plants)
    }

    /**
     * Update the plants cache for a specific grow zone.
     *
     * This function may decide to avoid making a network requests on every call based on a
     * cache-invalidation policy.
     */
    suspend fun tryUpdateRecentPlantsForGrowZoneCache(growZoneNumber: GrowZone) {
        if (shouldUpdatePlantsCache()) fetchPlantsForGrowZone(growZoneNumber)
    }


    //Fetch a list of plants for a grow zone from the network, and append them to [plantDao]
    private suspend fun fetchPlantsForGrowZone(growZone: GrowZone) {
        val plants = plantService.plantsByGrowZone(growZone)
        plantDao.insertAll(plants)
    }

    companion object {
        // For Singleton instantiation
        @Volatile
        private var instance: PlantRepository? = null

        fun getInstance(plantDao: PlantDao, plantService: NetworkService) =
            instance ?: synchronized(this) {
                instance ?: PlantRepository(plantDao, plantService).also { instance = it }
            }
    }
}
