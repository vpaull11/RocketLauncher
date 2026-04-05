package com.rocketlauncher.presentation.search

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rocketlauncher.data.repository.GlobalMessageHit
import com.rocketlauncher.data.repository.SearchRepository
import com.rocketlauncher.R
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class GlobalSearchUiState(
    val query: String = "",
    val results: List<GlobalMessageHit> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class GlobalSearchViewModel @Inject constructor(
    private val searchRepository: SearchRepository,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(GlobalSearchUiState())
    val uiState: StateFlow<GlobalSearchUiState> = _uiState.asStateFlow()

    fun onQueryChange(q: String) {
        _uiState.update { it.copy(query = q) }
    }

    fun search() {
        val q = _uiState.value.query.trim()
        if (q.length < 2) {
            _uiState.update {
                it.copy(
                    error = appContext.getString(R.string.global_search_min_chars),
                    results = emptyList()
                )
            }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val list = withContext(Dispatchers.IO) {
                    searchRepository.searchMessagesGlobally(q)
                }
                _uiState.update { it.copy(isLoading = false, results = list) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }
}
