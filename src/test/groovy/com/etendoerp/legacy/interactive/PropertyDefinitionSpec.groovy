package com.etendoerp.legacy.interactive

import com.etendoerp.legacy.interactive.model.PropertyDefinition
import spock.lang.Specification

/**
 * Unit tests for PropertyDefinition model class.
 * 
 * @author Etendo Interactive Setup Team
 * @since 2.0.4
 */
class PropertyDefinitionSpec extends Specification {

    def "should create PropertyDefinition with basic properties"() {
        given:
        def prop = new PropertyDefinition()
        prop.key = "test.property"
        prop.currentValue = "current"
        prop.defaultValue = "default"
        prop.documentation = "Test documentation"
        prop.group = "Test Group"
        prop.sensitive = false
        prop.required = true

        expect:
        prop.key == "test.property"
        prop.currentValue == "current"
        prop.defaultValue == "default"
        prop.documentation == "Test documentation"
        prop.group == "Test Group"
        !prop.sensitive
        prop.required
    }

    def "getDisplayValue should prioritize current over default value"() {
        given:
        def prop = new PropertyDefinition()
        prop.key = "test.key"

        when: "both current and default values are set"
        prop.currentValue = "current"
        prop.defaultValue = "default"

        then:
        prop.getDisplayValue() == "current"

        when: "only default value is set"
        prop.currentValue = null
        prop.defaultValue = "default"

        then:
        prop.getDisplayValue() == "default"

        when: "neither value is set"
        prop.currentValue = null
        prop.defaultValue = null

        then:
        prop.getDisplayValue() == ""
    }

    def "getPromptText should format correctly for various scenarios"() {
        given:
        def prop = new PropertyDefinition()
        prop.key = "database.host"

        when: "property has documentation and value"
        prop.documentation = "Database server hostname"
        prop.currentValue = "localhost"

        then:
        def promptText = prop.getPromptText()
        promptText.contains("Property: database.host")
        promptText.contains("Database server hostname")
        promptText.contains("Current value: localhost")
        promptText.contains("New value:")

        when: "property has no documentation but has value"
        prop.documentation = null
        prop.currentValue = "localhost"

        then:
        def promptText2 = prop.getPromptText()
        promptText2.contains("Property: database.host")
        promptText2.contains("Current value: localhost")
        promptText2.contains("New value:")

        when: "property has no value"
        prop.currentValue = null
        prop.defaultValue = null

        then:
        def promptText3 = prop.getPromptText()
        promptText3.contains("Property: database.host")
        promptText3.contains("New value:")
    }

    def "getPromptText should mask sensitive values"() {
        given:
        def prop = new PropertyDefinition()
        prop.key = "database.password"
        prop.sensitive = true
        prop.currentValue = "secretpassword"

        when:
        def promptText = prop.getPromptText()

        then:
        promptText.contains("Property: database.password")
        promptText.contains("Current value: ********")
        !promptText.contains("secretpassword")
    }

    def "hasValue should return correct boolean"() {
        given:
        def prop = new PropertyDefinition()

        when: "no values set"
        prop.currentValue = null
        prop.defaultValue = null

        then:
        !prop.hasValue()

        when: "current value set"
        prop.currentValue = "value"

        then:
        prop.hasValue()

        when: "only default value set"
        prop.currentValue = null
        prop.defaultValue = "default"

        then:
        prop.hasValue()

        when: "empty values"
        prop.currentValue = ""
        prop.defaultValue = "  "

        then:
        !prop.hasValue()
    }

    def "equals and hashCode should work correctly"() {
        given:
        def prop1 = new PropertyDefinition(key: "test.key")
        def prop2 = new PropertyDefinition(key: "test.key")
        def prop3 = new PropertyDefinition(key: "other.key")

        expect:
        prop1 == prop2
        prop1 != prop3
        prop1.hashCode() == prop2.hashCode()
    }

    def "toString should provide meaningful representation"() {
        given:
        def prop = new PropertyDefinition()
        prop.key = "test.key"
        prop.group = "Test"
        prop.sensitive = true
        prop.required = false
        prop.currentValue = "value"

        when:
        def str = prop.toString()

        then:
        str.contains("test.key")
        str.contains("Test")
        str.contains("sensitive=true")
        str.contains("required=false")
        str.contains("hasValue=true")
    }

    // ========== EXPANDED TESTS ACCORDING TO TESTPLAN TC1-TC8 ==========

    def "TC1: should create PropertyDefinition with basic values correctly"() {
        given: "A new PropertyDefinition instance"
        def prop = new PropertyDefinition()

        when: "Setting basic properties"
        prop.key = "database.host"
        prop.currentValue = "localhost"
        prop.defaultValue = "127.0.0.1"
        prop.documentation = "Database server host"
        prop.group = "Database"
        prop.sensitive = false
        prop.required = true

        then: "All properties are assigned correctly"
        prop.key == "database.host"
        prop.currentValue == "localhost"
        prop.defaultValue == "127.0.0.1"
        prop.documentation == "Database server host"
        prop.group == "Database"
        !prop.sensitive
        prop.required
    }

