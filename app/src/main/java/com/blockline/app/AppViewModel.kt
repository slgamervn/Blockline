package com.blockline.app

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.blockline.app.data.Project
import com.blockline.app.data.ProjectRepository
import com.blockline.app.ui.theme.ThemeMode

class AppViewModel(app: Application) : AndroidViewModel(app) {
    val repo = ProjectRepository(app)
    var projects by mutableStateOf<List<Project>>(emptyList())
    var current by mutableStateOf<Project?>(null)
    var message by mutableStateOf<String?>(null)
    var themeMode by mutableStateOf(ThemeMode.DARK)
    var fontSize by mutableStateOf(14)
    val tabs = mutableListOf<Any>()
    var activeTab by mutableStateOf(0)

    fun selectProject(p: Project) { current = p }
    fun saveTab(tab: Any) {}
    fun flushTabs() {}
}
