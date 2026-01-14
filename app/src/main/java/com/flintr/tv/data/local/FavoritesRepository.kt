package com.flintr.tv.data.local

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.flintr.tv.data.model.Favorite
import com.flintr.tv.data.model.FavoriteType
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FavoritesRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val moshi: Moshi
) {
    private val sharedPreferences: SharedPreferences = context.getSharedPreferences(
        "favorites_prefs",
        Context.MODE_PRIVATE
    )

    companion object {
        private const val KEY_FAVORITES = "iptv_favorites"
        private const val TAG = "FavoritesRepository"
    }

    private val listType = Types.newParameterizedType(List::class.java, Favorite::class.java)
    private val listAdapter = moshi.adapter<List<Favorite>>(listType)

    suspend fun getAllFavorites(): List<Favorite> = withContext(Dispatchers.IO) {
        val json = sharedPreferences.getString(KEY_FAVORITES, null) ?: return@withContext emptyList()
        return@withContext try {
            listAdapter.fromJson(json) ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing favorites JSON", e)
            emptyList()
        }
    }

    suspend fun addFavorite(favorite: Favorite): Boolean = withContext(Dispatchers.IO) {
        val favorites = getAllFavorites().toMutableList()
        
        // Check if favorite already exists
        if (favorites.any { it.id == favorite.id }) {
            Log.d(TAG, "Favorite with id ${favorite.id} already exists")
            return@withContext false
        }
        
        favorites.add(favorite)
        return@withContext saveFavorites(favorites)
    }

    suspend fun removeFavorite(favoriteId: String): Boolean = withContext(Dispatchers.IO) {
        val favorites = getAllFavorites().toMutableList()
        val removed = favorites.removeAll { it.id == favoriteId }
        
        if (removed) {
            return@withContext saveFavorites(favorites)
        }
        return@withContext false
    }

    suspend fun isFavorite(favoriteId: String): Boolean = withContext(Dispatchers.IO) {
        return@withContext getAllFavorites().any { it.id == favoriteId }
    }

    suspend fun clearAllFavorites() = withContext(Dispatchers.IO) {
        sharedPreferences.edit().remove(KEY_FAVORITES).apply()
    }

    suspend fun getFavoritesByType(type: FavoriteType): List<Favorite> = withContext(Dispatchers.IO) {
        return@withContext getAllFavorites().filter { it.type == type }
    }

    suspend fun getFavoriteCountByType(type: FavoriteType): Int = withContext(Dispatchers.IO) {
        return@withContext getAllFavorites().count { it.type == type }
    }

    private suspend fun saveFavorites(favorites: List<Favorite>): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val json = listAdapter.toJson(favorites)
            sharedPreferences.edit().putString(KEY_FAVORITES, json).apply()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error saving favorites", e)
            false
        }
    }
}
