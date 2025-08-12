package com.etendoerp.legacy.interactive.utils

import com.etendoerp.legacy.interactive.model.PropertyDefinition
import java.util.regex.Pattern

/**
 * Utility class for handling security-related aspects of property configuration.
 * Provides methods to identify sensitive properties and mask their values for display.
 * 
 * @author Etendo Interactive Setup Team
 * @since 2.0.4
 */
class SecurityUtils {
    
    /**
     * Patterns that typically indicate sensitive property names.
     * These patterns are checked against property names to automatically
     * identify potentially sensitive information.
     */
    private static final List<Pattern> SENSITIVE_PATTERNS = [
        ~/.*password.*/,
        ~/.*secret.*/,
        ~/.*token.*/,
        ~/.*key.*/,
        ~/.*credential.*/,
        ~/.*auth.*/,
        ~/.*private.*/,
        ~/.*secure.*/,
        ~/.*pass.*/,
        ~/.*pwd.*/
    ].asImmutable()
    
    /**
     * Property names that are explicitly known to be sensitive.
     * These are checked first before pattern matching.
     */
    private static final Set<String> SENSITIVE_PROPERTY_NAMES = [
        'bbdd.password',
        'bbdd.systemPassword',
        'nexusPassword',
        'githubToken',
        'apiKey',
        'secretKey',
        'privateKey'
    ].asImmutable()
    
    /**
     * Determines if a property should be treated as sensitive based on its name
     * and explicit sensitivity markers in the PropertyDefinition.
     * 
     * @param propertyName The name of the property to check
     * @param propertyDef The PropertyDefinition containing explicit sensitivity markers
     * @return true if the property should be treated as sensitive
     */
    static boolean isSensitive(String propertyName, PropertyDefinition propertyDef = null) {
        if (!propertyName) {
            return false
        }
        
        // Check explicit sensitivity marker first
        if (propertyDef?.sensitive) {
            return true
        }
        
        // Check known sensitive property names
        if (SENSITIVE_PROPERTY_NAMES.contains(propertyName)) {
            return true
        }
        
        // Check against patterns (case-insensitive)
        String lowerPropertyName = propertyName.toLowerCase()
        return SENSITIVE_PATTERNS.any { pattern ->
            lowerPropertyName.matches(pattern)
        }
    }
    
    /**
     * Masks a sensitive value for display purposes.
     * Returns a string of asterisks with length based on the original value.
     * 
     * @param value The value to mask
     * @param maxLength Maximum length of the masked output (default: 8)
     * @return Masked representation of the value
     */
    static String maskValue(String value, int maxLength = 8) {
        if (!value || value.trim().isEmpty()) {
            return ""
        }
        
        int maskLength = Math.min(value.length(), maxLength)
        return "*" * maskLength
    }
    
    /**
     * Creates a display-safe version of a property value.
     * If the property is sensitive, returns a masked version, otherwise returns the original value.
     * 
     * @param propertyName The name of the property
     * @param value The value to potentially mask
     * @param propertyDef Optional PropertyDefinition for explicit sensitivity checking
     * @return Either the original value or a masked version if sensitive
     */
    static String getDisplaySafeValue(String propertyName, String value, PropertyDefinition propertyDef = null) {
        if (isSensitive(propertyName, propertyDef)) {
            return maskValue(value)
        }
        return value ?: ""
    }
    
    /**
     * Validates that logging and debug output excludes sensitive information.
     * This method should be used before logging property values.
     * 
     * @param propertyName The name of the property being logged
     * @param value The value being logged
     * @param propertyDef Optional PropertyDefinition for context
     * @return Safe value for logging (masked if sensitive)
     */
    static String getSafeLoggingValue(String propertyName, String value, PropertyDefinition propertyDef = null) {
        if (isSensitive(propertyName, propertyDef)) {
            return "[SENSITIVE VALUE MASKED]"
        }
        return value ?: "[EMPTY]"
    }
    
    /**
     * Checks if a property name appears to be a credential or authentication-related property.
     * This is used for additional security checks and warnings.
     * 
     * @param propertyName The property name to check
     * @return true if the property appears to be authentication-related
     */
    static boolean isCredentialProperty(String propertyName) {
        if (!propertyName) return false
        
        String lower = propertyName.toLowerCase()
        return lower.contains('user') || lower.contains('username') || 
               lower.contains('login') || lower.contains('account') ||
               isSensitive(propertyName)
    }
    
    /**
     * Adds a new pattern to detect sensitive properties.
     * This allows for runtime extension of sensitivity detection.
     * 
     * @param pattern Regular expression pattern to add
     */
    static void addSensitivePattern(String pattern) {
        // Note: In production, this would need thread-safe implementation
        // For now, the patterns are immutable as defined above
        throw new UnsupportedOperationException("Dynamic pattern addition not yet implemented")
    }
}
