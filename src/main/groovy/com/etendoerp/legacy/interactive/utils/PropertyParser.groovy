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
    
    /**
     * Parses all setup.properties.docs files found in the modules directory.
     * Returns PropertyDefinition objects with documentation, groups, and default values.
     * 
     * @return List of PropertyDefinition objects from module documentation
     */
    List<PropertyDefinition> parseModuleDocumentation() {
        def properties = []
        def modulesDir = project.file('modules')
        
        if (!modulesDir.exists() || !modulesDir.isDirectory()) {
            project.logger.info("No modules directory found")
            return properties
        }
        
        modulesDir.listFiles()?.each { moduleDir ->
            if (moduleDir.isDirectory()) {
                def docFile = new File(moduleDir, DOC_FILE_NAME)
                if (docFile.exists()) {
                    try {
                        def moduleProperties = parseDocumentationFile(docFile)
                        properties.addAll(moduleProperties)
                    } catch (Exception e) {
                        project.logger.warn("Error parsing documentation in ${moduleDir.name}: ${e.message}")
                    }
                }
            }
        }
        
        return properties
    }
    
    /**
     * Parses a single documentation file and extracts property definitions.
     * 
     * @param docFile The documentation file to parse
     * @return List of PropertyDefinition objects from the file
     */
    List<PropertyDefinition> parseDocumentationFile(File docFile) {
        def properties = []
        def lines = docFile.readLines()
        
        String currentGroup = null
        List<String> currentComments = []
        boolean currentSensitive = false
        boolean currentRequired = false
        
        for (int i = 0; i < lines.size(); i++) {
            String line = lines[i].trim()
            
            if (line.isEmpty()) {
                // Empty line resets comment accumulation
                currentComments.clear()
                continue
            }
            
            if (line.startsWith(COMMENT_PREFIX)) {
                processCommentLine(line, currentComments, { group -> currentGroup = group },
                                 { sensitive -> currentSensitive = sensitive },
                                 { required -> currentRequired = required })
            } else if (line.contains('=')) {
                // Property line - create PropertyDefinition
                def propertyDef = parsePropertyLine(line, currentComments, currentGroup, 
                                                  currentSensitive, currentRequired)
                if (propertyDef) {
                    properties.add(propertyDef)
                }
                
                // Reset for next property
                currentComments.clear()
                currentSensitive = false
                currentRequired = false
            }
        }
        
        return properties
    }
    
    /**
     * Processes a comment line to extract group, sensitivity, and documentation.
     */
    private void processCommentLine(String line, List<String> comments, 
                                   Closure groupSetter, Closure sensitiveSetter, 
                                   Closure requiredSetter) {
        if (line.toLowerCase().startsWith(GROUP_MARKER)) {
            def group = line.substring(GROUP_MARKER.length()).trim()
            groupSetter(group)
        } else if (line.toLowerCase().startsWith(SENSITIVE_MARKER)) {
            def value = line.substring(SENSITIVE_MARKER.length()).trim().toLowerCase()
            sensitiveSetter(value == "true" || value == "yes")
        } else if (line.toLowerCase().startsWith(REQUIRED_MARKER)) {
            def value = line.substring(REQUIRED_MARKER.length()).trim().toLowerCase()
            requiredSetter(value == "true" || value == "yes")
        } else {
            // Regular comment - add to documentation
            String comment = line.substring(1).trim() // Remove # prefix
            if (!comment.isEmpty()) {
                comments.add(comment)
            }
        }
    }
    
    /**
     * Parses a property line (key=value) and creates a PropertyDefinition.
     */
    private PropertyDefinition parsePropertyLine(String line, List<String> comments, 
                                               String group, boolean sensitive, boolean required) {
        def parts = line.split('=', 2)
        if (parts.length < 1) return null
        
        String key = parts[0].trim()
        String defaultValue = parts.length > 1 ? parts[1].trim() : ""
        
        if (key.isEmpty()) return null
        
        def propertyDef = new PropertyDefinition()
        propertyDef.key = key
        propertyDef.defaultValue = defaultValue
        propertyDef.documentation = comments.join(' ')
        propertyDef.group = group ?: "General"
        propertyDef.sensitive = sensitive || SecurityUtils.isSensitive(key)
        propertyDef.required = required
        
        return propertyDef
    }
    
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
