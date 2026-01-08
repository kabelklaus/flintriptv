package com.flintr.tv.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flintr.tv.data.model.EpgProgram
import com.flintr.tv.data.repository.XtreamRepository
import com.flintr.tv.di.XtreamConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val repository: XtreamRepository
) : ViewModel() {
    
    private val _epgPrograms = MutableStateFlow<List<EpgProgram>>(emptyList())
    val epgPrograms: StateFlow<List<EpgProgram>> = _epgPrograms.asStateFlow()
    
    private val _currentStreamUrl = MutableStateFlow<String?>(null)
    val currentStreamUrl: StateFlow<String?> = _currentStreamUrl.asStateFlow()
    
    fun setStreamUrl(url: String) {
        _currentStreamUrl.value = url
    }
    
    fun loadEpg(streamId: Int) {
        viewModelScope.launch {
            repository.getEpg(XtreamConfig.USERNAME, XtreamConfig.PASSWORD, streamId)
                .onSuccess { _epgPrograms.value = it }
                .onFailure { }
        }
    }
    
    fun clearEpg() {
        _epgPrograms.value = emptyList()
    }
}

