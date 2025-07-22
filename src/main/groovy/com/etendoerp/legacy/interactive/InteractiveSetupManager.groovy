package com.etendoerp.legacy.interactive

import com.etendoerp.legacy.interactive.model.PropertyDefinition
import org.gradle.api.Project
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
    }
    
    /**
     * Executes the traditional setup task programmatically.
     * 
     * @throws RuntimeException if the setup execution fails
     */
    private void executeTraditionalSetup() {
        project.logger.quiet("")
        project.logger.quiet("🚀 **Executing Traditional Setup**")
        project.logger.quiet("="*50)
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
            project.logger.error("="*50)
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
            
            project.logger.quiet("DEBUG: Configured properties map size: ${configuredProperties.size()}")
            project.logger.quiet("DEBUG: Configured properties: ${configuredProperties}")
            
            // If empty map from default configuration, handle it differently
            if (configuredProperties.isEmpty()) {
                project.logger.lifecycle("Using default configuration values.")
                project.logger.lifecycle("✅ Configuration completed successfully!")
                project.logger.lifecycle("Default settings are already configured.")
                return
            }
            
            // Step 3: Show configuration summary and confirm
            project.logger.quiet("DEBUG: About to call confirmConfiguration")
            boolean confirmed = ui.confirmConfiguration(configuredProperties, properties)
            project.logger.quiet("DEBUG: Confirmation result: ${confirmed}")
            
            if (!confirmed) {
                project.logger.lifecycle("Configuration cancelled by user.")
                throw new StopExecutionException("Interactive setup cancelled by user")
            }
            
            // Step 4: Write configuration to gradle.properties
            project.logger.lifecycle("")
            project.logger.lifecycle("Writing configuration to gradle.properties...")
            
            writer.writeProperties(configuredProperties)
            
            project.logger.lifecycle("✅ Configuration completed successfully!")
            project.logger.lifecycle("Your settings have been saved to gradle.properties")
            project.logger.lifecycle("")
            
            // Log summary of what was configured
            logConfigurationSummary(configuredProperties, properties)
            
            // Step 5: Automatic setup execution (ETP-1960-03)
            // No additional confirmation needed - user already confirmed in the summary
            project.logger.lifecycle("")
            project.logger.lifecycle("🔗 **Automatic Setup Integration** (ETP-1960-03)")
            project.logger.lifecycle("="*60)
            project.logger.lifecycle("Proceeding with automatic traditional setup execution...")
            
            try {
                executeTraditionalSetup()
                
                project.logger.lifecycle("")
                project.logger.lifecycle("🎉 **Complete End-to-End Setup Finished!**")
                project.logger.lifecycle("="*60)
                project.logger.lifecycle("✅ Interactive configuration: COMPLETED")
                project.logger.lifecycle("✅ Traditional setup: COMPLETED")
                project.logger.lifecycle("✅ Environment ready for development")
                project.logger.lifecycle("")
                project.logger.lifecycle("Your Etendo environment is now fully configured and ready to use!")
                
            } catch (Exception setupError) {
                project.logger.error("")
                project.logger.error("⚠️  **Partial Setup Completion**")
                project.logger.error("="*60)
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
            def group = prop?.group ?: "General"
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
                project.logger.warn("  - ${prop.key} (${prop.group})")
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
     * Gets the current configuration state for inspection or debugging.
     * 
     * @return Map containing current state information
     */
    Map<String, Object> getCurrentState() {
        return [
            projectDir: project.projectDir.absolutePath,
            hasGradleProperties: project.file('gradle.properties').exists(),
            modulesCount: project.file('modules').exists() ? 
                project.file('modules').listFiles()?.count { it.isDirectory() } : 0,
            timestamp: new Date()
        ]
    }
}
