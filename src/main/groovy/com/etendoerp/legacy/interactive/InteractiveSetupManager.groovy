package com.etendoerp.legacy.interactive

import com.etendoerp.legacy.interactive.model.PropertyDefinition
import groovy.json.JsonSlurper
import org.gradle.api.Project
import com.etendoerp.connections.DatabaseConnection
import org.gradle.api.tasks.StopExecutionException

/**
 * Main orchestrator for the interactive setup process.
 *
 * This class coordinates the entire interactive configuration workflow:
 * 1. Scanning properties from multiple sources (config.gradle and setup.properties.docs)
 * 2. Collecting user input through guided prompts with main menu navigation
 * 3. Confirming configuration with the user
 * 4. Writing the final configuration to gradle.properties
 *
 * The manager ensures that the process is robust, user-friendly, and
 * maintains data integrity throughout the configuration process.
 *
 * Enhanced in version 2.0.4 to support ConfigSlurper-based configuration
 * with backward compatibility for legacy setup.properties.docs format.
 *
 * @author Etendo Interactive Setup Team
 * @since 2.0.4
 */
class InteractiveSetupManager {

    private final Project project
    private final ConfigSlurperPropertyScanner scanner
    private final UserInteraction ui
    private final ConfigWriter writer
    private final ProgressSuppressor progressSuppressor
    private List<PropertyDefinition> allPropertiesCache = []

    /**
     * Creates a new InteractiveSetupManager with enhanced ConfigSlurper support.
     *
     * @param project The Gradle project context
     */
    InteractiveSetupManager(Project project) {
        this.project = project
        this.scanner = new ConfigSlurperPropertyScanner(project)
        this.ui = new UserInteraction(project)
        this.writer = new ConfigWriter(project)
        this.progressSuppressor = new ProgressSuppressor(project)

        // Set reference to avoid circular dependency
        this.ui.setSetupManager(this)
        // Register project-level ext helper so tasks can call project.writeResultsForInteractiveSetup
        try {
            registerProjectExt(project)
        } catch (Exception e) {
            project.logger.debug("Failed to register project ext in InteractiveSetupManager constructor: ${e.message}")
        }
    }

    /**
     * Executes the traditional setup task programmatically.
     *
     * @throws RuntimeException if the setup execution fails
     */
    private void executeTraditionalSetup() {
        project.logger.quiet("")
        project.logger.quiet("🚀 **Executing Traditional Setup**")
        project.logger.quiet("=" * 50)
        project.logger.quiet("Running traditional setup task...")
        project.logger.quiet("")

        try {
            // Find the setup task
            def setupTask = project.tasks.findByName("setup")
            if (!setupTask) {
                throw new RuntimeException("Setup task not found. Please ensure the project is properly configured.")
            }

            project.logger.lifecycle("Found setup task, executing...")

            // Execute setup task using Gradle's internal mechanisms
            // This approach respects task dependencies and lifecycle
            def taskExecutionResult = project.gradle.taskGraph.getAllTasks().find { it.name == 'setup' }

            if (taskExecutionResult) {
                // Task is in the graph, execute it
                project.logger.info("Executing setup task through task graph...")
                setupTask.actions.each { action ->
                    action.execute(setupTask)
                }
            } else {
                // Execute task directly with proper lifecycle
                project.logger.info("Executing setup task directly...")

                // Execute task dependencies first if any
                setupTask.taskDependencies.getDependencies(setupTask).each { depTask ->
                    project.logger.debug("Executing setup dependency: ${depTask.name}")
                    depTask.actions.each { action ->
                        action.execute(depTask)
                    }
                }

                // Execute the setup task itself
                setupTask.actions.each { action ->
                    action.execute(setupTask)
                }
            }

            project.logger.quiet("")
            project.logger.quiet("✅ **Setup completed successfully!**")
            project.logger.quiet("Your Etendo environment is now configured and ready.")
            project.logger.quiet("")

        } catch (Exception e) {
            project.logger.error("")
            project.logger.error("❌ **Setup execution failed**")
            project.logger.error("=" * 50)
            project.logger.error("Error during traditional setup execution: ${e.message}")
            project.logger.error("")
            project.logger.error("**Your configuration has been saved to gradle.properties.**")
            project.logger.error("You can run the setup manually with: ./gradlew setup")
            project.logger.error("")
            project.logger.debug("Setup execution error details:", e)

            throw new RuntimeException("Traditional setup execution failed. Configuration saved but setup incomplete.", e)
        }
    }

