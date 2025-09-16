package com.etendoerp.legacy.interactive

import com.etendoerp.legacy.interactive.model.PropertyDefinition
import com.etendoerp.legacy.interactive.utils.SecurityUtils
import org.gradle.api.Project

/**
 * Handles all user interaction aspects of the interactive setup process.
 * 
 * This class is responsible for:
 * - Displaying the main configuration menu with multiple options
 * - Handling menu navigation and option selection
 * - Prompting users for property values (individual or by groups)
 * - Handling sensitive input (passwords, tokens) securely
 * - Displaying configuration summaries
 * - Managing user confirmation workflows with persistent state
 * - Preserving user input across menu interactions and confirmation cycles
 * 
 * The class supports three main configuration modes:
 * 1. Default configuration (shows summary and requires confirmation)
 * 2. Group configuration (configure specific groups or all)
 * 3. Exit without saving
 * 
 * Key behavioral improvements:
 * - Default configuration now displays a complete summary before confirmation
 * - User input is preserved across menu sessions when confirmation is cancelled
 * - All configuration paths follow consistent confirmation workflow
 * - Graceful handling of invalid menu selections with user-friendly feedback
 * - Session values are prioritized over default values in property prompts
 * - Clear indication when showing session values vs default values to users
 * - Explicit confirmation required - no accidental confirmations via Enter key
 * 
 * @author Etendo Interactive Setup Team
 * @since 2.0.4
 * @version 2.0.7 - Enhanced confirmation safety with explicit user response requirement
 */
class UserInteraction {
    
    private final Project project
    private Scanner scanner
    private Console console
    private InteractiveSetupManager setupManager  // Reference for executing process properties
    private List<PropertyDefinition> allProperties  // Reference to all properties for updating after process execution
    
    /**
     * Creates a new UserInteraction handler.
     * 
     * @param project The Gradle project context
     */
    UserInteraction(Project project) {
        this.project = project
        this.scanner = new Scanner(System.in)
        this.console = System.console()
        this.setupManager = null  // Will be set later to avoid circular dependency
    }
    
    /**
     * Sets the setup manager reference for executing process properties.
     * This is called after construction to avoid circular dependencies.
     */
    void setSetupManager(InteractiveSetupManager setupManager) {
        this.setupManager = setupManager
    }
    
    /**
     * Sets the scanner for testing purposes.
     * Package-private method to allow test injection.
     */
    void setScanner(Scanner scanner) {
        this.scanner = scanner
    }
    
    /**
     * Sets the console for testing purposes.
     * Package-private method to allow test injection.
     */
    void setConsole(Console console) {
        this.console = console
    }
    
    /**
     * Shows the main configuration menu and handles user selection.
     * Manages persistent state of configured properties throughout the session.
     * 
     * @param properties List of PropertyDefinition objects available for configuration
     * @return Map of property keys to user-provided values, or null if user exits
     */
    Map<String, String> showMainMenu(List<PropertyDefinition> properties) {
        if (!properties) {
            return [:]
        }

        def groupedProperties = groupPropertiesByCategory(properties)
        def availableGroups = groupedProperties.keySet().sort { a, b ->
            if (a == "General") return -1
            if (b == "General") return 1
            return a.compareTo(b)
        }
        
        // Persistent state for configured properties across menu interactions
        def configuredProperties = [:]
        
        while (true) {
            displayMainMenu(availableGroups)
            
            print "\n🎯 Select an option: "
            System.out.flush()
            String input = scanner.nextLine().trim().toLowerCase()
            
            // Handle menu selection and get new properties
            def newProperties = handleMenuSelection(input, properties, groupedProperties, availableGroups, configuredProperties)
            
            if (newProperties == null) {
                // User selected exit - return null
                return null
            } else if (newProperties instanceof Map && newProperties.containsKey('__CONTINUE_MENU__')) {
                // Special marker to continue menu loop
                // But first, extract and preserve any configured properties
                newProperties.remove('__CONTINUE_MENU__')  // Remove the special marker
                if (!newProperties.isEmpty()) {
                    configuredProperties.putAll(newProperties)  // Preserve the configured properties
                    println "💾 Saved ${newProperties.size()} configured properties. Total saved: ${configuredProperties.size()}"
                }
                continue
            } else {
                // Merge new properties with existing configured properties
                if (newProperties instanceof Map) {
                    configuredProperties.putAll(newProperties)
                }
                
                // Show configuration summary and ask for confirmation
                boolean confirmed = confirmConfiguration(configuredProperties, properties)
                
                if (confirmed) {
                    // User confirmed - return the configured properties
                    return configuredProperties
                } else {
                    // User cancelled - continue menu loop with preserved state
                    println "\n🔄 Returning to main menu. Your entered values have been preserved."
                    continue
                }
            }
        }
    }
    
