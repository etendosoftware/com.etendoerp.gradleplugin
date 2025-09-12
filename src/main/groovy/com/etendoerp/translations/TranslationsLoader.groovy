package com.etendoerp.translations

import org.gradle.api.Project

class TranslationsLoader {
    static void load(Project project) {
        // Register the export translations task
        project.tasks.register('export.translations', ExportTranslationsTask) {
            // Properties can be overridden via -Planguage, -Pclient, -PreducedVersion
        }
    }
}
