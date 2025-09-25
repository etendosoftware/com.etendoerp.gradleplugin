package com.etendoerp.legacy.interactive

import com.etendoerp.legacy.interactive.model.PropertyDefinition
import com.etendoerp.legacy.interactive.utils.PropertyParser
import org.gradle.api.Project

/**
 * Enhanced property scanner that supports both legacy setup.properties.docs format
 * and the new ConfigSlurper-based config.gradle format.
 * 
 * This scanner provides:
 * - Native support for ConfigSlurper config.gradle files
 * - Backward compatibility with setup.properties.docs files
 * - Unified property unification and sorting
 * - Improved metadata handling through structured configuration
 * 
 * @author Etendo Interactive Setup Team
 * @since 2.0.4
 */
class ConfigSlurperPropertyScanner {
    
    private static final String CONFIG_GRADLE_FILE = "config.gradle"
    
    private final Project project
    private final PropertyParser parser
    
    ConfigSlurperPropertyScanner(Project project) {
        if (project == null) {
            throw new Exception("Project cannot be null")
        }
        this.project = project
        this.parser = new PropertyParser(project)
    }
    
    /**
     * Scans all available property sources using the new ConfigSlurper format
     * with fallback to legacy format for backward compatibility.
     * 
     * @return Sorted and unified list of PropertyDefinition objects
     */
    List<PropertyDefinition> scanAllProperties() {
        project.logger.info("Starting enhanced property scanning process...")
        
        long startTime = System.currentTimeMillis()
        
        try {
            // Scan gradle.properties for current values (now using internal method)
            project.logger.debug("Scanning gradle.properties...")
            def gradleProperties = scanGradleProperties()
            project.logger.info("Found ${gradleProperties.size()} properties in gradle.properties")
            
            // Try to scan config.gradle files first
            project.logger.debug("Scanning config.gradle files...")
            def configProperties = scanConfigGradleFiles()
            
            // Legacy .docs format support removed in ETP-1960-06
            if (configProperties.isEmpty()) {
                project.logger.info("No config.gradle files found. Legacy setup.properties.docs support removed.")
                project.logger.info("Please migrate to config.gradle format in your modules.")
            } else {
                project.logger.info("Found ${configProperties.size()} documented properties in config.gradle files")
            }
            
            // Unify and sort properties
            project.logger.debug("Unifying and sorting properties...")
            def unifiedProperties = unifyAndSort(gradleProperties, configProperties)
            
            long elapsed = System.currentTimeMillis() - startTime
            project.logger.info("Enhanced property scanning completed in ${elapsed}ms. Total: ${unifiedProperties.size()} properties")
            
            // Log scanning summary
            logScanningSummary(unifiedProperties)
            
            return unifiedProperties
            
        } catch (Exception e) {
            project.logger.error("Error during enhanced property scanning: ${e.message}", e)
            throw new RuntimeException("Failed to scan properties with ConfigSlurper scanner", e)
        }
    }
    
