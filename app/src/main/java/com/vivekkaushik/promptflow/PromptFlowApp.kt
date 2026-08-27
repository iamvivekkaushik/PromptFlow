package com.vivekkaushik.promptflow

import android.app.Application
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.vivekkaushik.promptflow.core.data.Script
import com.vivekkaushik.promptflow.core.prompter.PrompterEngine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class PromptFlowApp : Application() {
    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(applicationContext)
        Graph.init(this)
        seedDemoScript()
    }

    /** First-run seed so the library isn't empty. */
    private fun seedDemoScript() {
        Graph.appScope.launch {
            val dao = Graph.db.scripts()
            if (dao.observeAll().first().isEmpty()) {
                val now = System.currentTimeMillis()
                dao.upsert(
                    Script(
                        title = "Q4 Launch Video",
                        body = PrompterEngine.DEMO_SCRIPT,
                        source = "On device",
                        createdAt = now - 2 * 60 * 60 * 1000,
                        updatedAt = now - 2 * 60 * 60 * 1000,
                    )
                )
            }
        }
    }
}
