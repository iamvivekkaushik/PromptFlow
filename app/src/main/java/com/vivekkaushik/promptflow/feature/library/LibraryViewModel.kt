package com.vivekkaushik.promptflow.feature.library

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vivekkaushik.promptflow.Graph
import com.vivekkaushik.promptflow.core.data.Script
import com.vivekkaushik.promptflow.core.importer.ScriptImporter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LibraryViewModel(app: Application) : AndroidViewModel(app) {

    private val query = MutableStateFlow("")
    val searchQuery: StateFlow<String> = query

    val scripts: StateFlow<List<Script>> =
        combine(Graph.db.scripts().observeAll(), query) { all, q ->
            if (q.isBlank()) all
            else all.filter { it.title.contains(q, ignoreCase = true) || it.body.contains(q, ignoreCase = true) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val settings = Graph.settings.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.vivekkaushik.promptflow.core.data.PrompterSettings())

    fun setQuery(q: String) { query.value = q }

    fun import(uri: Uri, onDone: (Long) -> Unit) {
        viewModelScope.launch {
            val imported = ScriptImporter.import(getApplication(), uri)
            val now = System.currentTimeMillis()
            val id = Graph.db.scripts().upsert(
                Script(title = imported.title, body = imported.body, source = "Imported", createdAt = now, updatedAt = now)
            )
            onDone(id)
        }
    }

    fun setRecorded(id: Long, recorded: Boolean) {
        viewModelScope.launch { Graph.db.scripts().setRecorded(id, recorded) }
    }

    fun newScript(onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val id = Graph.db.scripts().upsert(
                Script(title = "Untitled script", body = "", createdAt = now, updatedAt = now)
            )
            onCreated(id)
        }
    }
}