    /**
     * Collects user input for all provided properties.
     * 
     * This method is used when the user selects "all groups" configuration.
     * Properties are grouped by category and presented to the user
     * in an organized manner. Sensitive properties use hidden input.
     * 
     * @param properties List of PropertyDefinition objects to configure
     * @param sessionValues Map of values already configured in this session (optional)
     * @return Map of property keys to user-provided values
     */
    Map<String, String> collectUserInput(List<PropertyDefinition> properties, Map<String, String> sessionValues = [:]) {
        if (!properties) {
            return [:]
        }
        
        // Store reference to all properties for use in process property updates
        this.allProperties = properties
        
        def result = [:] 
        def groupedProperties = groupPropertiesByCategory(properties)
        
        groupedProperties.each { group, props ->
            if (props.isEmpty()) return
            
            displayGroupHeader(group, props.size())
            
            props.each { prop ->
                try {
                    String value = promptForProperty(prop, sessionValues)
                    if (value != null) {
                        result[prop.key] = value
                    }
                    // Add spacing between properties for better readability
                    println ""
                } catch (Exception e) {
                    project.logger.warn("Error collecting input for ${prop.key}: ${e.message}")
                    // Continue with other properties
                }
            }
            
            println() // Add spacing between groups
        }
        
        return result
    }
    
    /**
     * Prompts the user for a specific property value.
     * 
     * @param prop The PropertyDefinition to prompt for
     * @param sessionValues Map of values already configured in this session (optional)
     * @return The user-provided value, or the current/default value if user pressed Enter
     */
    private String promptForProperty(PropertyDefinition prop, Map<String, String> sessionValues = [:]) {
        // Handle process properties differently
        if (prop.process) {
            return promptForProcessProperty(prop, sessionValues)
        }
        
        // Display the property information and current value
        println "🔧 Property: ${prop.key}"
        
        // Add documentation if available
        if (prop.documentation && !prop.documentation.trim().isEmpty()) {
            println "   ℹ️  ${prop.documentation}"
        }
        
        // Add help text if available
        if (prop.help && !prop.help.trim().isEmpty()) {
            println "   💡 ${prop.help}"
        }
        
        // Determine the current value to display - prioritize session value over default
        def currentValue = sessionValues?.get(prop.key) ?: prop.getDisplayValue()
        def isSessionValue = sessionValues?.containsKey(prop.key) ?: false
        
        // Add current/default value if available
        if (currentValue && !currentValue.trim().isEmpty()) {
            // Mask sensitive values in the prompt
            def maskedValue = prop.sensitive ? prop.maskValue(currentValue) : currentValue
            def valueLabel = isSessionValue ? "Current value (from this session)" : "Current value"
            println "   ${valueLabel}: ${maskedValue}"
        }
        
        String input
        if (prop.sensitive && console != null) {
            // For sensitive properties with console available
            print "🔐 New value (hidden): "
            System.out.flush() // Force the prompt to appear immediately
            char[] passwordChars = console.readPassword()
            input = passwordChars ? new String(passwordChars) : ""
            // Clear the password from memory
            if (passwordChars) {
                Arrays.fill(passwordChars, ' ' as char)
            }
        } else {
            if (prop.sensitive && console == null) {
                // Warning about non-hidden input for sensitive properties
                println "⚠️  WARNING: Input will be visible"
            }
            // Show the input prompt and flush to ensure it appears
            print "✏️  New value: "
            System.out.flush() // This ensures the prompt shows before waiting for input
            input = scanner.nextLine()
        }
        
        // Trim whitespace
        input = input?.trim() ?: ""
        
        // Return user input if provided, otherwise return the current value (prioritizing session value)
        if (input.isEmpty()) {
            return currentValue ?: prop.getDisplayValue()
        } else {
            return input
        }
    }
    
