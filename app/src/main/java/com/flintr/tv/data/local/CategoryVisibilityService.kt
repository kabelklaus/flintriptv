package com.flintr.tv.data.local

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.flintr.tv.data.model.CategoryType
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    // StateFlow for reactive state management
    private val _hiddenCategories = MutableStateFlow<Map<CategoryType, Set<String>>>(emptyMap())
    val hiddenCategories: StateFlow<Map<CategoryType, Set<String>>> = _hiddenCategories.asStateFlow()

    init {
        // Load initial state from SharedPreferences
        _hiddenCategories.value = CategoryType.values().associateWith { loadHiddenCategories(it) }
    }

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
        val key = getKeyForType(categoryType)
        return try {
            val json = sharedPreferences.getString(key, null)
            if (json.isNullOrBlank()) {
                emptySet()
            } else {
                setAdapter.fromJson(json) ?: emptySet()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing hidden categories JSON for type: $categoryType, returning empty set", e)
            // Clear corrupted data
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
        val currentState = _hiddenCategories.value.toMutableMap()
        val hiddenCategories = currentState.getOrDefault(categoryType, emptySet()).toMutableSet()
        val wasAdded = hiddenCategories.add(categoryId)
        if (wasAdded) {
            currentState[categoryType] = hiddenCategories
            _hiddenCategories.value = currentState
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
        val currentState = _hiddenCategories.value.toMutableMap()
        val hiddenCategories = currentState.getOrDefault(categoryType, emptySet()).toMutableSet()
        val wasRemoved = hiddenCategories.remove(categoryId)
        if (wasRemoved) {
            currentState[categoryType] = hiddenCategories
            _hiddenCategories.value = currentState
            saveHiddenCategories(categoryType, hiddenCategories)
            Log.d(TAG, "Category shown: categoryId=$categoryId, type=$categoryType")
        }
        return wasRemoved
    }

    /**
     * Check if a category is hidden
     */
    fun isCategoryHidden(categoryId: String, categoryType: CategoryType): Boolean {
        return _hiddenCategories.value[categoryType]?.contains(categoryId) ?: false
    }

    /**
     * Get all hidden category IDs for the given type
     */
    fun getHiddenCategories(categoryType: CategoryType): Set<String> {
        return _hiddenCategories.value[categoryType] ?: emptySet()
    }

    /**
     * Get all hidden categories for all types
     */
    fun getAllHiddenCategories(): Map<CategoryType, Set<String>> {
        return _hiddenCategories.value
    }
}
