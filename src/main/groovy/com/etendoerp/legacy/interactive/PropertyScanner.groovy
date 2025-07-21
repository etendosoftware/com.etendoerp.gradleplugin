package com.etendoerp.legacy.interactive

import com.etendoerp.legacy.interactive.model.PropertyDefinition
import com.etendoerp.legacy.interactive.utils.PropertyParser
import org.gradle.api.Project

/**
 * Scanner component responsible for discovering and unifying properties 
 * from multiple sources including gradle.properties and module documentation files.
 * 
 * This class orchestrates the scanning process and ensures properties are
 * properly unified and sorted for the interactive setup process.
 * 
 * @author Etendo Interactive Setup Team
 * @since 2.0.4
 */
class PropertyScanner {
    
    private final Project project
    private final PropertyParser parser
    
    PropertyScanner(Project project) {
        this.project = project
        this.parser = new PropertyParser(project)
    }
    
    /**
     * Scans all available property sources and returns a unified list 
     * of PropertyDefinition objects.
     * 
     * This method:
     * 1. Scans existing gradle.properties for current values
     * 2. Scans module documentation files for metadata and defaults
     * 3. Unifies and sorts the results
     * 4. Handles conflicts by prioritizing current values over defaults
     * 
     * @return Sorted and unified list of PropertyDefinition objects
     */
    List<PropertyDefinition> scanAllProperties() {
        project.logger.info("Starting property scanning process...")
        
        long startTime = System.currentTimeMillis()
        
        try {
            // Scan gradle.properties for current values
            project.logger.debug("Scanning gradle.properties...")
            def gradleProperties = scanGradleProperties()
            project.logger.info("Found ${gradleProperties.size()} properties in gradle.properties")
            
            // Scan module documentation files
            project.logger.debug("Scanning module documentation...")
            def docProperties = scanModuleDocumentation()
            project.logger.info("Found ${docProperties.size()} documented properties in modules")
            
            // Unify and sort properties
            project.logger.debug("Unifying and sorting properties...")
            def unifiedProperties = unifyAndSort(gradleProperties, docProperties)
            
            long elapsed = System.currentTimeMillis() - startTime
            project.logger.info("Property scanning completed in ${elapsed}ms. Total: ${unifiedProperties.size()} properties")
            
            // Log scanning summary
            logScanningSummary(unifiedProperties)
            
            return unifiedProperties
            
        } catch (Exception e) {
            project.logger.error("Error during property scanning: ${e.message}", e)
            throw new RuntimeException("Failed to scan properties", e)
        }
    }
    
    /**
     * Scans gradle.properties file for existing property values.
     * 
     * @return List of PropertyDefinition objects with current values
     */
    List<PropertyDefinition> scanGradleProperties() {
        def startTime = System.currentTimeMillis()
        def properties = parser.parseGradleProperties()
        def elapsed = System.currentTimeMillis() - startTime
        
        project.logger.debug("Scanned gradle.properties in ${elapsed}ms")
        return properties
    }
    
    /**
     * Scans all modules directories for setup.properties.docs files.
     * 
     * @return List of PropertyDefinition objects with documentation metadata
     */
    List<PropertyDefinition> scanModuleDocumentation() {
        def startTime = System.currentTimeMillis()
        def properties = parser.parseModuleDocumentation()
        def elapsed = System.currentTimeMillis() - startTime
        
        project.logger.debug("Scanned module documentation in ${elapsed}ms")
        return properties
    }
    
    /**
     * Unifies properties from different sources and sorts them appropriately.
     * 
     * Resolution strategy for conflicts:
     * - Current values from gradle.properties take precedence
     * - Documentation and metadata from module docs are preserved
     * - Default values are used when no current value exists
     * - Duplicate properties (same key) are merged intelligently
     * 
     * @param gradleProperties Properties from gradle.properties
     * @param docProperties Properties from documentation files
     * @return Unified and sorted list of properties
     */
    List<PropertyDefinition> unifyAndSort(List<PropertyDefinition> gradleProperties, 
                                         List<PropertyDefinition> docProperties) {
        
        // Use PropertyParser's merge logic for consistent behavior
        def unified = PropertyParser.mergeProperties(gradleProperties, docProperties)
        
        // Log any conflicts found during merging
        logMergeConflicts(gradleProperties, docProperties, unified)
        
        return unified
    }
    
