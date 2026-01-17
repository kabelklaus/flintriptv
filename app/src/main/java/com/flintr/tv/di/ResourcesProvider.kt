package com.flintr.tv.di

/**
 * Interface for providing string resources.
 * Allows ViewModels to access string resources without direct Context dependency.
 */
fun interface ResourcesProvider {
    /**
     * Gets a string resource with optional format arguments.
     *
     * @param resId The resource identifier
     * @param formatArgs Optional format arguments for string formatting
     * @return The formatted string
     */
    fun getString(resId: Int, vararg formatArgs: Any): String
}