    /**
     * Executes the complete interactive setup process.
     *
     * This is the main entry point for the interactive configuration.
     * The method orchestrates the entire workflow and handles errors gracefully.
     *
     * @throws StopExecutionException if the user cancels the configuration
     * @throws RuntimeException if a critical error occurs during the process
     */
    void execute() {
        // Ensure quiet output during user interaction
        def originalLogLevel = project.gradle.startParameter.logLevel
        project.gradle.startParameter.logLevel = org.gradle.api.logging.LogLevel.LIFECYCLE

        // Apply comprehensive progress suppression
        progressSuppressor.suppressProgress()

        project.logger.quiet("This wizard will guide you through configuring your Etendo project.")
        project.logger.quiet("Press Enter to keep existing values or type new ones.")
        project.logger.quiet("")
        project.logger.quiet("Scanning for configuration properties...")

        try {
            // Step 1: Scan all available properties
            project.logger.lifecycle("Scanning for configuration properties...")
            List<PropertyDefinition> properties = scanner.scanAllProperties()

            // Cache the scanned properties for use during process executions
            this.allPropertiesCache = properties

            if (properties.isEmpty()) {
                project.logger.lifecycle("No configurable properties found. Setup complete.")
                return
            }

            // Validate scan results
            if (!scanner.validateScanResults(properties)) {
                throw new RuntimeException("Property scanning validation failed")
            }

            project.logger.quiet("Found ${properties.size()} configurable properties.")
            project.logger.quiet("")

            // Step 2: Show main menu and collect user input based on selection
            project.logger.quiet("Please select your configuration preference:")
            project.logger.quiet("")

            Map<String, String> configuredProperties = ui.showMainMenu(properties)

            // Check if user chose to exit
            if (configuredProperties == null) {
                project.logger.lifecycle("Setup cancelled by user.")
                throw new StopExecutionException("Interactive setup cancelled by user")
            }

            // Process any executed process properties and merge their results
            def finalProperties = processExecutedProcessProperties(configuredProperties, properties)

            project.logger.quiet("DEBUG: User-configured properties: ${configuredProperties.size()}")
            project.logger.quiet("DEBUG: Final properties map size: ${finalProperties.size()}")
            project.logger.quiet("DEBUG: Final properties: ${finalProperties}")

            // If empty map from default configuration, handle it differently
            if (finalProperties.isEmpty()) {
                project.logger.lifecycle("Using default configuration values.")
                project.logger.lifecycle("✅ Configuration completed successfully!")
                project.logger.lifecycle("Default settings are already configured.")
                return
            }

            // Step 3: Write configuration to gradle.properties
            project.logger.lifecycle("")
            project.logger.lifecycle("Writing configuration to gradle.properties...")

            // Filter out properties whose value equals their default value
            def filteredFinal = filterOutDefaults(finalProperties, properties)
            writer.writeProperties(filteredFinal)

            project.logger.lifecycle("✅ Configuration completed successfully!")
            project.logger.lifecycle("Your settings have been saved to gradle.properties")
            project.logger.lifecycle("")

            // Log summary of what was configured
            logConfigurationSummary(finalProperties, properties)

            // Step 4: Automatic setup execution (ETP-1960-03)
            // No additional confirmation needed - user already confirmed in the summary
            project.logger.lifecycle("")
            project.logger.lifecycle("🔗 **Automatic Setup Integration** (ETP-1960-03)")
            project.logger.lifecycle("=" * 60)
            project.logger.lifecycle("Proceeding with automatic traditional setup execution...")

            try {
                executeTraditionalSetup()

                project.logger.lifecycle("")
                project.logger.lifecycle("🎉 **Complete End-to-End Setup Finished!**")
                project.logger.lifecycle("=" * 60)
                project.logger.lifecycle("✅ Interactive configuration: COMPLETED")
                project.logger.lifecycle("✅ Traditional setup: COMPLETED")
                project.logger.lifecycle("✅ Environment ready for development")
                project.logger.lifecycle("")
                project.logger.lifecycle("Your Etendo environment is now fully configured and ready to use!")

            } catch (Exception setupError) {
                project.logger.error("")
                project.logger.error("⚠️  **Partial Setup Completion**")
                project.logger.error("=" * 60)
                project.logger.error("✅ Interactive configuration: COMPLETED")
                project.logger.error("❌ Traditional setup: FAILED")
                project.logger.error("")
                project.logger.error("Error during automatic setup: ${setupError.message}")
                project.logger.error("Your configuration has been saved successfully.")
                project.logger.error("Please run the setup manually: ./gradlew setup")
                project.logger.error("")

                // Don't throw the error - configuration was successful
                project.logger.warn("Setup integration failed but configuration is complete")
            }

        } catch (StopExecutionException e) {
            // User cancellation - re-throw to stop gradle execution cleanly
            throw e
        } catch (Exception e) {
            project.logger.error("❌ Interactive setup failed: ${e.message}", e)
            throw new RuntimeException("Interactive setup process failed", e)
        } finally {
            // Restore original log level
            project.gradle.startParameter.logLevel = originalLogLevel

            // Restore progress suppression settings
            progressSuppressor.restoreProgress()
        }
    }

