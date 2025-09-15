package com.etendoerp.legacy.interactive.utils

import com.etendoerp.legacy.interactive.model.PropertyDefinition
import spock.lang.Specification

/**
 * Unit tests for SecurityUtils utility class.
 * 
 * @author Etendo Interactive Setup Team
 * @since 2.0.4
 */
class SecurityUtilsSpec extends Specification {

    def "isSensitive should detect explicit sensitivity markers"() {
        given:
        def prop = new PropertyDefinition()
        prop.sensitive = true

        expect:
        SecurityUtils.isSensitive("any.property", prop)
    }

    def "isSensitive should detect known sensitive property names"() {
        expect:
        SecurityUtils.isSensitive("bbdd.password")
        SecurityUtils.isSensitive("bbdd.systemPassword")
        SecurityUtils.isSensitive("nexusPassword")
        SecurityUtils.isSensitive("githubToken")
        SecurityUtils.isSensitive("apiKey")
        SecurityUtils.isSensitive("secretKey")
        SecurityUtils.isSensitive("privateKey")
    }

    def "isSensitive should detect sensitive patterns"() {
        expect:
        SecurityUtils.isSensitive("user.password")
        SecurityUtils.isSensitive("app.secret")
        SecurityUtils.isSensitive("auth.token")
        SecurityUtils.isSensitive("encryption.key")
        SecurityUtils.isSensitive("oauth.credentials")
        SecurityUtils.isSensitive("service.auth")
        SecurityUtils.isSensitive("user.private.data")
        SecurityUtils.isSensitive("app.secure.setting")
        SecurityUtils.isSensitive("admin.pass")
        SecurityUtils.isSensitive("system.pwd")
    }

    def "isSensitive should not detect non-sensitive properties"() {
        expect:
        !SecurityUtils.isSensitive("database.host")
        !SecurityUtils.isSensitive("app.name")
        !SecurityUtils.isSensitive("server.port")
        !SecurityUtils.isSensitive("debug.enabled")
        !SecurityUtils.isSensitive("log.level")
        !SecurityUtils.isSensitive("context.name")
    }

    def "isSensitive should handle null and empty values"() {
        expect:
        !SecurityUtils.isSensitive(null)
        !SecurityUtils.isSensitive("")
        !SecurityUtils.isSensitive("   ")
    }

    def "maskValue should mask values correctly"() {
        expect:
        SecurityUtils.maskValue("password123") == "********"
        SecurityUtils.maskValue("short") == "*****"
        SecurityUtils.maskValue("verylongpasswordstring") == "********"
        SecurityUtils.maskValue("") == ""
        SecurityUtils.maskValue(null) == ""
    }

    def "maskValue should respect maxLength parameter"() {
        expect:
        SecurityUtils.maskValue("password", 4) == "****"
        SecurityUtils.maskValue("password", 12) == "********"
        SecurityUtils.maskValue("short", 10) == "*****"
    }

    def "getDisplaySafeValue should mask sensitive values"() {
        given:
        def sensitiveProp = new PropertyDefinition()
        sensitiveProp.sensitive = true

        expect:
        SecurityUtils.getDisplaySafeValue("password", "secret123", sensitiveProp) == "********"
        SecurityUtils.getDisplaySafeValue("normal.prop", "value123") == "value123"
        SecurityUtils.getDisplaySafeValue("user.password", "secret") == "******"
    }

    def "getSafeLoggingValue should return safe values for logging"() {
        given:
        def sensitiveProp = new PropertyDefinition()
        sensitiveProp.sensitive = true

        expect:
        SecurityUtils.getSafeLoggingValue("password", "secret", sensitiveProp) == "[SENSITIVE VALUE MASKED]"
        SecurityUtils.getSafeLoggingValue("normal.prop", "value") == "value"
        SecurityUtils.getSafeLoggingValue("empty.prop", "") == "[EMPTY]"
        SecurityUtils.getSafeLoggingValue("null.prop", null) == "[EMPTY]"
    }

