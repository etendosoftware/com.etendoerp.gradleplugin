package com.etendoerp.translations

import org.gradle.api.Project

class TranslationsLoader {
    static void load(Project project) {
        // Register the export translations task only if it doesn't exist
        if (!project.tasks.findByName('export.translations')) {
            project.tasks.register('export.translations', ExportTranslationsTask) {
                // Properties can be overridden via -Planguage, -Pclient, -PreducedVersion
            }
        }
    }
}