    /**
     * Logs a summary of the configuration process for user reference.
     *
     * @param configuredProperties The final configured property values
     * @param allProperties All available properties for context
     */
    private void logConfigurationSummary(Map<String, String> configuredProperties,
                                         List<PropertyDefinition> allProperties) {

        def configuredCount = configuredProperties.size()
        def totalCount = allProperties.size()
        def sensitiveCount = configuredProperties.count { key, value ->
            def prop = allProperties.find { it.key == key }
            return prop?.sensitive ?: false
        }

        project.logger.lifecycle("=== Configuration Summary ===")
        project.logger.lifecycle("Configured ${configuredCount} of ${totalCount} available properties")

        if (sensitiveCount > 0) {
            project.logger.lifecycle("Including ${sensitiveCount} sensitive properties (passwords, tokens, etc.)")
        }

        // Group configured properties by category
        def groupedProps = [:]
        configuredProperties.each { key, value ->
            def prop = allProperties.find { it.key == key }
            def propGroups = prop?.groups?.isEmpty() ? ["General"] : prop?.groups
            def group = propGroups ? propGroups.join(", ") : "General"
            if (!groupedProps[group]) {
                groupedProps[group] = 0
            }
            groupedProps[group]++
        }

        project.logger.lifecycle("Properties configured by category:")
        groupedProps.each { group, count ->
            project.logger.lifecycle("  ${group}: ${count}")
        }

        // Check for required properties that weren't configured
        def requiredProperties = allProperties.findAll { it.required }
        def unconfiguredRequired = requiredProperties.findAll { prop ->
            !configuredProperties.containsKey(prop.key) && !prop.hasValue()
        }

        if (unconfiguredRequired.size() > 0) {
            project.logger.warn("⚠️ Warning: ${unconfiguredRequired.size()} required properties remain unconfigured:")
            unconfiguredRequired.each { prop ->
                def propGroups = prop?.groups?.isEmpty() ? ["General"] : prop?.groups
                project.logger.warn("  - ${prop.key} (${propGroups.join(", ")})")
            }
        }

        project.logger.lifecycle("")
    }

