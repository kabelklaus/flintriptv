package com.flintr.tv.viewmodel

import androidx.lifecycle.ViewModel
import com.flintr.tv.data.tmdb.TmdbMetaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** DI-Brücke fürs Compose-UI; der Repository-State bleibt allein im Repository. */
@HiltViewModel
class TmdbMetaViewModel @Inject constructor(
    val repository: TmdbMetaRepository
) : ViewModel()
