package com.etendoerp.automation

import com.etendoerp.legacy.interactive.ConfigSlurperPropertyScanner
import com.etendoerp.legacy.interactive.ConfigWriter
import com.etendoerp.legacy.interactive.model.PropertyDefinition
import org.gradle.api.Project

/**
 * Service to manage Etendo configuration properties
 * Reads from config.gradle files and writes to gradle.properties
 *
 * This service integrates with the InteractiveSetupManager infrastructure
 * to provide REST API access to configuration management.
 */
class ConfigurationService {

    private final Project project
    private final ConfigSlurperPropertyScanner scanner
    private final ConfigWriter writer
    private final File propertiesFile

    ConfigurationService(Project project) {
        this.project = project
        this.scanner = new ConfigSlurperPropertyScanner(project)
        this.writer = new ConfigWriter(project)
        this.propertiesFile = new File(project.rootDir, "gradle.properties")
    }

    /**
     * Reads all configurations from config.gradle files with their current values from gradle.properties
     * @return Map with all configuration properties organized by module
     */
    Map<String, Object> readAllConfigurations() {
        try {
            // Scan all property definitions from config.gradle files
            List<PropertyDefinition> properties = scanner.scanAllProperties()

            // Convert to API-friendly format
            def result = [
                total: properties.size(),
                properties: properties.collect { prop ->
                    convertPropertyToMap(prop)
                }
            ]

            project.logger.debug("Read ${properties.size()} configuration properties")
            return result

        } catch (Exception e) {
            project.logger.error("Error reading configurations: ${e.message}", e)
            throw new RuntimeException("Failed to read configurations", e)
        }
    }

    /**
     * Reads configurations organized by groups/categories
     * @return Map with configurations grouped by category
     */
    Map<String, Object> readCategorizedConfigurations() {
        try {
            List<PropertyDefinition> properties = scanner.scanAllProperties()

            // Group properties by their group/category
            def grouped = [:].withDefault { [] }

            properties.each { prop ->
                def groups = prop.groups ?: ["General"]
                groups.each { group ->
                    grouped[group] << convertPropertyToMap(prop)
                }
            }

            def result = [
                total: properties.size(),
                groups: grouped.collectEntries { group, props ->
                    [
                        group,
                        [
                            count: props.size(),
                            properties: props
                        ]
                    ]
                }
            ]

            project.logger.debug("Read configurations grouped into ${grouped.size()} categories")
            return result

        } catch (Exception e) {
            project.logger.error("Error reading categorized configurations: ${e.message}", e)
            throw new RuntimeException("Failed to read categorized configurations", e)
        }
    }

    /**
     * Reads configurations organized by module source
     * @return Map with configurations grouped by module
     */
    Map<String, Object> readConfigurationsByModule() {
        try {
            List<PropertyDefinition> properties = scanner.scanAllProperties()

            // Group properties by their source (module)
            def byModule = properties.groupBy { prop ->
                prop.source ?: "main"
            }

            def result = [
                total: properties.size(),
                modules: byModule.collectEntries { moduleName, props ->
                    [
                        moduleName,
                        [
                            count: props.size(),
                            properties: props.collect { convertPropertyToMap(it) }
                        ]
                    ]
                }
            ]

            project.logger.debug("Read configurations from ${byModule.size()} modules")
            return result

        } catch (Exception e) {
            project.logger.error("Error reading configurations by module: ${e.message}", e)
            throw new RuntimeException("Failed to read configurations by module", e)
        }
    }

    /**
     * Saves configurations to gradle.properties
     * @param configurations Map with configuration key-value pairs
     * @return Map with result status
     */
    Map<String, Object> saveConfigurations(Map<String, String> configurations) {
        try {
            if (!propertiesFile.exists()) {
                throw new FileNotFoundException("gradle.properties file not found at: ${propertiesFile.absolutePath}")
            }

            // Get all property definitions for validation
            List<PropertyDefinition> allProperties = scanner.scanAllProperties()

            // Validate configurations against definitions
            def validationErrors = validateConfigurations(configurations, allProperties)
            if (!validationErrors.isEmpty()) {
                return [
                    success: false,
                    message: "Validation errors found",
                    validationErrors: validationErrors
                ]
            }

            // Filter out properties that equal their default values
            def filtered = filterOutDefaults(configurations, allProperties)

            // Write to gradle.properties using ConfigWriter
            writer.writeProperties(filtered)

            project.logger.info("Successfully updated ${filtered.size()} properties in gradle.properties")

            return [
                success: true,
                message: "Successfully updated ${filtered.size()} properties",
                updatedKeys: filtered.keySet().toList(),
                totalReceived: configurations.size(),
                filtered: configurations.size() - filtered.size()
            ]

        } catch (Exception e) {
            project.logger.error("Error saving configurations: ${e.message}", e)
            return [
                success: false,
                message: "Error saving configurations: ${e.message}",
                error: e.class.simpleName
            ]
        }
    }