    /**
     * Performs pre-execution validation to ensure the environment is ready
     * for interactive setup.
     *
     * @return true if validation passes
     * @throws RuntimeException if validation fails
     */
    boolean validateEnvironment() {
        // Check if we're in a valid Etendo project
        if (!project.file('build.gradle').exists()) {
            throw new RuntimeException("Interactive setup must be run from an Etendo project root directory")
        }

        // Check if console is available for user interaction
        if (!System.console() && !System.getProperty("idea.test.runner")) {
            project.logger.warn("⚠️ Warning: No console available. Password input may not be hidden.")
        }

        // Check write permissions for gradle.properties
        def gradlePropsFile = project.file('gradle.properties')
        if (gradlePropsFile.exists() && !gradlePropsFile.canWrite()) {
            throw new RuntimeException("Cannot write to gradle.properties - check file permissions")
        }

        // Check if parent directory is writable (for creating gradle.properties if it doesn't exist)
        if (!gradlePropsFile.exists() && !gradlePropsFile.parentFile.canWrite()) {
            throw new RuntimeException("Cannot create gradle.properties - check directory permissions")
        }

        return true
    }

    /**
     * Executes a single process property and returns the configured values.
     * These properties execute Gradle tasks that output JSON configuration.
     *
     * @param processProperty The process property to execute
     * @return Map of property keys to configured values from the task output
     */
    Map<String, String> executeProcessProperty(PropertyDefinition processProperty) {
        def configuredProperties = [:]

        try {
            project.logger.lifecycle("� Executing process property: ${processProperty.key}")

            // Create temporary file for task output
            def tempFile = File.createTempFile("etendo-process-", ".json")
            tempFile.deleteOnExit()

            // Extract task name from the process property
            def taskName = extractTaskName(processProperty)

            project.logger.debug("Executing task '${taskName}' with output file: ${tempFile.absolutePath}")

            // Execute the task using Gradle's internal API
            def results = executeGradleTaskInternal(taskName, tempFile, processProperty)
                if (results != null) {
                    configuredProperties.putAll(results)
                    // Filter out defaults before writing
                    def toWrite = filterOutDefaults(configuredProperties, this.allPropertiesCache)
                    if (!toWrite.isEmpty()) {
                        writer.writeProperties(toWrite)
                        project.logger.debug("Wrote ${toWrite.size()} properties to gradle.properties (internal)")
                    }
                    return configuredProperties
                }

            // Write the configured properties to gradle.properties if any were configured
            if (!configuredProperties.isEmpty()) {
                def toWrite = filterOutDefaults(configuredProperties, this.allPropertiesCache)
                if (!toWrite.isEmpty()) {
                    writer.writeProperties(toWrite)
                    project.logger.debug("Wrote ${toWrite.size()} properties to gradle.properties")
                }
            }

        } catch (Exception e) {
            project.logger.error("❌ Error processing property ${processProperty.key}: ${e.message}", e)
        }

        return configuredProperties
    }

