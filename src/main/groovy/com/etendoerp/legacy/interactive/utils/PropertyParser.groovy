package com.etendoerp.legacy.interactive.utils

import com.etendoerp.legacy.interactive.model.PropertyDefinition
import org.gradle.api.Project

/**
 * Parser utility for processing property documentation files.
 * Handles parsing of setup.properties.docs files and gradle.properties 
 * to extract property metadata including documentation, groups, and default values.
 * 
 * @author Etendo Interactive Setup Team
 * @since 2.0.4
 */
class PropertyParser {
    
    private static final String DOC_FILE_NAME = "setup.properties.docs"
    private static final String GROUP_MARKER = "# group:"
    private static final String SENSITIVE_MARKER = "# sensitive:"
    private static final String REQUIRED_MARKER = "# required:"
    private static final String COMMENT_PREFIX = "#"
    
    private final Project project
    
    PropertyParser(Project project) {
        this.project = project
    }
    
    /**
     * Parses all properties from gradle.properties file.
     * Returns PropertyDefinition objects with current values but no documentation.
     * 
     * @return List of PropertyDefinition objects from gradle.properties
     */
    List<PropertyDefinition> parseGradleProperties() {
        def gradlePropsFile = project.file('gradle.properties')
        if (!gradlePropsFile.exists()) {
            project.logger.info("No gradle.properties file found")
            return []
        }
        
        def properties = []
        def props = new Properties()
        
        try {
            gradlePropsFile.withInputStream { props.load(it) }
            
            props.each { key, value ->
                // Skip gradle-specific and helper properties
                if (shouldSkipProperty(key.toString())) {
                    return
                }
                
                def propertyDef = new PropertyDefinition()
                propertyDef.key = key.toString()
                propertyDef.currentValue = value.toString()
                propertyDef.group = "General" // Default group for gradle.properties
                propertyDef.sensitive = SecurityUtils.isSensitive(propertyDef.key)
                
                properties.add(propertyDef)
            }
        } catch (Exception e) {
            project.logger.warn("Error reading gradle.properties: ${e.message}")
        }
        
        return properties
    }
    
    // Legacy methods for setup.properties.docs removed in ETP-1960-06
    // Migration to config.gradle format completed - see ConfigSlurperPropertyScanner
    
    // Legacy parsing methods removed in ETP-1960-06 - migrated to ConfigSlurperPropertyScanner
    // processCommentLine, parsePropertyLine and related methods removed
    
    /**
     * Determines if a property should be skipped during parsing.
     * Skips gradle-specific and internal properties.
     */
    private boolean shouldSkipProperty(String key) {
        if (!key) return true
        
        return key.startsWith("org.gradle") || 
               key == "bbdd.port" ||
               key in ["nexusUser", "nexusPassword", "githubUser", "githubToken"] &&
               // Only skip these if they're empty or contain placeholder values
               (project.findProperty(key)?.toString()?.trim()?.isEmpty() || 
                project.findProperty(key)?.toString()?.contains('${'))
    }
    
    /**
     * Merges properties from different sources, handling duplicates.
     * Priority: existing current values > documentation defaults > new defaults
     * 
     * @param gradleProperties Properties from gradle.properties
     * @param docProperties Properties from documentation files
     * @return Unified list of PropertyDefinition objects
     */
    static List<PropertyDefinition> mergeProperties(List<PropertyDefinition> gradleProperties, 
                                                   List<PropertyDefinition> docProperties) {
        def mergedMap = [:]
        
        // First, add all documentation properties
        docProperties.each { docProp ->
            mergedMap[docProp.key] = docProp
        }
        
        // Then, merge in gradle properties, preserving current values and updating metadata
        gradleProperties.each { gradleProp ->
            if (mergedMap.containsKey(gradleProp.key)) {
                // Merge with existing documentation
                def existing = mergedMap[gradleProp.key]
                existing.currentValue = gradleProp.currentValue
                // Keep documentation and group from docs, but update sensitivity check
                existing.sensitive = existing.sensitive || SecurityUtils.isSensitive(gradleProp.key)
            } else {
                // Add new property from gradle.properties
                mergedMap[gradleProp.key] = gradleProp
            }
        }
        
        // Sort by group, then by key
        return mergedMap.values().sort { a, b ->
            def groupCompare = (a.group ?: "").compareTo(b.group ?: "")
            if (groupCompare != 0) return groupCompare
            return (a.key ?: "").compareTo(b.key ?: "")
        }
    }
}
