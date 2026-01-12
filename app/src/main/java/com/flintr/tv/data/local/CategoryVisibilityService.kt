package com.flintr.tv.data.local

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.flintr.tv.data.model.CategoryType
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CategoryVisibilityService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val moshi: Moshi
) {
    private val sharedPreferences: SharedPreferences = context.getSharedPreferences(
        "category_visibility_prefs",
        Context.MODE_PRIVATE
    )

    companion object {
        private const val KEY_HIDDEN_LIVE_CATEGORIES = "iptv_hidden_live_categories"
        private const val KEY_HIDDEN_VOD_CATEGORIES = "iptv_hidden_vod_categories"
        private const val KEY_HIDDEN_SERIES_CATEGORIES = "iptv_hidden_series_categories"
        private const val TAG = "CategoryVisibilityService"
    }

    private val setType = Types.newParameterizedType(
        Set::class.java,
        String::class.java
    )
    private val setAdapter = moshi.adapter<Set<String>>(setType)

    /**
     * Get the SharedPreferences key for the given category type
     */
    private fun getKeyForType(categoryType: CategoryType): String {
        return when (categoryType) {
            CategoryType.LIVE_TV -> KEY_HIDDEN_LIVE_CATEGORIES
            CategoryType.VOD -> KEY_HIDDEN_VOD_CATEGORIES
            CategoryType.SERIES -> KEY_HIDDEN_SERIES_CATEGORIES
        }
    }

    /**
     * Load hidden categories for the given type from SharedPreferences
     */
    private fun loadHiddenCategories(categoryType: CategoryType): Set<String> {
        return try {
            val key = getKeyForType(categoryType)
            val json = sharedPreferences.getString(key, null)
            if (json.isNullOrBlank()) {
                emptySet()
            } else {
                setAdapter.fromJson(json) ?: emptySet()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing hidden categories JSON for type: $categoryType, returning empty set", e)
            // Clear corrupted data
            val key = getKeyForType(categoryType)
            sharedPreferences.edit().remove(key).apply()
            emptySet()
        }
    }

    /**
     * Save hidden categories for the given type to SharedPreferences
     */
    private fun saveHiddenCategories(categoryType: CategoryType, hiddenCategories: Set<String>) {
        try {
            val key = getKeyForType(categoryType)
            val json = setAdapter.toJson(hiddenCategories)
            sharedPreferences.edit().putString(key, json).apply()
            Log.d(TAG, "Saved hidden categories for type: $categoryType, count: ${hiddenCategories.size}")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving hidden categories for type: $categoryType", e)
        }
    }

    /**
     * Hide a category by adding its ID to the hidden set
     * @return true if category was hidden, false if it was already hidden
     */
    fun hideCategory(categoryId: String, categoryType: CategoryType): Boolean {
        val hiddenCategories = loadHiddenCategories(categoryType).toMutableSet()
        val wasAdded = hiddenCategories.add(categoryId)
        if (wasAdded) {
            saveHiddenCategories(categoryType, hiddenCategories)
            Log.d(TAG, "Category hidden: categoryId=$categoryId, type=$categoryType")
        }
        return wasAdded
    }

    /**
     * Show a category by removing its ID from the hidden set
     * @return true if category was shown, false if it was not hidden
     */
    fun showCategory(categoryId: String, categoryType: CategoryType): Boolean {
        val hiddenCategories = loadHiddenCategories(categoryType).toMutableSet()
        val wasRemoved = hiddenCategories.remove(categoryId)
        if (wasRemoved) {
            saveHiddenCategories(categoryType, hiddenCategories)
            Log.d(TAG, "Category shown: categoryId=$categoryId, type=$categoryType")
        }
        return wasRemoved
    }

    /**
     * Check if a category is hidden
     */
    fun isCategoryHidden(categoryId: String, categoryType: CategoryType): Boolean {
        val hiddenCategories = loadHiddenCategories(categoryType)
        return hiddenCategories.contains(categoryId)
    }

    /**
     * Get all hidden category IDs for the given type
     */
    fun getHiddenCategories(categoryType: CategoryType): Set<String> {
        return loadHiddenCategories(categoryType)
    }

    /**
     * Get all hidden categories for all types
     */
    fun getAllHiddenCategories(): Map<CategoryType, Set<String>> {
        return mapOf(
            CategoryType.LIVE_TV to loadHiddenCategories(CategoryType.LIVE_TV),
            CategoryType.VOD to loadHiddenCategories(CategoryType.VOD),
            CategoryType.SERIES to loadHiddenCategories(CategoryType.SERIES)
        )
    }
}
