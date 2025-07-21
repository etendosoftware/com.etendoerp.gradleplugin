package com.etendoerp.legacy.interactive

import com.etendoerp.legacy.interactive.model.PropertyDefinition
import com.etendoerp.legacy.interactive.utils.SecurityUtils
import org.gradle.api.Project

/**
 * Handles all user interaction aspects of the interactive setup process.
 * 
 * This class is responsible for:
 * - Prompting users for property values
 * - Handling sensitive input (passwords, tokens) securely
 * - Displaying configuration summaries
 * - Managing user confirmation workflows
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
     * Collects user input for all provided properties.
     * 
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
        // Display the prompt text
        print prop.getPromptText()
        
        String input
        if (prop.sensitive && console != null) {
            // Use console for secure password input
            char[] passwordChars = console.readPassword()
            input = passwordChars ? new String(passwordChars) : ""
            // Clear the password from memory
            if (passwordChars) {
                Arrays.fill(passwordChars, ' ' as char)
            }
        } else {
            if (prop.sensitive && console == null) {
                // Warning about non-hidden input for sensitive properties
                print "[WARNING: Input will be visible] "
            }
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
        
        displayConfigurationSummary(configuredProperties, allProperties)
        
        while (true) {
            print "\n¿Confirmar configuración? (S/n): "
            String response = scanner.nextLine().trim().toLowerCase()
            
            if (response.isEmpty() || response == 's' || response == 'si' || response == 'yes' || response == 'y') {
                return true
            } else if (response == 'n' || response == 'no') {
                return false
            } else {
                println "Por favor responda 'S' para confirmar o 'N' para cancelar."
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
        
        println "=== Resumen de Configuración ==="
        
        if (configuredProperties.isEmpty()) {
            println "No hay propiedades configuradas."
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
            println "\n${group}:"
            properties.sort { it.key }.each { propInfo ->
                def displayValue = propInfo.sensitive ? 
                    SecurityUtils.maskValue(propInfo.value) : 
                    propInfo.value
                println "  ${propInfo.key} = ${displayValue}"
            }
        }
        
        // Show statistics
        def totalProps = configuredProperties.size()
        def sensitiveProps = configuredProperties.count { key, value ->
            def prop = allProperties.find { it.key == key }
            return prop?.sensitive ?: SecurityUtils.isSensitive(key, prop)
        }
        
        println "\nTotal: ${totalProps} propiedades configuradas"
        if (sensitiveProps > 0) {
            println "Incluyendo ${sensitiveProps} propiedades sensibles (se mostrarán enmascaradas)"
        }
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
        def header = "=== ${groupName} ==="
        println header
        
        if (propertyCount > 1) {
            println "(${propertyCount} propiedades)"
        }
        println()
    }
    
    /**
     * Displays help information about using the interactive setup.
     */
    void displayHelp() {
        println """
=== Ayuda del Setup Interactivo ===

Uso:
- Presiona Enter para mantener el valor actual/por defecto mostrado entre paréntesis
- Para propiedades sensibles (contraseñas, tokens), la entrada estará oculta
- Puedes cancelar en cualquier momento durante la confirmación final

Tipos de propiedades:
- Generales: Configuración básica del proyecto
- Base de Datos: Configuración de conexión a la base de datos  
- Seguridad: Credenciales y tokens de acceso
- Rutas: Directorios y ubicaciones de archivos

Propiedades sensibles:
Las propiedades que contienen información sensible (contraseñas, tokens, claves)
se detectan automáticamente y se muestran enmascaradas en los resúmenes.

Para más información, consulta la documentación del proyecto.
"""
    }
    
    /**
     * Asks user if they want to see help information.
     * 
     * @return true if user wants help
     */
    boolean askForHelp() {
        print "¿Desea ver la ayuda del setup interactivo? (s/N): "
        String response = scanner.nextLine().trim().toLowerCase()
        return response == 's' || response == 'si' || response == 'yes' || response == 'y'
    }
    
    /**
     * Handles cleanup when the interaction is completed or cancelled.
     */
    void cleanup() {
        // Close scanner if we created it
        // Note: Don't close System.in as it may be used by other parts of Gradle
    }
}
