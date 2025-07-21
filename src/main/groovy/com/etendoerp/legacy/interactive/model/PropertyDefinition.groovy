package com.etendoerp.legacy.interactive.model

/**
 * Model class representing a property configuration with its metadata.
 * This class encapsulates all information needed to prompt the user for
 * property configuration including documentation, default values, and sensitivity.
 * 
 * @author Etendo Interactive Setup Team
 * @since 2.0.4
 */
class PropertyDefinition {
    
    /** The property key/name */
    String key
    
    /** Current value from gradle.properties if exists */
    String currentValue
    
    /** Default value from documentation if specified */
    String defaultValue
    
    /** Documentation/help text for this property */
    String documentation
    
    /** Group/category for organizing properties in the UI */
    String group
    
    /** Whether this property contains sensitive information (passwords, tokens, etc.) */
    boolean sensitive = false
    
    /** Whether this property is required for the application to function */
    boolean required = false
    
    /**
     * Gets the value to display to the user (current or default).
     * Prioritizes current value over default value.
     * 
     * @return The display value or empty string if neither current nor default exists
     */
    String getDisplayValue() {
        return currentValue ?: defaultValue ?: ""
    }
    
    /**
     * Generates the prompt text to display to the user when asking for input.
     * Includes documentation, property key, and current/default value in parentheses.
     * 
     * @return Formatted prompt text ready for console display
     */
    String getPromptText() {
        def prompt = ""
        
        // Add documentation if available
        if (documentation && !documentation.trim().isEmpty()) {
            prompt += "${documentation}\n"
        }
        
        // Add property key
        prompt += "${key}"
        
        // Add current/default value in parentheses if available
        def displayValue = getDisplayValue()
        if (displayValue && !displayValue.trim().isEmpty()) {
            // Mask sensitive values in the prompt
            def maskedValue = sensitive ? maskValue(displayValue) : displayValue
            prompt += " (${maskedValue})"
        }
        
        prompt += ": "
        return prompt
    }
    
    /**
     * Checks if this property has any configured value (current or default).
     * 
     * @return true if the property has a current or default value
     */
    boolean hasValue() {
        def displayValue = getDisplayValue()
        return displayValue && !displayValue.trim().isEmpty()
    }
    
    /**
     * Masks a sensitive value for display purposes.
     * 
     * @param value The value to mask
     * @return Masked representation of the value
     */
    private String maskValue(String value) {
        if (!value || value.trim().isEmpty()) {
            return ""
        }
        return "*".repeat(Math.min(value.length(), 8))
    }
    
    @Override
    String toString() {
        return "PropertyDefinition{" +
                "key='${key}', " +
                "group='${group}', " +
                "sensitive=${sensitive}, " +
                "required=${required}, " +
                "hasValue=${hasValue()}" +
                "}"
    }
    
    @Override
    boolean equals(Object obj) {
        if (this.is(obj)) return true
        if (!(obj instanceof PropertyDefinition)) return false
        
        PropertyDefinition other = (PropertyDefinition) obj
        return key == other.key
    }
    
    @Override
    int hashCode() {
        return key?.hashCode() ?: 0
    }
}