    def "isCredentialProperty should identify credential-related properties"() {
        expect:
        SecurityUtils.isCredentialProperty("database.user")
        SecurityUtils.isCredentialProperty("admin.username")
        SecurityUtils.isCredentialProperty("service.login")
        SecurityUtils.isCredentialProperty("system.account")
        SecurityUtils.isCredentialProperty("user.password") // Also sensitive
        
        and:
        !SecurityUtils.isCredentialProperty("database.host")
        !SecurityUtils.isCredentialProperty("app.name")
        !SecurityUtils.isCredentialProperty(null)
        !SecurityUtils.isCredentialProperty("")
    }

    // ========== EXPANDED TESTS ACCORDING TO TESTPLAN TC9-TC15 ==========

    def "TC9: isSensitive() should detect explicit sensitivity markers"() {
        given: "A PropertyDefinition with explicit sensitive flag"
        def prop = new PropertyDefinition()
        prop.key = "regular.property"
        prop.sensitive = true

        when: "Checking if property is sensitive"
        def result = SecurityUtils.isSensitive("regular.property", prop)

        then: "Returns true due to explicit flag"
        result
    }

    def "TC10: isSensitive() should detect sensitive patterns in property names"() {
        expect: "Known sensitive patterns are detected"
        SecurityUtils.isSensitive("api.secret.token")
        SecurityUtils.isSensitive("oauth.private.key")
        SecurityUtils.isSensitive("system.auth.credentials")
        SecurityUtils.isSensitive("db.admin.pass")
        SecurityUtils.isSensitive("service.secure.pwd")
    }

    def "TC11: isSensitive() should not flag regular properties as sensitive"() {
        given: "A PropertyDefinition without sensitive flag"
        def prop = new PropertyDefinition()
        prop.key = "db.host"
        prop.sensitive = false

        when: "Checking if regular property is sensitive"
        def result = SecurityUtils.isSensitive("db.host", prop)

        then: "Returns false"
        !result
    }

    def "TC12: isSensitive() should respect explicit flag regardless of property name"() {
        given: "A PropertyDefinition with explicit sensitive=true"
        def prop = new PropertyDefinition()
        prop.key = "db.host" // Normally not sensitive
        prop.sensitive = true

        when: "Checking sensitivity"
        def result = SecurityUtils.isSensitive("db.host", prop)

        then: "Returns true due to explicit flag"
        result
    }

    def "TC13: maskValue() should handle short values correctly"() {
        when: "Masking short values"
        def result1 = SecurityUtils.maskValue("abc")
        def result2 = SecurityUtils.maskValue("x")
        def result3 = SecurityUtils.maskValue("12")

        then: "Returns appropriate number of asterisks"
        result1 == "***"
        result2 == "*"
        result3 == "**"
    }

    def "TC14: maskValue() should cap long values at maximum asterisks"() {
        when: "Masking very long values"
        def result1 = SecurityUtils.maskValue("verylongpasswordstring")
        def result2 = SecurityUtils.maskValue("a" * 50)

        then: "Returns maximum 8 asterisks"
        result1 == "********"
        result2 == "********"
    }

    def "TC15: maskValue() should handle null and empty values gracefully"() {
        when: "Masking null and empty values"
        def resultNull = SecurityUtils.maskValue(null)
        def resultEmpty = SecurityUtils.maskValue("")
        def resultWhitespace = SecurityUtils.maskValue("   ")

        then: "Returns empty string for null/empty/whitespace"
        resultNull == ""
        resultEmpty == ""
        resultWhitespace == ""  // Because trim() makes it empty
    }

    // ========== EDGE CASES AND ADDITIONAL VALIDATIONS ==========