    /**
     * Prompts the user for a process property - offers to execute it.
     * 
     * @param prop The process PropertyDefinition
     * @param sessionValues Map of values already configured in this session
     * @return The result of the process or the current value
     */
    private String promptForProcessProperty(PropertyDefinition prop, Map<String, String> sessionValues) {
        // Display the process property information
        println "⚙️ Configuration task available: ${prop.key}"
        
        // Add documentation if available
        if (prop.documentation && !prop.documentation.trim().isEmpty()) {
            println "   ℹ️  ${prop.documentation}"
        }
        
        // Add help text if available
        if (prop.help && !prop.help.trim().isEmpty()) {
            println "   💡 ${prop.help}"
        }
        
        // Check if we have a session value (from previous execution in this session)
        def sessionValue = sessionValues?.get(prop.key)
        if (sessionValue) {
            println "   ✅ Already executed in this session: ${sessionValue}"
        }
        
        // Determine current value
        def currentValue = sessionValue ?: prop.getDisplayValue()

        
        while (true) {
            print "\n🔄 Execute this process? (y/N): "
            System.out.flush()
            String input = scanner.nextLine().trim().toLowerCase()
            
            if (input == 'y' || input == 'yes') {
                // Execute the process property
                if (setupManager) {
                    try {
                        println "\n🚀 Executing process property..."
                        def processResults = setupManager.executeProcessProperty(prop)
                        
                        if (!processResults.isEmpty()) {
                            println "✅ Process completed successfully!"
                            println "📊 Generated ${processResults.size()} configuration values:"
                            processResults.each { key, value ->
                                println "   • ${key} = ${value}"
                            }
                            
                            // Update the properties with the new values after process execution
                            if (allProperties) {
                                setupManager.updatePropertiesAfterProcessExecution(allProperties, processResults)
                                println "🔄 Updated current values - these will be shown in subsequent property questions."
                            }
                            
                            // For process properties, we return a special marker to indicate execution
                            // The actual configured values are handled separately
                            return "EXECUTED:${processResults.size()}_properties_configured"
                        } else {
                            println "⚠️  Process completed but no configuration values were generated."
                            return currentValue ?: ""
                        }
                    } catch (Exception e) {
                        println "❌ Process execution failed: ${e.message}"
                        return currentValue ?: ""
                    }
                } else {
                    println "❌ Cannot execute process - setup manager not available"
                    return currentValue ?: ""
                }
            } else if (input == 'n' || input == 'no') {
                // Skip execution
                println "⏭️  Skipping process execution"
                return currentValue ?: ""
            } else if (input.isEmpty()) {
                // Keep current value
                return currentValue ?: ""
            } else {
                println "❌ Please respond 'Y' to execute, 'N' to skip, or Enter to keep current value."
            }
        }
    }
    
