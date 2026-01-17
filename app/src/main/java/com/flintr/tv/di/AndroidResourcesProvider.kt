package com.flintr.tv.di

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Android implementation of ResourcesProvider.
 * Provides access to Android string resources via Context.
 */
@Singleton
class AndroidResourcesProvider @Inject constructor(
    @ApplicationContext private val context: Context
) : ResourcesProvider {
    override fun getString(resId: Int, vararg formatArgs: Any): String {
        return if (formatArgs.isEmpty()) {
            context.getString(resId)
        } else {
            context.getString(resId, *formatArgs)
        }
    }
}
