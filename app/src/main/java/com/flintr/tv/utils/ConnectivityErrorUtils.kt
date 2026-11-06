package com.flintr.tv.utils

import java.io.IOException

internal fun Throwable.isConnectivityError(): Boolean {
    return when (this) {
        is IOException -> true
        else -> cause?.isConnectivityError() == true
    }
}