    /**
     * Updates the current property values after a process property has been executed.
     * This method reloads the properties from gradle.properties and updates any matching
     * properties with their new values.
     *
     * @param properties The list of all properties to update
     * @param configuredProperties The properties that were configured by the process task
     */
    void updatePropertiesAfterProcessExecution(List<PropertyDefinition> properties,
                                               Map<String, String> configuredProperties) {
        project.logger.debug("Updating property values after process execution...")

        // Handle null or empty inputs
        if (properties == null || properties.isEmpty()) {
            project.logger.debug("No properties to update")
            return
        }

        def gradlePropsMap = [:]

        try {
            // Reload current values from gradle.properties
            def currentGradleProperties = scanner.scanGradleProperties()
            currentGradleProperties.each { prop ->
                gradlePropsMap[prop.key] = prop.currentValue
            }
        } catch (Exception e) {
            project.logger.warn("Failed to scan gradle.properties: ${e.message}")
            // Continue with empty gradlePropsMap - configured properties will still be applied
        }

        try {
            // Update the current values in the properties list
            int updatedCount = 0
            properties.each { prop ->
                if (configuredProperties != null && configuredProperties.containsKey(prop.key)) {
                    // Property was configured by the process task
                    def newValue = configuredProperties[prop.key]
                    if (prop.currentValue != newValue) {
                        project.logger.debug("Updating property ${prop.key}: '${prop.currentValue}' -> '${newValue}'")
                        prop.currentValue = newValue
                        updatedCount++
                    }
                } else if (gradlePropsMap.containsKey(prop.key)) {
                    // Property might have been updated in gradle.properties
                    def gradleValue = gradlePropsMap[prop.key]
                    if (prop.currentValue != gradleValue) {
                        project.logger.debug("Updating property ${prop.key} from gradle.properties: '${prop.currentValue}' -> '${gradleValue}'")
                        prop.currentValue = gradleValue
                        updatedCount++
                    }
                }
            }

            project.logger.debug("Updated ${updatedCount} property values after process execution")

        } catch (Exception e) {
            project.logger.warn("Failed to update properties after process execution: ${e.message}")
        }
    }

    /**
     * Extracts the task name from a process property.
     * Priority order:
     * 1. Task name from documentation (Task: taskname)
     * 2. Property key as-is (for tasks like copilot.variables.setup)  
     * 3. Converted camelCase name
     */
    public String extractTaskName(PropertyDefinition processProperty) {
        // First check if there's a task specification in documentation
        if (processProperty.documentation && processProperty.documentation.contains("Task: ")) {
            def matcher = processProperty.documentation =~ /Task: ([\w\.]+)/
            if (matcher) {
                return matcher[0][1]
            }
        }

        // For properties that map directly to task names (like copilot.variables.setup)
        // Use the key as-is first
        return processProperty.key
    }

    /**
     * Attempts to execute a Gradle task using internal API.
     */
    private Map<String, String> executeGradleTaskInternal(String taskName, File tempFile, PropertyDefinition processProperty) {
        // Find the task in the current project or subprojects
        def task = findGradleTask(taskName)

        if (!task) {
            project.logger.debug("Task '${taskName}' not found in project hierarchy")
            return null
        }

        project.logger.lifecycle("✅ Found task '${taskName}' in project '${task.project.name}'")

        // Set the output parameter for the task
        def originalOutputProperty = null
        if (task.project.hasProperty('output')) {
            originalOutputProperty = task.project.property('output')
        }

        // Set the output parameter as a project property
        task.project.ext.set('output', tempFile.absolutePath)

        try {
            // Execute the task
            project.logger.lifecycle("🚀 Executing task '${taskName}'...")

            // Execute task dependencies first if any exist
            task.taskDependencies.getDependencies(task).each { depTask ->
                project.logger.debug("Executing dependency: ${depTask.name}")
                depTask.actions.each { action ->
                    action.execute(depTask)
                }
            }

            // Execute the task itself
            task.actions.each { action ->
                action.execute(task)
            }

            project.logger.lifecycle("✅ Task '${taskName}' completed successfully")

            // Read and parse the output JSON
            return readJsonOutput(tempFile, taskName)

        } catch (Exception e) {
            project.logger.warn("❌ Internal task execution failed: ${e.message}")
            throw e
        } finally {
            // Restore original property value
            if (originalOutputProperty != null) {
                task.project.ext.set('output', originalOutputProperty)
            } else {
                // Remove the property if it didn't exist before
                task.project.extensions.extraProperties.properties.remove('output')
            }
        }
    }

