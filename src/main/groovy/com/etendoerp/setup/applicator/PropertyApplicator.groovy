package com.etendoerp.setup.applicator

import org.gradle.api.Project

/**
 * Applies properties to gradle.properties file
 */
class PropertyApplicator {

    /** Keywords that indicate a property value should be masked in console output. */
    private static final List<String> SENSITIVE_KEYWORDS = ['KEY', 'TOKEN', 'PASSWORD', 'SECRET']

    /**
     * Mask a value for display, showing only the first 4 and last 4 characters.
     * Values shorter than 9 characters are fully masked.
     */
    static String maskValue(String value) {
        if (value == null || value.length() <= 8) {
            return '****'
        }
        return value.substring(0, 4) + '****' + value.substring(value.length() - 4)
    }

    /**
     * Return the display value for a property, masking it if the key contains a sensitive keyword.
     */
    static String displayValue(String key, String value) {
        String upperKey = key.toUpperCase()
        boolean sensitive = SENSITIVE_KEYWORDS.any { upperKey.contains(it) }
        return sensitive ? maskValue(value) : value
    }

    /**
     * Apply properties to gradle.properties, preserving section comments from the template.
     * @param project The Gradle project
     * @param properties Map of properties to apply
     * @param propertyOrder Ordered list of keys and comment lines from the template (optional)
     */
    static void apply(Project project, Map<String, String> properties, List<String> propertyOrder = []) {
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

        // Track which properties have been processed (for ordered writing)
        Set<String> processed = [] as LinkedHashSet

        // If we have propertyOrder, use it to write in template order with comments
        if (propertyOrder) {
            propertyOrder.each { entry ->
                if (entry.startsWith('#')) {
                    // Section comment — add blank line + comment as separator
                    if (existingKeys.isEmpty() || !lines.isEmpty()) {
                        lines.add('')
                    }
                    lines.add(entry)
                } else {
                    // Property key
                    String key = entry
                    if (properties.containsKey(key)) {
                        String value = properties[key]
                        String propertyLine = "${key}=${value}"

                        if (existingKeys.containsKey(key)) {
                            lines[existingKeys[key]] = propertyLine
                            println "  ${key}=${displayValue(key, value)} (updated)"
                        } else {
                            lines.add(propertyLine)
                            println "  ${key}=${displayValue(key, value)}"
                        }
                        processed.add(key)
                    }
                }
            }
        }

        // Add any remaining properties not in propertyOrder
        properties.each { key, value ->
            if (!processed.contains(key)) {
                String propertyLine = "${key}=${value}"

                if (existingKeys.containsKey(key)) {
                    lines[existingKeys[key]] = propertyLine
                    println "  ${key}=${displayValue(key, value)} (updated)"
                } else {
                    lines.add(propertyLine)
                    println "  ${key}=${displayValue(key, value)}"
                }
            }
        }

        // Write back to file
        propsFile.text = lines.join('\n') + '\n'
    }
}
