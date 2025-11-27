package com.etendoerp.legacy.interactive

import groovy.json.JsonBuilder
import org.gradle.api.Project

class InteractiveSetupWriter {

    static boolean writeResults(Project project, Map<String, String> results, String outputPath = null) {
        try {
            String finalOutputPath = outputPath ?: project.findProperty('output')?.toString()

            if (!finalOutputPath) {
                project.logger.warn("No output path specified for interactive setup results")
                return false
            }

            def outputFile = new File(finalOutputPath)
            outputFile.parentFile?.mkdirs()

            def json = new JsonBuilder(results)
            outputFile.text = json.toPrettyString()

            project.logger.lifecycle("Interactive setup results written to: ${finalOutputPath}")
            project.logger.debug("Results: ${results}")

            return true
        } catch (Exception e) {
            project.logger.error("Failed to write interactive setup results: ${e.message}", e)
            return false
        }
    }
}