    /**
     * Finds a Gradle task by name in the project hierarchy.
     * Searches current project, all subprojects, and specifically the modules directory.
     */
    public Object findGradleTask(String taskName) {
        // First, check current project
        def task = project.tasks.findByName(taskName)
        if (task) {
            project.logger.debug("Found task '${taskName}' in root project")
            return task
        }

        // Check all subprojects  
        def foundTask = null
        project.allprojects { proj ->
            if (!foundTask) {
                def projTask = proj.tasks.findByName(taskName)
                if (projTask) {
                    project.logger.debug("Found task '${taskName}' in project '${proj.name}'")
                    foundTask = projTask
                }
            }
        }

        if (foundTask) {
            return foundTask
        }

        // For module-specific tasks, check modules subproject structure
        if (taskName.contains('.')) {
            def moduleProject = project.findProject(':modules')
            if (moduleProject) {
                task = moduleProject.tasks.findByName(taskName)
                if (task) {
                    project.logger.debug("Found task '${taskName}' in modules project")
                    return task
                }

                // Check individual module subprojects
                moduleProject.subprojects { subProj ->
                    if (!task) {
                        def subTask = subProj.tasks.findByName(taskName)
                        if (subTask) {
                            project.logger.debug("Found task '${taskName}' in module '${subProj.name}'")
                            task = subTask
                        }
                    }
                }
            }
        }

        return task
    }

    /**
     * Reads and parses JSON output from a task execution.
     */
    private Map<String, String> readJsonOutput(File tempFile, String taskName) {
        if (!tempFile.exists() || tempFile.size() == 0) {
            project.logger.warn("⚠️ Task ${taskName} did not create output file or file is empty")
            return [:]
        }

        try {
            def jsonText = tempFile.text.trim()
            if (!jsonText) {
                project.logger.warn("⚠️ Task ${taskName} produced empty output")
                return [:]
            }

            project.logger.debug("Task ${taskName} JSON output: ${jsonText}")

            def jsonSlurper = new JsonSlurper()
            def configJson = jsonSlurper.parseText(jsonText)

            def result = [:]
            if (configJson instanceof Map) {
                configJson.each { key, value ->
                    result[key.toString()] = value.toString()
                    project.logger.debug("Parsed configuration: ${key} = ${value}")
                }

                project.logger.lifecycle("✅ Configured ${result.size()} properties from ${taskName}")

                // Log the configured properties with sensitive data masking
                result.each { key, value ->
                    def displayValue = isSensitiveProperty(key) ? "********" : value
                    project.logger.lifecycle("  • ${key} = ${displayValue}")
                }
            } else {
                project.logger.warn("⚠️ Task ${taskName} did not output a JSON object. Output type: ${configJson.class.simpleName}")
            }

            return result

        } catch (Exception e) {
            project.logger.error("❌ Error parsing JSON output from ${taskName}: ${e.message}", e)
            project.logger.debug("Raw JSON content was: ${tempFile.text}")
            return [:]
        }
    }

    /**
     * Determines if a property key represents sensitive data.
     */
    private boolean isSensitiveProperty(String key) {
        def sensitivePatterns = ['password', 'key', 'token', 'secret', 'auth']
        return sensitivePatterns.any { pattern ->
            key.toLowerCase().contains(pattern.toLowerCase())
        }
    }