    /**
     * Displays configuration summary and asks for user confirmation.
     * Requires explicit confirmation - no default option is provided.
     * 
     * @param configuredProperties The properties configured by the user
     * @param allProperties All available properties for context
     * @return true if user explicitly confirms with 'Y', false if user cancels with 'N'
     */
    boolean confirmConfiguration(Map<String, String> configuredProperties, 
                                List<PropertyDefinition> allProperties) {
        
        project.logger.quiet("\n" + "=" * 60)
        displayConfigurationSummary(configuredProperties, allProperties)
        
        while (true) {
            print "\n✅ Confirm configuration? (y/n): "
            System.out.flush() // Ensure the prompt appears immediately
            String response = scanner.nextLine().trim().toLowerCase()
            
            if (response == 'y' || response == 'yes' || response == 's' || response == 'si') {
                return true
            } else if (response == 'n' || response == 'no') {
                return false
            } else {
                println "❌ Please respond 'Y' to confirm or 'N' to cancel. Empty responses are not accepted."
            }
        }
    }
    
    /**
     * Displays the configuration summary organized by groups.
     * 
     * @param configuredProperties The properties to display
     * @param allProperties All properties for metadata lookup
     */
    private void displayConfigurationSummary(Map<String, String> configuredProperties,
                                            List<PropertyDefinition> allProperties) {
        
        println "📊 Configuration Summary"
        println "=" * 60
        
        if (configuredProperties.isEmpty()) {
            println "ℹ️  No custom properties configured - using current/default values."
            println "   All properties will maintain their existing values."
            return
        }
        
        // Group configured properties for display
        def groupedForDisplay = [:]
        configuredProperties.each { key, value ->
            def prop = allProperties.find { it.key == key }
            def group = prop?.group ?: "General"
            
            if (!groupedForDisplay[group]) {
                groupedForDisplay[group] = []
            }
            
            groupedForDisplay[group] << [
                key: key,
                value: value,
                sensitive: prop?.sensitive ?: SecurityUtils.isSensitive(key, prop)
            ]
        }
        
        // Display each group
        groupedForDisplay.sort().each { group, properties ->
            println "\n📋 ${group}:"
            properties.sort { it.key }.each { propInfo ->
                def displayValue = propInfo.sensitive ? 
                    SecurityUtils.maskValue(propInfo.value) : 
                    propInfo.value
                println "   🔧 ${propInfo.key} = ${displayValue}"
            }
        }
        
        // Show statistics
        def totalProps = configuredProperties.size()
        def sensitiveProps = configuredProperties.count { key, value ->
            def prop = allProperties.find { it.key == key }
            return prop?.sensitive ?: SecurityUtils.isSensitive(key, prop)
        }
        
        println "\n📊 Total: ${totalProps} properties configured"
        if (sensitiveProps > 0) {
            println "🔐 Including ${sensitiveProps} sensitive properties (shown masked)"
        }
    }
    
    /**
     * Displays the main configuration menu.
     * 
     * @param availableGroups List of available property groups
     */
    private void displayMainMenu(List<String> availableGroups) {
        println "\n🎛️  Interactive Setup - Main Menu"
        println "=" * 60
        println ""
        println "📋 Choose configuration option:"
        println ""
        println "1️⃣  Default configuration (use current/default values)"
        println "2️⃣  Group configuration (you can configure multiple groups):"
        println "   📦 a. all - Configure all groups"
        
        // Show available groups with letters (max 25 groups to prevent index out of bounds)
        def letters = ['b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z']
        def maxGroupsToShow = Math.min(availableGroups.size(), letters.size())
        
        availableGroups.eachWithIndex { group, index ->
            if (index < maxGroupsToShow) {
                println "   📋 ${letters[index]}. ${group}"
            } else {
                // Show remaining groups with numbers if we exceed letter capacity
                println "   📋 ${index + 2}. ${group} (use number ${index + 2})"
            }
        }
        
        println "3️⃣  Exit without saving"
        println ""
    }
    