    /**
     * Scans all directories for config.gradle files using ConfigSlurper.
     * Includes both the project root and modules directories.
     * 
     * @return List of PropertyDefinition objects with structured metadata
     */
    List<PropertyDefinition> scanConfigGradleFiles() {
        def properties = []
        def startTime = System.currentTimeMillis()
        
        // Check project root for main config.gradle
        def rootConfigFile = project.file(CONFIG_GRADLE_FILE)
        if (rootConfigFile.exists()) {
            project.logger.debug("Processing main config.gradle in project root")
            try {
                def rootProperties = parseConfigGradle(rootConfigFile, "main")
                properties.addAll(rootProperties)
                project.logger.debug("Loaded ${rootProperties.size()} properties from main config.gradle")
            } catch (Exception e) {
                // Enhanced error handling - log error but continue processing modules
                project.logger.warn("Failed to parse main config.gradle: ${e.message}")
                project.logger.debug("Full stack trace for debugging:", e)
            }
        }
        
        // Define all search paths for config.gradle files
        def searchPaths = [
            [path: 'modules', description: 'Standard modules'],
            [path: 'modules_core', description: 'Core modules'],
            [path: 'build/etendo/modules', description: 'Build modules']
        ]
        
        // Scan each search path for config.gradle files
        searchPaths.each { searchPath ->
            def searchDir = project.file(searchPath.path)
            project.logger.debug("Scanning ${searchPath.description} directory: ${searchDir.absolutePath}")
            
            if (searchDir.exists() && searchDir.isDirectory()) {
                project.logger.info("✓ Found ${searchPath.description} directory: ${searchDir.absolutePath}")
                
                // Scan each module directory for config.gradle
                def moduleCount = 0
                def configCount = 0
                
                searchDir.listFiles()?.findAll { it.isDirectory() }?.each { moduleDir ->
                    moduleCount++
                    def configFile = new File(moduleDir, CONFIG_GRADLE_FILE)
                    if (configFile.exists()) {
                        configCount++
                        project.logger.debug("✓ Processing config.gradle in ${searchPath.description} module: ${moduleDir.name}")
                        try {
                            def moduleProperties = parseConfigGradle(configFile, "${searchPath.path}/${moduleDir.name}")
                            properties.addAll(moduleProperties)
                            project.logger.debug("Loaded ${moduleProperties.size()} properties from ${searchPath.path}/${moduleDir.name}")
                        } catch (Exception e) {
                            // Enhanced error handling - log error but continue processing other modules
                            project.logger.warn("✗ Failed to parse config.gradle in ${searchPath.path}/${moduleDir.name}: ${e.message}")
                            project.logger.debug("Full stack trace for debugging:", e)
                        }
                    } else {
                        project.logger.debug("✗ No config.gradle found in ${searchPath.path}/${moduleDir.name}")
                    }
                }
                
                project.logger.info("Scanned ${moduleCount} modules in ${searchPath.description}, found ${configCount} config.gradle files")
            } else {
                project.logger.info("✗ ${searchPath.description} directory not found: ${searchDir.absolutePath}")
            }
        }
        
        def elapsed = System.currentTimeMillis() - startTime
        project.logger.debug("Scanned config.gradle files in ${elapsed}ms")
        return properties
    }
    
    /**
     * Parses a single config.gradle file using ConfigSlurper.
     * 
     * @param configFile The config.gradle file to parse
     * @param moduleName The name of the module (for logging/debugging)
     * @return List of PropertyDefinition objects from this file
     */
    List<PropertyDefinition> parseConfigGradle(File configFile, String moduleName) {
        def properties = []
        
        try {
            // Use ConfigSlurper to parse the Groovy configuration
            def configSlurper = new ConfigSlurper()
            def config = configSlurper.parse(configFile.text)
            
            // Process each configuration group - preserve order by using keySet()
            def groupKeys = config.keySet().toList()
            def orderIndex = 0
            groupKeys.each { groupKey ->
                def groupConfig = config[groupKey]
                processConfigGroup(groupKey.toString(), groupConfig, properties, moduleName, orderIndex++)
            }
            
        } catch (Exception e) {
            // Log error but don't throw - allow scanner to continue with other files
            project.logger.error("Error parsing config.gradle in ${moduleName}: ${e.message}", e)
            project.logger.warn("✗ Failed to parse config.gradle in ${moduleName}. Continuing with other files.")
        }
        
        return properties
    }
    
    /**
     * Processes a configuration group and extracts property definitions.
     * 
     * @param groupKey The group key (e.g., "database", "security") or property key for root-level properties
     * @param groupConfig The configuration object for this group
     * @param properties The list to add properties to
     * @param moduleName The module name for context
     * @param orderIndex Counter for preserving order of properties
     */
    private void processConfigGroup(String groupKey, Object groupConfig, List<PropertyDefinition> properties, String moduleName, int orderIndex = 0) {
        if (!(groupConfig instanceof ConfigObject)) {
            return
        }
        // Skip invalid empty group keys that print as '[:]'
        if (groupKey == null) return
        if (groupKey.toString().trim() == '[:]') return
        
        // Check if this is a root-level property (has description, value, etc. directly)
        if (groupConfig.containsKey('description') || groupConfig.containsKey('value')) {
            // This is a root-level property, not a group
            try {
                def property = createPropertyDefinitionFromRootLevel(groupKey, groupConfig, moduleName, orderIndex)
                if (property) {
                    properties.add(property)
                }
            } catch (Exception e) {
                project.logger.warn("Failed to process root-level property ${groupKey}: ${e.message}")
            }
        } else {
            // This is a group with nested properties - preserve order by using keySet() and indexing
            def keysList = groupConfig.keySet().toList()
            keysList.eachWithIndex { propertyKey, index ->
                def propertyConfig = groupConfig[propertyKey]
                if (propertyConfig instanceof ConfigObject) {
                    try {
                        def property = createPropertyDefinition(groupKey, propertyKey.toString(), propertyConfig, moduleName, orderIndex + index)
                        if (property) {
                            properties.add(property)
                        }
                    } catch (Exception e) {
                        project.logger.warn("Failed to process property ${propertyKey} in group ${groupKey}: ${e.message}")
                    }
                }
            }
        }
    }
    
