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
        prop.getPromptText() == "Database server hostname\ndatabase.host (localhost): "

        when: "property has no documentation but has value"
        prop.documentation = null
        prop.currentValue = "localhost"

        then:
        prop.getPromptText() == "database.host (localhost): "

        when: "property has no value"
        prop.currentValue = null
        prop.defaultValue = null

        then:
        prop.getPromptText() == "database.host: "
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
        promptText.contains("database.password (********)")
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
}
