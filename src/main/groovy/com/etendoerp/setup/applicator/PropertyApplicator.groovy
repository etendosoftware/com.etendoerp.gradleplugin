package com.etendoerp.setup.applicator

import org.gradle.api.Project

/**
 * Applies properties to gradle.properties file
 */
class PropertyApplicator {

    /**
     * Apply properties to gradle.properties
     * @param project The Gradle project
     * @param properties Map of properties to apply
     */
    static void apply(Project project, Map<String, String> properties) {
        if (!properties) {
            return
        }

        File propsFile = new File(project.rootDir, 'gradle.properties')
        
        if (!propsFile.exists()) {
            project.logger.warn("gradle.properties not found in project root. Creating new file.")
            propsFile.createNewFile()
        }

        // Read existing properties while preserving comments
        List<String> lines = propsFile.exists() ? propsFile.readLines() : []
        Map<String, Integer> existingKeys = [:]
        
        // Find existing keys and their line numbers
        lines.eachWithIndex { line, index ->
            String trimmed = line.trim()
            if (!trimmed.isEmpty() && !trimmed.startsWith('#')) {
                int equalsIndex = trimmed.indexOf('=')
                if (equalsIndex > 0) {
                    String key = trimmed.substring(0, equalsIndex).trim()
                    existingKeys[key] = index
                }
            }
        }

        // Update or add properties
        properties.each { key, value ->
            String propertyLine = "${key}=${value}"
            
            if (existingKeys.containsKey(key)) {
                // Update existing property
                lines[existingKeys[key]] = propertyLine
                println "*** ${key}=${value} (updated)"
            } else {
                // Add new property
                lines.add(propertyLine)
                println "*** ${key}=${value}"
            }
        }

        // Write back to file
        propsFile.text = lines.join('\n') + '\n'
    }
}
