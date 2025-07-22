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
 * - Managing user confirmation workflows
 * 
 * The class supports three main configuration modes:
 * 1. Default configuration (use existing/default values)
 * 2. Group configuration (configure specific groups or all)
 * 3. Exit without saving
 * 
 * @author Etendo Interactive Setup Team
 * @since 2.0.4
 */
class UserInteraction {
    
    private final Project project
    private final Scanner scanner
    private final Console console
    
    /**
     * Creates a new UserInteraction handler.
     * 
     * @param project The Gradle project context
     */
    UserInteraction(Project project) {
        this.project = project
        this.scanner = new Scanner(System.in)
        this.console = System.console()
    }
    
    /**
     * Shows the main configuration menu and handles user selection.
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
        
        while (true) {
            displayMainMenu(availableGroups)
            
            print "\n🎯 Select an option: "
            System.out.flush()
            String input = scanner.nextLine().trim()
            
            // Handle menu selection
            def result = handleMenuSelection(input, properties, groupedProperties, availableGroups)
            if (result != null) {
                return result
            }
            // If result is null, continue the loop to show menu again
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
     * @return Map of property keys to user-provided values
     */
    Map<String, String> collectUserInput(List<PropertyDefinition> properties) {
        if (!properties) {
            return [:]
        }
        
        def result = [:]
        def groupedProperties = groupPropertiesByCategory(properties)
        
        groupedProperties.each { group, props ->
            if (props.isEmpty()) return
            
            displayGroupHeader(group, props.size())
            
            props.each { prop ->
                try {
                    String value = promptForProperty(prop)
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
     * @return The user-provided value, or the default/current value if user pressed Enter
     */
    private String promptForProperty(PropertyDefinition prop) {
        // Display the property information and current value
        println "🔧 Property: ${prop.key}"
        
        // Add documentation if available
        if (prop.documentation && !prop.documentation.trim().isEmpty()) {
            println "   ℹ️  ${prop.documentation}"
        }
        
        // Add current/default value if available
        def displayValue = prop.getDisplayValue()
        if (displayValue && !displayValue.trim().isEmpty()) {
            // Mask sensitive values in the prompt
            def maskedValue = prop.sensitive ? prop.maskValue(displayValue) : displayValue
            println "   Current value: ${maskedValue}"
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
        
        // Return user input if provided, otherwise return the current/default value
        if (input.isEmpty()) {
            return prop.getDisplayValue()
        } else {
            return input
        }
    }
    
    /**
     * Displays configuration summary and asks for user confirmation.
     * 
     * @param configuredProperties The properties configured by the user
     * @param allProperties All available properties for context
     * @return true if user confirms, false if user cancels
     */
    boolean confirmConfiguration(Map<String, String> configuredProperties, 
                                List<PropertyDefinition> allProperties) {
        
        project.logger.quiet("\n" + "=" * 60)
        displayConfigurationSummary(configuredProperties, allProperties)
        
        while (true) {
            print "\n✅ Confirm configuration? (Y/n): "
            System.out.flush() // Ensure the prompt appears immediately
            String response = scanner.nextLine().trim().toLowerCase()
            
            if (response.isEmpty() || response == 'y' || response == 'yes' || response == 's' || response == 'si') {
                return true
            } else if (response == 'n' || response == 'no') {
                return false
            } else {
                println "❌ Please respond 'Y' to confirm or 'N' to cancel."
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
            println "❌ No properties configured."
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
        println "2️⃣  Group configuration:"
        println "   📦 a. all - Configure all groups"
        
        // Show available groups with letters
        def letters = ['b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z']
        availableGroups.eachWithIndex { group, index ->
            if (index < letters.size()) {
                println "   📋 ${letters[index]}. ${group}"
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
     * @return Map of configured properties, empty map for default config, or null to continue menu loop
     */
    private Map<String, String> handleMenuSelection(String input, List<PropertyDefinition> properties, 
                                                   Map<String, List<PropertyDefinition>> groupedProperties, 
                                                   List<String> availableGroups) {
        input = input.toLowerCase().trim()
        
        switch (input) {
            case '1':
                // Default configuration - return empty map (use defaults)
                println "✅ Using default configuration..."
                return [:]
                
            case '2':
                // Group configuration - show all groups
                return collectUserInput(properties)
                
            case '3':
                // Exit without saving
                println "🚪 Exiting without saving changes..."
                return null
                
            case 'a':
                // Configure all groups
                return collectUserInput(properties)
                
            default:
                // Check if it's a specific group letter
                def letters = ['b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z']
                def letterIndex = letters.indexOf(input)
                
                if (letterIndex >= 0 && letterIndex < availableGroups.size()) {
                    def selectedGroup = availableGroups[letterIndex]
                    def groupProperties = groupedProperties[selectedGroup]
                    
                    if (groupProperties && !groupProperties.isEmpty()) {
                        println "🎯 Configuring group: ${selectedGroup}"
                        return collectUserInputForGroup(selectedGroup, groupProperties)
                    } else {
                        println "❌ No properties available for group: ${selectedGroup}"
                        return null // Continue menu loop
                    }
                } else {
                    println "❌ Invalid option: '${input}'. Please select a valid number (1-3) or letter (a-z)."
                    return null // Continue menu loop
                }
        }
    }
    
    /**
     * Collects user input for a specific group of properties.
     * 
     * @param groupName The name of the group being configured
     * @param properties List of PropertyDefinition objects in this group
     * @return Map of property keys to user-provided values
     */
    private Map<String, String> collectUserInputForGroup(String groupName, List<PropertyDefinition> properties) {
        if (!properties || properties.isEmpty()) {
            return [:]
        }
        
        def result = [:]
        
        displayGroupHeader(groupName, properties.size())
        
        properties.each { prop ->
            try {
                String value = promptForProperty(prop)
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
        
        return result
    }

    /**
     * Groups properties by their category/group for organized display.
     * 
     * @param properties List of properties to group
     * @return Map of group names to lists of properties
     */
    private Map<String, List<PropertyDefinition>> groupPropertiesByCategory(List<PropertyDefinition> properties) {
        def grouped = properties.groupBy { prop -> 
            prop.group ?: "General" 
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
- You can cancel at any time during the final confirmation

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