    def "should detect case-insensitive sensitive patterns"() {
        expect: "Case variations are detected"
        SecurityUtils.isSensitive("DB.PASSWORD")
        SecurityUtils.isSensitive("Api.Secret")
        SecurityUtils.isSensitive("NEXUS.TOKEN")
        SecurityUtils.isSensitive("github.TOKEN")
    }

    def "should handle special characters in property names"() {
        expect:
        SecurityUtils.isSensitive("app_secret_key")
        SecurityUtils.isSensitive("oauth-token")
        SecurityUtils.isSensitive("system.auth.key")
        !SecurityUtils.isSensitive("app_normal_setting")
    }

    def "should preserve masking consistency"() {
        given:
        def value = "consistent123"

        expect: "Same value always masks the same way"
        SecurityUtils.maskValue(value) == SecurityUtils.maskValue(value)
        SecurityUtils.maskValue(value) == "********"
    }

    def "should handle unicode values in masking"() {
        expect:
        SecurityUtils.maskValue("пароль").length() <= 8  // Will be masked with up to 8 stars
        SecurityUtils.maskValue("密码").length() <= 8     // Will be masked with up to 8 stars
        SecurityUtils.maskValue("пароль") == "******"     // 6 characters = 6 stars
        SecurityUtils.maskValue("密码") == "**"            // 2 characters = 2 stars
    }

    def "should detect comprehensive sensitive property names"() {
        expect: "Extended list of sensitive property patterns"
        // Additional password patterns
        SecurityUtils.isSensitive("bbdd.password")
        SecurityUtils.isSensitive("bbdd.systemPassword")
        SecurityUtils.isSensitive("nexusPassword")
        SecurityUtils.isSensitive("githubToken")
        
        // API and security patterns
        SecurityUtils.isSensitive("apiKey")
        SecurityUtils.isSensitive("secretKey")
        SecurityUtils.isSensitive("privateKey")
        SecurityUtils.isSensitive("accessToken")
        SecurityUtils.isSensitive("refreshToken")
        
        // Authentication patterns
        SecurityUtils.isSensitive("auth.token")
        SecurityUtils.isSensitive("oauth.secret")
        SecurityUtils.isSensitive("jwt.secret")
        SecurityUtils.isSensitive("ssl.keystore.password")
    }

    // ========== ENHANCED TESTING FOR BETTER COVERAGE ==========

    def "should detect credential properties correctly"() {
        expect: "Credential detection works for various patterns"
        SecurityUtils.isCredentialProperty("database.user")
        SecurityUtils.isCredentialProperty("system.username") 
        SecurityUtils.isCredentialProperty("oauth.login")
        SecurityUtils.isCredentialProperty("service.account")
        SecurityUtils.isCredentialProperty("admin.password") // Also sensitive
        SecurityUtils.isCredentialProperty("api.token") // Also sensitive
        
        !SecurityUtils.isCredentialProperty("database.host")
        !SecurityUtils.isCredentialProperty("app.name")
        !SecurityUtils.isCredentialProperty("server.port")
    }

    def "should handle mixed sensitivity checks"() {
        given: "property with explicit and implicit sensitivity"
        def explicitSensitive = new PropertyDefinition()
        explicitSensitive.sensitive = true
        
        def notExplicitSensitive = new PropertyDefinition()
        notExplicitSensitive.sensitive = false

        expect: "Explicit sensitivity takes precedence"
        SecurityUtils.isSensitive("normal.property", explicitSensitive) == true
        SecurityUtils.isSensitive("password.field", notExplicitSensitive) == true // Pattern still wins
        SecurityUtils.isSensitive("normal.property", notExplicitSensitive) == false
    }

    def "should handle edge cases in display safe values"() {
        expect: "Edge cases in display safe values"
        SecurityUtils.getDisplaySafeValue("test", null) == ""
        SecurityUtils.getDisplaySafeValue("test", "") == ""
        SecurityUtils.getDisplaySafeValue("password", null) == ""
        SecurityUtils.getDisplaySafeValue("password", "") == ""
        SecurityUtils.getDisplaySafeValue(null, "value") == "value" // Non-sensitive due to null key
    }