    /**
     * Creates a PropertyDefinition from a root-level ConfigSlurper property configuration.
     *
     * @param propertyKey The property key (this is also the gradle.properties key)
     * @param propertyConfig The configuration object containing metadata
     * @param moduleName The module name for context
     * @param orderIndex The order index for preserving definition order
     * @return PropertyDefinition object or null if invalid
     */
    private PropertyDefinition createPropertyDefinitionFromRootLevel(String propertyKey, ConfigObject propertyConfig, String moduleName, int orderIndex) {
        // Determine gradle key: prefer custom name if it's a real value, otherwise fallback
    def gradleKey = propertyKey
        def rawName = propertyConfig.name
        if (rawName != null) {
            if (rawName instanceof ConfigObject || rawName instanceof Map) {
                if (!rawName.isEmpty()) {
                    gradleKey = rawName.toString()
                }
            } else {
                gradleKey = rawName.toString()
            }
        }
        // Guard against weird toString() cases like '[:]' - fallback to propertyKey
        if (gradleKey == null || gradleKey.toString().trim().isEmpty() || gradleKey.toString().trim() == '[:]') {
            gradleKey = propertyKey ?: mapToGradlePropertyKey(groupKey, propertyKey)
        }
        
        // Extract metadata from the configuration
        def description = ""
        def rawDescription = propertyConfig.description
        if (rawDescription == null) {
            description = ""
        } else if (rawDescription instanceof ConfigObject || rawDescription instanceof Map) {
            description = rawDescription.isEmpty() ? "" : rawDescription.toString()
        } else {
            description = rawDescription.toString()
        }
        def defaultValue = ""
        def rawValue = propertyConfig.value
        if (rawValue == null) {
            defaultValue = ""
        } else if (rawValue instanceof ConfigObject || rawValue instanceof Map) {
            defaultValue = rawValue.isEmpty() ? "" : rawValue.toString()
        } else {
            defaultValue = rawValue.toString()
        }
        def helpText = ""

        def rawHelp = propertyConfig.help
        if (rawHelp == null) {
            helpText = defaultValue ?: ""
        } else if (rawHelp instanceof ConfigObject || rawHelp instanceof Map) {
            if (rawHelp.isEmpty()) {
                helpText = defaultValue ?: ""
            } else {
                helpText = rawHelp.toString()
            }
        } else {
            helpText = rawHelp.toString()
        }
        def sensitive = propertyConfig.sensitive instanceof Boolean ? propertyConfig.sensitive : false
        def required = propertyConfig.required instanceof Boolean ? propertyConfig.required : false
        def process = propertyConfig.process instanceof Boolean ? propertyConfig.process : false
        def notSetWhenDefault = propertyConfig.notSetWhenDefault instanceof Boolean ? propertyConfig.notSetWhenDefault : false
    def group = "General"
        def rawGroup = propertyConfig.group
        if (rawGroup == null) {
            group = "General"
        } else if (rawGroup instanceof ConfigObject || rawGroup instanceof Map) {
            if (rawGroup.isEmpty()) {
                group = "General"
            } else {
                group = rawGroup.toString()
            }
        } else {
            group = rawGroup.toString()
        }
        // Normalize group if it ended up as '[:]'
        if (group == null || group.toString().trim() == '[:]') {
            group = "General"
        }
        
        project.logger.debug("Creating root-level property: ${gradleKey} (group: ${group}, sensitive: ${sensitive}, required: ${required}, process: ${process}, notSetWhenDefault: ${notSetWhenDefault}, order: ${orderIndex})")
        
        return new PropertyDefinition(
            key: gradleKey,
            currentValue: null, // Will be filled from gradle.properties during unification
            defaultValue: defaultValue,
            documentation: description,
            help: helpText,
            group: group,
            sensitive: sensitive,
            required: required,
            process: process,
            notSetWhenDefault: notSetWhenDefault,
            source: "config.gradle (${moduleName})",
            order: orderIndex
        )
    }    /**
     * Creates a PropertyDefinition from a ConfigSlurper property configuration.
     *
     * @param groupKey The group this property belongs to
     * @param propertyKey The property key
     * @param propertyConfig The configuration object containing metadata
     * @param moduleName The module name for context
     * @param orderIndex The order index for preserving definition order
     * @return PropertyDefinition object or null if invalid
     */
    private PropertyDefinition createPropertyDefinition(String groupKey, String propertyKey, ConfigObject propertyConfig, String moduleName, int orderIndex) {
        // Determine gradle key: prefer custom name if it's a real value, otherwise use mapping
    def gradleKey = mapToGradlePropertyKey(groupKey, propertyKey)
        def rawName = propertyConfig.name
        if (rawName != null) {
            if (rawName instanceof ConfigObject || rawName instanceof Map) {
                if (!rawName.isEmpty()) {
                    gradleKey = rawName.toString()
                }
            } else {
                gradleKey = rawName.toString()
            }
        }
        // Guard against weird toString() cases like '[:]' - fallback to mapping
        if (gradleKey == null || gradleKey.toString().trim().isEmpty() || gradleKey.toString().trim() == '[:]') {
            gradleKey = mapToGradlePropertyKey(groupKey, propertyKey)
        }
        
        // Extract metadata from the configuration
        def description = ""
        def rawDescription = propertyConfig.description
        if (rawDescription == null) {
            description = ""
        } else if (rawDescription instanceof ConfigObject || rawDescription instanceof Map) {
            description = rawDescription.isEmpty() ? "" : rawDescription.toString()
        } else {
            description = rawDescription.toString()
        }
        def defaultValue = ""
        def rawValue = propertyConfig.value
        if (rawValue == null) {
            defaultValue = ""
        } else if (rawValue instanceof ConfigObject || rawValue instanceof Map) {
            defaultValue = rawValue.isEmpty() ? "" : rawValue.toString()
        } else {
            defaultValue = rawValue.toString()
        }
        def helpText = ""

        def rawHelp = propertyConfig.help
        if (rawHelp == null) {
            helpText = defaultValue ?: ""
        } else if (rawHelp instanceof ConfigObject || rawHelp instanceof Map) {
            if (rawHelp.isEmpty()) {
                helpText = defaultValue ?: ""
            } else {
                helpText = rawHelp.toString()
            }
        } else {
            helpText = rawHelp.toString()
        }
        def sensitive = propertyConfig.sensitive instanceof Boolean ? propertyConfig.sensitive : false
        def required = propertyConfig.required instanceof Boolean ? propertyConfig.required : false
        def process = propertyConfig.process instanceof Boolean ? propertyConfig.process : false
        def notSetWhenDefault = propertyConfig.notSetWhenDefault instanceof Boolean ? propertyConfig.notSetWhenDefault : false
    def group = capitalizeFirst(groupKey)
        def rawGroup = propertyConfig.group
        if (rawGroup == null) {
            group = capitalizeFirst(groupKey)
        } else if (rawGroup instanceof ConfigObject || rawGroup instanceof Map) {
            if (rawGroup.isEmpty()) {
                group = capitalizeFirst(groupKey)
            } else {
                group = rawGroup.toString()
            }
        } else {
            group = rawGroup.toString()
        }
        if (group == null || group.toString().trim() == '[:]') {
            group = capitalizeFirst(groupKey)
        }
        
        project.logger.debug("Creating property: ${gradleKey} (group: ${group}, sensitive: ${sensitive}, required: ${required}, process: ${process}, notSetWhenDefault: ${notSetWhenDefault}, order: ${orderIndex})")
        
        return new PropertyDefinition(
            key: gradleKey,
            currentValue: null, // Will be filled from gradle.properties during unification
            defaultValue: defaultValue,
            documentation: description,
            help: helpText,
            group: group,
            sensitive: sensitive,
            required: required,
            process: process,
            notSetWhenDefault: notSetWhenDefault,
            source: "config.gradle (${moduleName})",
            order: orderIndex
        )
    }    /**
     * Maps structured property keys to gradle.properties format.
     * 
     * This method is used as fallback when no custom 'name' is specified
     * in the property configuration. Preserves original property names 
     * without any transformation.
     * 
     * Examples:
     * - database.host -> database.host
     * - database.systemUser -> database.systemUser  
     * - apiKeys.openai -> apiKeys.openai (unless name="OPENAI_API_KEY" is specified)
     * - security.githubToken -> security.githubToken
     * 
     * @param groupKey The group key
     * @param propertyKey The property key (preserved as-is)
     * @return The gradle.properties compatible key
     */
    private String mapToGradlePropertyKey(String groupKey, String propertyKey) {
        // No transformation - preserve original property names
        return "${groupKey.toLowerCase()}.${propertyKey}"
    }
    