    /**
     * Processes the results of executed process properties and merges them with regular properties.
     * Process properties that were executed will have special marker values that indicate
     * the task was run and configuration values were generated.
     *
     * @param userConfiguredProperties Properties configured by the user (including process execution markers)
     * @param allProperties All available properties for context
     * @return Final map of properties to write to gradle.properties
     */
    private Map<String, String> processExecutedProcessProperties(Map<String, String> userConfiguredProperties, List<PropertyDefinition> allProperties) {
        def finalProperties = [:]

        // First, add all regular (non-process) properties
        def processExecutionResults = [:]

        userConfiguredProperties.each { key, value ->
            def property = allProperties.find { it.key == key }

            if (property?.process && value?.startsWith("EXECUTED:")) {
                // This is a process property that was already executed
                // Instead of re-executing, get the values that were already written to gradle.properties
                project.logger.debug("Process property '${key}' was already executed - using existing values from gradle.properties")

                // Get the current values from gradle.properties for all properties that might have been configured
                def currentGradleProperties = scanner.scanGradleProperties()
                def gradlePropsMap = [:]
                currentGradleProperties.each { prop ->
                    gradlePropsMap[prop.key] = prop.currentValue
                }

                // Add all properties that have values in gradle.properties
                allProperties.each { prop ->
                    if (gradlePropsMap.containsKey(prop.key) && gradlePropsMap[prop.key]) {
                        def gradleValue = gradlePropsMap[prop.key]
                        if (gradleValue && !gradleValue.trim().isEmpty()) {
                            processExecutionResults[prop.key] = gradleValue
                        }
                    }
                }
            } else if (!property?.process) {
                // Regular property - add to final properties
                finalProperties[key] = value
            }
            // Process properties with non-execution values (skipped or current values) are not added to final config
        }

        // Add all process execution results
        finalProperties.putAll(processExecutionResults)

        project.logger.debug("Process execution results: ${processExecutionResults.size()} properties")
        project.logger.debug("Regular properties: ${finalProperties.size() - processExecutionResults.size()} properties")

        return finalProperties
    }

    /**
     * Gets the current configuration state for inspection or debugging.
     *
     * @return Map containing current state information
     */
    Map<String, Object> getCurrentState() {
        return [
                projectDir         : project.projectDir.absolutePath,
                hasGradleProperties: project.file('gradle.properties').exists(),
                modulesCount       : project.file('modules').exists() ?
                        project.file('modules').listFiles()?.count { it.isDirectory() } : 0,
                timestamp          : new Date()
        ]
    }

    /**
     * Removes properties from the provided map whose value equals the configured default.
     * Uses the provided list of PropertyDefinition to determine defaultValue.
     */
    private Map<String, String> filterOutDefaults(Map<String, String> properties, List<PropertyDefinition> definitions) {
        if (!properties || properties.isEmpty()) return [:]
        if (!definitions) return properties

        def defsMap = definitions.collectEntries { [(it.key): it] }
        def result = [:]

        properties.each { k, v ->
            def propertyDef = defsMap.containsKey(k) ? defsMap[k] : null
            def defaultVal = propertyDef?.defaultValue ?: ""
            
            if (propertyDef == null) {
                // No known definition - keep the property
                result[k] = v
            } else {
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
     * Writes results for interactive setup tasks in JSON format.
     * This function is designed to be used by interactive tasks that need to output
     * their results in a structured format for the interactive setup system.
     *
     * @param project The Gradle project context
     * @param results Map of property keys to values that were configured
     * @param outputPath Optional custom output file path (uses project.output if not provided)
     * @return boolean true if results were written successfully, false otherwise
     */
    static boolean writeResultsForInteractiveSetup(Project project, Map<String, String> results, String outputPath = null) {
        // Delegate to the centralized writer in buildSrc2
        try {
            return InteractiveSetupWriter.writeResults(project, results, outputPath)
        } catch (Exception e) {
            project.logger.error("Failed to delegate interactive setup results: ${e.message}", e)
            return false
        }
    }

    /**
     * Registers a project extension closure so tasks can call project.writeResultsForInteractiveSetup(results, outputPath)
     */
    static void registerProjectExt(Project project) {
        project.ext.writeResultsForInteractiveSetup = { Map results, String outputPath = null ->
            InteractiveSetupWriter.writeResults(project, results, outputPath)
        }

        // Register DatabaseConnection creation closure on project.ext mirroring writer pattern
        project.ext.createDatabaseConnection = { boolean systemConnection = false ->
            DatabaseConnection.createAndLoad(project, systemConnection)
        }
    }
}
