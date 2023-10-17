package com.etendoerp.copilot.tools


import org.gradle.api.Project

class CopilotToolsLoader {
    static void load(Project project) {
        XMLTranslationTool.load(project)
    }
}