    /**
     * Converts a PropertyDefinition to a Map for API response
     */
    private Map<String, Object> convertPropertyToMap(PropertyDefinition prop) {
        return [
            key: prop.key,
            currentValue: prop.currentValue,
            defaultValue: prop.defaultValue,
            description: prop.documentation ?: "",
            help: prop.help ?: "",
            group: prop.groups?.join(", ") ?: "General",
            groups: prop.groups ?: ["General"],
            required: prop.required ?: false,
            sensitive: prop.sensitive ?: false,
            source: prop.source,
            hasValue: prop.hasValue(),
            order: prop.order ?: 0,
            notSetWhenDefault: prop.notSetWhenDefault ?: false,
            process: prop.process ?: false
        ]
    }

    /**
     * Validates configuration values against property definitions
     */
    private Map<String, String> validateConfigurations(Map<String, String> configurations,
                                                       List<PropertyDefinition> allProperties) {
        Map<String, String> errors = [:]
        def propertiesMap = allProperties.collectEntries { [(it.key): it] }

        configurations.each { key, value ->
            def propDef = propertiesMap[key]

            if (!propDef) {
                // Unknown property - warn but allow
                project.logger.warn("Configuration for unknown property: ${key}")
                return
            }

            // Validate required fields
            if (propDef.required && (!value || value.trim().isEmpty())) {
                errors[key] = "This field is required and cannot be empty"
            }

            // Basic validation for common property patterns
            // Port validation
            if (key.contains("port") || key.contains("Port")) {
                try {
                    def port = Integer.parseInt(value)
                    if (port < 1 || port > 65535) {
                        errors[key] = "Port must be between 1 and 65535"
                    }
                } catch (NumberFormatException e) {
                    errors[key] = "Port must be a valid number"
                }
            }

            // Path validation
            if (key.contains("path") || key.contains("Path") || key.contains("dir") || key.contains("Dir")) {
                if (value.contains("..")) {
                    errors[key] = "Path cannot contain '..' for security reasons"
                }
            }

            // Email validation
            if (key.contains("email") || key.contains("Email")) {
                if (!value.matches(/^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$/)) {
                    errors[key] = "Must be a valid email address"
                }
            }
        }

        return errors
    }

    /**
     * Filters out properties whose value equals their default value
     */
    private Map<String, String> filterOutDefaults(Map<String, String> properties,
                                                   List<PropertyDefinition> definitions) {
        if (!properties || properties.isEmpty()) return [:]
        if (!definitions) return properties

        def defsMap = definitions.collectEntries { [(it.key): it] }
        def result = [:]

        properties.each { k, v ->
            def propertyDef = defsMap[k]

            if (propertyDef == null) {
                // No known definition - keep the property
                result[k] = v
            } else {
                def defaultVal = propertyDef.defaultValue ?: ""

                // Check if we should filter based on notSetWhenDefault flag
                if (propertyDef.notSetWhenDefault && v == defaultVal) {
                    // Property should NOT be set when value equals default
                    project.logger.debug("Skipping write for ${k} because notSetWhenDefault=true and value equals default (${v})")
                } else if (!propertyDef.notSetWhenDefault) {
                    // Always set the property regardless of whether it equals default
                    result[k] = v
                } else {
                    // notSetWhenDefault=true but value is different from default - set it
                    result[k] = v
                }
            }
        }

        return result
    }

    /**
     * Gets configuration statistics
     */
    Map<String, Object> getConfigurationStats() {
        try {
            List<PropertyDefinition> properties = scanner.scanAllProperties()

            def stats = [
                total: properties.size(),
                configured: properties.count { it.hasValue() },
                required: properties.count { it.required },
                sensitive: properties.count { it.sensitive },
                processProperties: properties.count { it.process },
                byGroup: [:],
                byModule: [:]
            ]

            // Group statistics
            properties.each { prop ->
                def groups = prop.groups ?: ["General"]
                groups.each { group ->
                    stats.byGroup[group] = (stats.byGroup[group] ?: 0) + 1
                }

                // Module statistics
                def module = prop.source ?: "main"
                stats.byModule[module] = (stats.byModule[module] ?: 0) + 1
            }

            return stats

        } catch (Exception e) {
            project.logger.error("Error getting configuration stats: ${e.message}", e)
            throw new RuntimeException("Failed to get configuration stats", e)
        }
    }
}