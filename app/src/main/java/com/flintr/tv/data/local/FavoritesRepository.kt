package com.flintr.tv.data.local

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.flintr.tv.data.model.Favorite
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import dagger.hilt.android.qualifiers.ApplicationContext
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

    fun getAllFavorites(): List<Favorite> {
        val json = sharedPreferences.getString(KEY_FAVORITES, null) ?: return emptyList()
        return try {
            listAdapter.fromJson(json) ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing favorites JSON", e)
            emptyList()
        }
    }

    fun addFavorite(favorite: Favorite): Boolean {
        val favorites = getAllFavorites().toMutableList()
        
        // Check if favorite already exists
        if (favorites.any { it.id == favorite.id }) {
            Log.d(TAG, "Favorite with id ${favorite.id} already exists")
            return false
        }
        
        favorites.add(favorite)
        return saveFavorites(favorites)
    }

    fun removeFavorite(favoriteId: String): Boolean {
        val favorites = getAllFavorites().toMutableList()
        val removed = favorites.removeAll { it.id == favoriteId }
        
        if (removed) {
            return saveFavorites(favorites)
        }
        return false
    }

    fun isFavorite(favoriteId: String): Boolean {
        return getAllFavorites().any { it.id == favoriteId }
    }

    fun clearAllFavorites() {
        sharedPreferences.edit().remove(KEY_FAVORITES).apply()
    }

    private fun saveFavorites(favorites: List<Favorite>): Boolean {
        return try {
            val json = listAdapter.toJson(favorites)
            sharedPreferences.edit().putString(KEY_FAVORITES, json).apply()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error saving favorites", e)
            false
        }
    }
}
