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
}