    /**
     * Handles the user's menu selection.
     * 
     * @param input User input string
     * @param properties All available properties
     * @param groupedProperties Properties grouped by category
     * @param availableGroups List of available group names
     * @param configuredProperties Currently configured properties from session state
     * @return Map of newly configured properties, null for exit, or special marker map to continue menu loop
     */
    private Map<String, String> handleMenuSelection(String input, List<PropertyDefinition> properties, 
                                                   Map<String, List<PropertyDefinition>> groupedProperties, 
                                                   List<String> availableGroups, Map<String, String> configuredProperties) {
        input = input.toLowerCase().trim()
        
        switch (input) {
            case '1':
                // Default configuration - prepare default values for all properties
                println "✅ Preparing default configuration..."
                def defaultProperties = [:]
                properties.each { prop ->
                    def defaultValue = prop.getDisplayValue()
                    if (defaultValue && !defaultValue.trim().isEmpty()) {
                        defaultProperties[prop.key] = defaultValue
                    }
                }
                return defaultProperties
                
            case '2':
                // Group configuration - show all groups
                return collectUserInput(properties, configuredProperties)
                
            case '3':
                // Exit without saving
                println "🚪 Exiting without saving changes..."
                return null
                
            case 'a':
                // Configure all groups
                return collectUserInput(properties, configuredProperties)
                
            default:
                // Check if it's a specific group letter
                def letters = ['b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z']
                def letterIndex = letters.indexOf(input)
                
                if (letterIndex >= 0 && letterIndex < availableGroups.size()) {
                    def selectedGroup = availableGroups[letterIndex]
                    def groupProperties = groupedProperties[selectedGroup]
                    
                    if (groupProperties && !groupProperties.isEmpty()) {
                        println "🎯 Configuring group: ${selectedGroup}"
                        return collectUserInputForGroup(selectedGroup, groupProperties, configuredProperties)
                    } else {
                        println "❌ No properties available for group: ${selectedGroup}"
                        return ['__CONTINUE_MENU__': true] // Continue menu loop
                    }
                } else {
                    // Check if it's a numeric input for groups beyond letter capacity
                    try {
                        def numericInput = Integer.parseInt(input)
                        def groupIndex = numericInput - 2 // Adjust for 0-based index (options 1,2 are taken)
                        
                        if (groupIndex >= 0 && groupIndex < availableGroups.size()) {
                            def selectedGroup = availableGroups[groupIndex]
                            def groupProperties = groupedProperties[selectedGroup]
                            
                            if (groupProperties && !groupProperties.isEmpty()) {
                                println "🎯 Configuring group: ${selectedGroup}"
                                return collectUserInputForGroup(selectedGroup, groupProperties, configuredProperties)
                            } else {
                                println "❌ No properties available for group: ${selectedGroup}"
                                return ['__CONTINUE_MENU__': true] // Continue menu loop
                            }
                        } else {
                            println "❌ Invalid option: '${input}'. Please select a valid number (1-3), letter (a-z), or number for specific groups."
                            return ['__CONTINUE_MENU__': true] // Continue menu loop
                        }
                    } catch (NumberFormatException e) {
                        println "❌ Invalid option: '${input}'. Please select a valid number (1-3) or letter (a-z)."
                        return ['__CONTINUE_MENU__': true] // Continue menu loop
                    }
                }
        }
    }
    
