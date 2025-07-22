package com.etendoerp.legacy.interactive

import org.gradle.api.Project
import org.gradle.api.logging.configuration.ConsoleOutput

/**
 * Utility class to suppress Gradle's progress bar and progress output
 * during interactive user sessions.
 * 
 * This class attempts multiple techniques to ensure progress bars
 * do not interfere with user input prompts.
 * 
 * NOTE: The most effective solution currently is to use the --console=plain
 * flag when executing the Gradle command. This suppresses the progress bar
 * that appears during buildSrc compilation before the interactive session starts.
 * 
 * Recommended usage:
 *   ./gradlew interactiveSetup -Pinteractive=true --console=plain
 * 
 * @author Etendo
 * @since 2.0.4
 */
class ProgressSuppressor {
    
    private final Project project
    private final Map<String, String> originalSystemProperties = [:]
    
    ProgressSuppressor(Project project) {
        this.project = project
    }
    
    /**
     * Applies all known techniques to suppress progress output.
     * Call this before starting interactive user input.
     */
    void suppressProgress() {
        // Store original values for restoration
        storeOriginalValues()
        
        // Apply Gradle console output configuration
        applyGradleConsoleConfig()
        
        // Apply system property configurations
        applySystemPropertyConfig()
        
        // Apply internal API configurations (if available)
        applyInternalApiConfig()
        
        project.logger.debug("Progress suppression applied")
    }
    
    /**
     * Restores original progress settings.
     * Call this after completing interactive user input.
     */
    void restoreProgress() {
        // Restore system properties
        restoreSystemProperties()
        
        project.logger.debug("Progress suppression restored")
    }
    
    private void storeOriginalValues() {
        // Store original system properties that we'll modify
        def propertiesToStore = [
            "org.gradle.console.verbose",
            "org.gradle.internal.progress.disable",
            "org.gradle.daemon.performance.enable-monitoring",
            "org.gradle.logging.console",
            "org.gradle.logging.level"
        ]
        
        propertiesToStore.each { prop ->
            originalSystemProperties[prop] = System.getProperty(prop)
        }
    }
    
    private void applyGradleConsoleConfig() {
        // Force plain console output (no progress bars or colors)
        project.gradle.startParameter.consoleOutput = ConsoleOutput.Plain
    }
    
    private void applySystemPropertyConfig() {
        // Disable various progress and verbose output mechanisms
        System.setProperty("org.gradle.console.verbose", "false")
        System.setProperty("org.gradle.internal.progress.disable", "true")
        System.setProperty("org.gradle.daemon.performance.enable-monitoring", "false")
        System.setProperty("org.gradle.logging.console", "plain")
        System.setProperty("org.gradle.logging.level", "lifecycle")
    }
    
    private void applyInternalApiConfig() {
        try {
            // Try to access and disable the progress logger factory
            def progressLoggerFactory = project.gradle.services.get(
                org.gradle.internal.logging.progress.ProgressLoggerFactory
            )
            
            // Try different approaches to disable progress logging
            if (progressLoggerFactory.hasProperty('enabled')) {
                progressLoggerFactory.enabled = false
            }
            
            // Try to access build progress logger if available
            def buildProgressLogger = project.gradle.services.find { service ->
                service.class.simpleName.contains('Progress')
            }
            
            if (buildProgressLogger?.hasProperty('enabled')) {
                buildProgressLogger.enabled = false
            }
            
        } catch (Exception e) {
            // Internal APIs are not guaranteed to be stable, so we catch any errors
            project.logger.debug("Internal API progress suppression not available: ${e.message}")
        }
    }
    
    private void restoreSystemProperties() {
        originalSystemProperties.each { prop, value ->
            if (value != null) {
                System.setProperty(prop, value)
            } else {
                System.clearProperty(prop)
            }
        }
        originalSystemProperties.clear()
    }
}