    def "should handle edge cases in safe logging values"() {
        expect: "Edge cases in safe logging values"
        SecurityUtils.getSafeLoggingValue("test", null) == "[EMPTY]"
        SecurityUtils.getSafeLoggingValue("test", "") == "[EMPTY]"
        SecurityUtils.getSafeLoggingValue("password", null) == "[SENSITIVE VALUE MASKED]"
        SecurityUtils.getSafeLoggingValue("password", "") == "[SENSITIVE VALUE MASKED]"
        SecurityUtils.getSafeLoggingValue(null, "value") == "value" // Non-sensitive due to null key
    }

    def "should handle very long property names"() {
        given: "very long property names"
        def longNormalName = "very.long.property.name.that.goes.on.and.on.without.sensitive.words"
        def longSensitiveName = "very.long.property.name.that.contains.password.in.the.middle.somewhere"

        expect: "Long names are handled correctly"
        !SecurityUtils.isSensitive(longNormalName)
        SecurityUtils.isSensitive(longSensitiveName)
    }

    def "should handle property names with numbers and special chars"() {
        expect: "Property names with numbers and special characters"
        SecurityUtils.isSensitive("oauth2.secret")
        SecurityUtils.isSensitive("app-v2.password")
        SecurityUtils.isSensitive("service_1.token")
        !SecurityUtils.isSensitive("port_8080.setting")
        !SecurityUtils.isSensitive("version-2.1.name")
    }

    def "should handle masking with different lengths"() {
        expect: "Masking with various length parameters"
        SecurityUtils.maskValue("password", 0) == ""
        SecurityUtils.maskValue("password", 1) == "*"
        SecurityUtils.maskValue("password", 4) == "****"
        SecurityUtils.maskValue("password", 20) == "********" // Limited by actual length
        SecurityUtils.maskValue("a", 10) == "*" // Limited by actual length
    }

    def "should handle regex patterns correctly"() {
        expect: "Regex patterns work as expected"
        // Test that patterns work for partial matches
        SecurityUtils.isSensitive("mypassword")
        SecurityUtils.isSensitive("passwordfield")
        SecurityUtils.isSensitive("secretvalue")
        SecurityUtils.isSensitive("tokendata")
        SecurityUtils.isSensitive("keystore")
        SecurityUtils.isSensitive("credentials")
        SecurityUtils.isSensitive("authdata")
        SecurityUtils.isSensitive("privateinfo")
        SecurityUtils.isSensitive("securedata")
        SecurityUtils.isSensitive("userpass")
        SecurityUtils.isSensitive("systempwd")
    }

    def "should reject adding new patterns"() {
        when: "trying to add new sensitive pattern"
        SecurityUtils.addSensitivePattern("newpattern.*")

        then: "should throw UnsupportedOperationException"
        thrown(UnsupportedOperationException)
    }

    def "should handle PropertyDefinition without sensitive field set"() {
        given: "PropertyDefinition without explicit sensitive field"
        def prop = new PropertyDefinition()
        // sensitive field is not set (defaults to false)

        expect: "Should fall back to pattern matching"
        SecurityUtils.isSensitive("password.field", prop) == true
        SecurityUtils.isSensitive("normal.field", prop) == false
    }

    def "should handle complex property names"() {
        expect: "Complex nested property names"
        SecurityUtils.isSensitive("app.database.connection.password")
        SecurityUtils.isSensitive("security.oauth.client.secret")
        SecurityUtils.isSensitive("services.external.api.token")
        SecurityUtils.isSensitive("infrastructure.ssl.private.key")
        
        !SecurityUtils.isSensitive("app.database.connection.timeout")
        !SecurityUtils.isSensitive("security.client.enabled") // Avoid 'auth' pattern
        !SecurityUtils.isSensitive("services.external.api.endpoint")
    }
}
