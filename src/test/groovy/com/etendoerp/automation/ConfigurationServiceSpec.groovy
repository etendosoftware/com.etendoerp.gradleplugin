package com.etendoerp.automation

import com.etendoerp.legacy.interactive.ConfigSlurperPropertyScanner
import com.etendoerp.legacy.interactive.ConfigWriter
import com.etendoerp.legacy.interactive.model.PropertyDefinition
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification
import spock.lang.TempDir

import java.lang.reflect.Field
import java.nio.file.Path

class ConfigurationServiceSpec extends Specification {

    @TempDir
    Path tempDir

    def project
    def service
    ConfigSlurperPropertyScanner scanner
    ConfigWriter writer

    def setup() {
        project = ProjectBuilder.builder()
                .withProjectDir(tempDir.toFile())
                .build()

        // Ensure gradle.properties exists for save operations
        new File(project.rootDir, "gradle.properties").text = ""

        scanner = Mock(ConfigSlurperPropertyScanner)
        writer = Mock(ConfigWriter)
        service = new ConfigurationService(project)

        // Replace final collaborators with mocks to isolate behavior
        overwriteFinalField(service, "scanner", scanner)
        overwriteFinalField(service, "writer", writer)
    }

    def "readAllConfigurations maps scanned properties"() {
        given:
        def definitions = [
                new PropertyDefinition(
                        key: "db.host",
                        currentValue: "localhost",
                        defaultValue: "127.0.0.1",
                        documentation: "Database host",
                        groups: ["Database"],
                        required: true,
                        source: "main",
                        order: 1
                ),
                new PropertyDefinition(
                        key: "app.email",
                        currentValue: "",
                        defaultValue: "",
                        help: "Support contact",
                        groups: [],
                        required: false,
                        source: "modules/app",
                        order: 2
                )
        ]
        scanner.scanAllProperties() >> definitions

        when:
        def result = service.readAllConfigurations()

        then:
        result.total == 2
        result.properties*.key == ["db.host", "app.email"]
        result.properties.find { it.key == "db.host" }.group == "Database"
        result.properties.find { it.key == "app.email" }.groups == ["General"]
    }

    def "saveConfigurations reports validation errors for invalid input"() {
        given:
        def definitions = [
                new PropertyDefinition(key: "server.port", required: true),
                new PropertyDefinition(key: "admin.email", required: false)
        ]
        scanner.scanAllProperties() >> definitions

        when:
        def result = service.saveConfigurations([
                "server.port": "not-a-number",
                "admin.email": "invalid-email"
        ])

        then:
        !result.success
        result.validationErrors.keySet() == ["server.port", "admin.email"] as Set
        0 * writer.writeProperties(_)
    }

    def "saveConfigurations filters defaults honoring notSetWhenDefault flag"() {
        given:
        def definitions = [
                new PropertyDefinition(
                        key: "app.port",
                        defaultValue: "8080",
                        notSetWhenDefault: true
                ),
                new PropertyDefinition(
                        key: "app.name",
                        defaultValue: "etendo",
                        notSetWhenDefault: false
                )
        ]
        scanner.scanAllProperties() >> definitions

        when:
        def result = service.saveConfigurations([
                "app.port": "8080",
                "app.name": "custom"
        ])

        then:
        result.success
        result.updatedKeys == ["app.name"]
        1 * writer.writeProperties(["app.name": "custom"])
    }

    private static void overwriteFinalField(Object target, String fieldName, Object value) {
        Field field = target.class.getDeclaredField(fieldName)
        field.accessible = true
        field.set(target, value)
    }
}