    /**
     * Collects user input for a specific group of properties.
     * 
     * @param groupName The name of the group being configured
     * @param properties List of PropertyDefinition objects in this group
     * @param sessionValues Map of values already configured in this session (optional)
     * @return Map of property keys to user-provided values
     */
    private Map<String, String> collectUserInputForGroup(String groupName, List<PropertyDefinition> properties, Map<String, String> sessionValues = [:]) {
        if (!properties || properties.isEmpty()) {
            return [:]
        }
        
        // Store reference to all properties for use in process property updates
        // Note: This might be just the group properties, but process properties should update the main list
        if (this.allProperties == null) {
            this.allProperties = properties
        }
        
        def result = [:]
        
        displayGroupHeader(groupName, properties.size())
        
        properties.each { prop ->
            try {
                String value = promptForProperty(prop, sessionValues)
                if (value != null) {
                    result[prop.key] = value
                }
                // Add spacing between properties for better readability
                println ""
            } catch (Exception e) {
                project.logger.warn("Error collecting input for ${prop.key}: ${e.message}")
                // Continue with other properties
            }
        }
        
        // After configuring the group, ask user what to do next
        println ""
        println "✅ Group '${groupName}' configuration completed!"
        println "📊 Configured ${result.size()} properties in this group."
        println ""
        
        while (true) {
            print "🎯 What would you like to do next? (M=return to main Menu, C=Continue to confirmation): "
            System.out.flush()
            String nextAction = scanner.nextLine().trim().toLowerCase()
            
            if (nextAction == 'm' || nextAction == 'menu') {
                println "🔄 Returning to main menu..."
                // Return a special marker to indicate we should continue the menu loop
                // but include the configured properties
                def menuResult = ['__CONTINUE_MENU__': true]
                menuResult.putAll(result)  // Include the configured properties
                return menuResult
            } else if (nextAction == 'c' || nextAction == 'continue') {
                println "➡️ Continuing to configuration review..."
                return result
            } else {
                println "❌ Please respond 'M' to return to menu or 'C' to continue to final confirmation step."
            }
        }
        
        return result
    }

    /**
     * Groups properties by category while preserving original order within groups.
     * Process properties are sorted first within each group, maintaining their definition order.
     * 
     * @param properties List of properties to group
     * @return Map of group names to lists of properties (ordered by definition, with process properties first)
     */
    private Map<String, List<PropertyDefinition>> groupPropertiesByCategory(List<PropertyDefinition> properties) {
        def grouped = properties.groupBy { prop -> 
            prop.group ?: "General" 
        }
        
        // Sort properties within each group: process properties first (by order), then regular properties (by order)
        grouped.each { groupName, groupProperties ->
            groupProperties.sort { a, b ->
                // Process properties come first
                if (a.process && !b.process) return -1
                if (!a.process && b.process) return 1
                // Within the same type (both process or both regular), sort by original order
                return (a.order ?: 999) <=> (b.order ?: 999)
            }
        }
        
        // Sort groups, with "General" first, then alphabetically
        return grouped.sort { a, b ->
            if (a.key == "General") return -1
            if (b.key == "General") return 1
            return a.key.compareTo(b.key)
        }
    }
    
    /**
     * Displays a header for a property group.
     * 
     * @param groupName The name of the group
     * @param propertyCount Number of properties in the group
     */
    private void displayGroupHeader(String groupName, int propertyCount) {
        println "\n📋 ${groupName}"
        println "=" * 50
        println ""
    }
    
    /**
     * Displays help information about using the interactive setup.
     */
    void displayHelp() {
        println """
=== Interactive Setup Help ===

Usage:
- Press Enter to keep the current/default value shown
- For sensitive properties (passwords, tokens), input will be hidden
- Configuration requires explicit confirmation (Y/N) - no default option

Property types:
- General: Basic project configuration
- Database: Database connection configuration  
- Security: Credentials and access tokens
- Paths: Directories and file locations

Sensitive properties:
Properties containing sensitive information (passwords, tokens, keys)
are automatically detected and shown masked in summaries.

For more information, consult the project documentation.
"""
    }
    
    /**
     * Asks user if they want to see help information.
     * 
     * @return true if user wants help
     */
    boolean askForHelp() {
        print "Do you want to see the interactive setup help? (y/N): "
        String response = scanner.nextLine().trim().toLowerCase()
        return response == 'y' || response == 'yes' || response == 's' || response == 'si'
    }
    
    /**
     * Handles cleanup when the interaction is completed or cancelled.
     */
    void cleanup() {
        // Close scanner if we created it
        // Note: Don't close System.in as it may be used by other parts of Gradle
    }
}