    /**
     * Capitalizes the first letter of a string.
     */
    private String capitalizeFirst(String str) {
        if (!str) return str
        return str.substring(0, 1).toUpperCase() + str.substring(1)
    }
    
    /**
     * Unifies properties from gradle.properties and config.gradle files.
     * Uses PropertyParser's merge logic for consistent behavior.
     */
    private List<PropertyDefinition> unifyAndSort(List<PropertyDefinition> gradleProperties, 
                                                 List<PropertyDefinition> configProperties) {
        return PropertyParser.mergeProperties(gradleProperties, configProperties)
    }
    
    /**
     * Logs scanning summary using the same format as legacy scanner.
     */
    private void logScanningSummary(List<PropertyDefinition> properties) {
        if (!project.logger.isInfoEnabled()) return
        
        def groupCounts = properties.groupBy { it.group }.collectEntries { group, props ->
            [group, props.size()]
        }
        
        def sensitiveCounts = properties.count { it.sensitive }
        def requiredCounts = properties.count { it.required }
        def withValueCounts = properties.count { it.hasValue() }
        def configGradleCounts = properties.count { it.source?.contains("config.gradle") }
        def legacyCounts = properties.size() - configGradleCounts
        
        project.logger.info("=== Enhanced Property Scanning Summary ===")
        project.logger.info("Total properties: ${properties.size()}")
        project.logger.info("Properties with values: ${withValueCounts}")
        project.logger.info("Sensitive properties: ${sensitiveCounts}")
        project.logger.info("Required properties: ${requiredCounts}")
        project.logger.info("From config.gradle: ${configGradleCounts}")
        if (legacyCounts > 0) {
            project.logger.info("From legacy format: ${legacyCounts}")
        }
        project.logger.info("Properties by group:")
        groupCounts.each { group, count ->
            project.logger.info("  ${group}: ${count}")
        }
    }
    
    /**
     * Validates the scanning results.
     */
    boolean validateScanResults(List<PropertyDefinition> properties) {
        if (properties == null) {
            project.logger.error("Property scanning returned null results")
            return false
        }
        
        if (properties.isEmpty()) {
            project.logger.warn("No properties found during scanning")
            return true // Empty is valid, just a warning
        }
        
        // Check for null or invalid properties
        def invalidProperties = properties.findAll { prop ->
            prop == null || 
            prop.key == null || 
            prop.key.trim().isEmpty()
        }
        
        if (!invalidProperties.isEmpty()) {
            project.logger.error("Found ${invalidProperties.size()} invalid properties with null/empty keys")
            return false
        }
        
        project.logger.debug("Property scanning validation passed: ${properties.size()} valid properties")
        return true
    }
    
    /**
     * Scans gradle.properties for current values (migrated from PropertyScanner).
     * 
     * @return List of PropertyDefinition objects from gradle.properties
     */
    List<PropertyDefinition> scanGradleProperties() {
        def startTime = System.currentTimeMillis()
        def properties = parser.parseGradleProperties()
        def elapsed = System.currentTimeMillis() - startTime
        
        project.logger.debug("Scanned gradle.properties in ${elapsed}ms")
        return properties
    }
}