    /**
     * Logs a summary of the scanning process for debugging and monitoring.
     */
    private void logScanningSummary(List<PropertyDefinition> properties) {
        if (!project.logger.isInfoEnabled()) return
        
        def groupCounts = properties.groupBy { it.group }.collectEntries { group, props ->
            [group, props.size()]
        }
        
        def sensitiveCounts = properties.count { it.sensitive }
        def requiredCounts = properties.count { it.required }
        def withValueCounts = properties.count { it.hasValue() }
        
        project.logger.info("=== Property Scanning Summary ===")
        project.logger.info("Total properties: ${properties.size()}")
        project.logger.info("Properties with values: ${withValueCounts}")
        project.logger.info("Sensitive properties: ${sensitiveCounts}")
        project.logger.info("Required properties: ${requiredCounts}")
        project.logger.info("Properties by group:")
        groupCounts.each { group, count ->
            project.logger.info("  ${group}: ${count}")
        }
    }
    
    /**
     * Logs any conflicts found during the merge process.
     */
    private void logMergeConflicts(List<PropertyDefinition> gradleProps, 
                                  List<PropertyDefinition> docProps,
                                  List<PropertyDefinition> unified) {
        
        // Find properties that exist in both sources
        def gradleKeys = gradleProps.collect { it.key }.toSet()
        def docKeys = docProps.collect { it.key }.toSet()
        def conflictKeys = gradleKeys.intersect(docKeys)
        
        if (conflictKeys.size() > 0) {
            project.logger.info("Found ${conflictKeys.size()} properties in both gradle.properties and documentation:")
            conflictKeys.each { key ->
                def gradleProp = gradleProps.find { it.key == key }
                def docProp = docProps.find { it.key == key }
                def unifiedProp = unified.find { it.key == key }
                
                project.logger.debug("  ${key}:")
                project.logger.debug("    gradle.properties: '${gradleProp.currentValue}'")
                project.logger.debug("    documentation: '${docProp.defaultValue}' (group: ${docProp.group})")
                project.logger.debug("    unified: current='${unifiedProp.currentValue}', default='${unifiedProp.defaultValue}'")
            }
        }
        
        // Check for properties only in documentation (new properties)
        def newProperties = docKeys - gradleKeys
        if (newProperties.size() > 0) {
            project.logger.info("Found ${newProperties.size()} new properties from documentation: ${newProperties}")
        }
        
        // Check for properties only in gradle.properties (undocumented)
        def undocumentedProperties = gradleKeys - docKeys
        if (undocumentedProperties.size() > 0) {
            project.logger.info("Found ${undocumentedProperties.size()} undocumented properties in gradle.properties: ${undocumentedProperties}")
        }
    }
    
    /**
     * Validates the scanning results and performs basic consistency checks.
     * 
     * @param properties The scanned properties to validate
     * @return true if validation passes, false otherwise
     */
    boolean validateScanResults(List<PropertyDefinition> properties) {
        if (properties == null) {
            project.logger.error("Property scan returned null")
            return false
        }
        
        // Check for duplicate keys (should not happen after unification)
        def keys = properties.collect { it.key }
        def duplicateKeys = keys.findAll { keys.count(it) > 1 }.unique()
        if (duplicateKeys.size() > 0) {
            project.logger.error("Found duplicate property keys after unification: ${duplicateKeys}")
            return false
        }
        
        // Check for empty or null keys
        def invalidProperties = properties.findAll { !it.key || it.key.trim().isEmpty() }
        if (invalidProperties.size() > 0) {
            project.logger.error("Found ${invalidProperties.size()} properties with empty or null keys")
            return false
        }
        
        // Log warning for required properties without values
        def requiredWithoutValues = properties.findAll { it.required && !it.hasValue() }
        if (requiredWithoutValues.size() > 0) {
            project.logger.warn("Found ${requiredWithoutValues.size()} required properties without default values:")
            requiredWithoutValues.each { prop ->
                project.logger.warn("  ${prop.key} (group: ${prop.group})")
            }
        }
        
        return true
    }
}
