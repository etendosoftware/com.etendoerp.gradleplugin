package com.etendoerp.translations

import org.gradle.api.Project

class TranslateAILoader {
    static void load(Project project) {
        // Register the translate AI task only if it doesn't exist
        if (!project.tasks.findByName('translate.ai')) {
            project.tasks.register('translate.ai', TranslateAITask) {
                description = 'Auto-translate using OpenAI for the specified language(s) and module(s)'
            }
        }
    }
}
