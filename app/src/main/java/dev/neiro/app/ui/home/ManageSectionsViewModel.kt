package dev.neiro.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.neiro.app.data.prefs.NieroPreferences
import dev.neiro.app.data.repository.LastFmRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class ManageSectionsViewModel @Inject constructor(
    private val preferences: NieroPreferences,
    private val lastFmRepository: LastFmRepository
) : ViewModel() {

    private val _sections = MutableStateFlow(DEFAULT_HOME_SECTIONS)
    val sections: StateFlow<List<HomeSectionConfig>> = _sections.asStateFlow()

    private val _hasLastFm = MutableStateFlow(false)
    val hasLastFm: StateFlow<Boolean> = _hasLastFm.asStateFlow()

    init {
        viewModelScope.launch {
            preferences.homeSectionsJson.collect { json ->
                _sections.value = json?.toHomeSectionConfigs() ?: DEFAULT_HOME_SECTIONS
            }
        }
        viewModelScope.launch {
            _hasLastFm.value = lastFmRepository.isStatsConfigured()
        }
    }

    fun update(id: String, transform: (HomeSectionConfig) -> HomeSectionConfig) =
        save(_sections.value.map { if (it.id == id) transform(it) else it })

    fun addSection() {
        val new = HomeSectionConfig(
            id = UUID.randomUUID().toString(),
            title = "New Section",
            contentType = SectionContentType.ALBUMS,
            sortType = AlbumSortType.RECENTLY_ADDED,
            layout = SectionLayout.SHELF,
            size = 20,
            enabled = true
        )
        save(_sections.value + new)
    }

    fun deleteSection(id: String) =
        save(_sections.value.filter { it.id != id })

    fun moveUp(index: Int) {
        if (index <= 0) return
        val list = _sections.value.toMutableList()
        val tmp = list[index]; list[index] = list[index - 1]; list[index - 1] = tmp
        save(list)
    }

    fun moveDown(index: Int) {
        if (index >= _sections.value.size - 1) return
        val list = _sections.value.toMutableList()
        val tmp = list[index]; list[index] = list[index + 1]; list[index + 1] = tmp
        save(list)
    }

    private fun save(sections: List<HomeSectionConfig>) {
        _sections.value = sections
        viewModelScope.launch { preferences.saveHomeSections(sections.toJson()) }
    }
}