    def "TC2: getDisplayValue() should prioritize currentValue over defaultValue"() {
        given: "A PropertyDefinition with both current and default values"
        def prop = new PropertyDefinition()
        prop.key = "test.property"
        prop.currentValue = "currentValue"
        prop.defaultValue = "defaultValue"

        when: "Calling getDisplayValue()"
        def result = prop.getDisplayValue()

        then: "Returns currentValue (priority over default)"
        result == "currentValue"
    }

    def "TC3: getDisplayValue() should return defaultValue when currentValue is null"() {
        given: "A PropertyDefinition with only defaultValue"
        def prop = new PropertyDefinition()
        prop.key = "test.property"
        prop.currentValue = null
        prop.defaultValue = "defaultValue"

        when: "Calling getDisplayValue()"
        def result = prop.getDisplayValue()

        then: "Returns defaultValue"
        result == "defaultValue"
    }

    def "TC4: getDisplayValue() should return empty string when no values are set"() {
        given: "A PropertyDefinition without values"
        def prop = new PropertyDefinition()
        prop.key = "test.property"
        prop.currentValue = null
        prop.defaultValue = null

        when: "Calling getDisplayValue()"
        def result = prop.getDisplayValue()

        then: "Returns empty string"
        result == ""
    }

    def "TC5: getPromptText() should generate prompt with documentation and visible value for regular property"() {
        given: "A non-sensitive PropertyDefinition with documentation"
        def prop = new PropertyDefinition()
        prop.key = "db.host"
        prop.documentation = "Database hostname"
        prop.currentValue = "localhost"
        prop.sensitive = false

        when: "Calling getPromptText()"
        def result = prop.getPromptText()

        then: "Generates prompt with documentation and visible value"
        result.contains("Database hostname")
        result.contains("db.host")
        result.contains("localhost")
        !result.contains("*")
    }

    def "TC6: getPromptText() should generate prompt with masked value for sensitive property"() {
        given: "A sensitive PropertyDefinition"
        def prop = new PropertyDefinition()
        prop.key = "db.password"
        prop.documentation = "Database password"
        prop.currentValue = "secretpass123"
        prop.sensitive = true

        when: "Calling getPromptText()"
        def result = prop.getPromptText()

        then: "Generates prompt with masked value"
        result.contains("Database password")
        result.contains("db.password")
        result.contains("*")
        !result.contains("secretpass123")
    }

    def "TC7: hasValue() should return true when property has values"() {
        given: "PropertyDefinition instances"
        def propWithCurrent = new PropertyDefinition()
        propWithCurrent.currentValue = "current"
        
        def propWithDefault = new PropertyDefinition()
        propWithDefault.defaultValue = "default"
        
        def propWithBoth = new PropertyDefinition()
        propWithBoth.currentValue = "current"
        propWithBoth.defaultValue = "default"

        expect: "hasValue() returns true for all cases"
        propWithCurrent.hasValue()
        propWithDefault.hasValue()
        propWithBoth.hasValue()
    }

    def "TC8: hasValue() should return false when property has no values"() {
        given: "PropertyDefinition instances without values"
        def propEmpty = new PropertyDefinition()
        
        def propNulls = new PropertyDefinition()
        propNulls.currentValue = null
        propNulls.defaultValue = null
        
        def propBlankStrings = new PropertyDefinition()
        propBlankStrings.currentValue = ""
        propBlankStrings.defaultValue = "   "

        expect: "hasValue() returns false for all cases"
        !propEmpty.hasValue()
        !propNulls.hasValue()
        !propBlankStrings.hasValue()
    }

    // ========== EDGE CASES AND ADDITIONAL VALIDATIONS ==========

    def "should handle special characters in property values"() {
        given:
        def prop = new PropertyDefinition()
        prop.key = "special.chars"
        prop.currentValue = "value=with:special\\characters"

        expect:
        prop.getDisplayValue() == "value=with:special\\characters"
        prop.getPromptText().contains("value=with:special\\characters")
    }

    def "should handle unicode characters in values"() {
        given:
        def prop = new PropertyDefinition()
        prop.key = "unicode.test"
        prop.currentValue = "测试值"
        prop.documentation = "Descripción"

        expect:
        prop.getDisplayValue() == "测试值"
        prop.getPromptText().contains("测试值")
        prop.getPromptText().contains("Descripción")
    }

    def "should handle extremely long values"() {
        given:
        def longValue = "a" * 1000
        def prop = new PropertyDefinition()
        prop.key = "long.value"
        prop.currentValue = longValue

        expect:
        prop.getDisplayValue() == longValue
        prop.hasValue()
    }
}
