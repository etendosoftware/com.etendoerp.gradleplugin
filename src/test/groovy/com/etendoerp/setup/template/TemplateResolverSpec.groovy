package com.etendoerp.setup.template

import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path

/**
 * Tests for TemplateResolver
 */
class TemplateResolverSpec extends Specification {

    @TempDir
    Path tempDir

    def project

    def setup() {
        project = ProjectBuilder.builder()
                .withProjectDir(tempDir.toFile())
                .build()
    }

    // ====== FILE RESOLUTION TESTS ======

    def "loadFromFile loads template from existing file"() {
        given:
        def templateFile = new File(tempDir.toFile(), 'test.template')
        templateFile.text = """
[properties]
test.property=value1
"""

        when:
        def template = TemplateResolver.loadFromFile(templateFile.absolutePath)

        then:
        template != null
        template.name == 'test'
        template.source.startsWith('file:')
        template.properties['test.property'] == 'value1'
    }

    def "loadFromFile throws exception for non-existent file"() {
        when:
        TemplateResolver.loadFromFile('/non/existent/file.template')

        then:
        thrown(FileNotFoundException)
    }

    def "loadFromFile handles file without .template extension"() {
        given:
        def templateFile = new File(tempDir.toFile(), 'mytemplate')
        templateFile.text = """
[properties]
key=value
"""

        when:
        def template = TemplateResolver.loadFromFile(templateFile.absolutePath)

        then:
        template != null
        template.name == 'mytemplate'
    }

    // ====== URL RESOLUTION TESTS ======

    def "loadFromUrl throws exception for invalid URL"() {
        when:
        TemplateResolver.loadFromUrl('not-a-valid-url')

        then:
        thrown(Exception)
    }

    // ====== RESOURCES RESOLUTION TESTS ======

    def "loadFromResources throws exception for non-existent template"() {
        when:
        TemplateResolver.loadFromResources('non-existent-template')

        then:
        thrown(IllegalArgumentException)
    }

    def "listAvailableTemplates returns template names"() {
        when:
        def templates = TemplateResolver.listAvailableTemplates()

        then:
        templates != null
        !templates.isEmpty()
        templates.contains('copilot')
        templates.contains('base')
        templates.contains('production')
        templates.contains('development')
    }

    // ====== RESOLVE METHOD TESTS ======

    def "resolve prioritizes URL over other sources"() {
        given:
        def templateFile = new File(tempDir.toFile(), 'test.template')
        templateFile.text = """
[properties]
key=value
"""

        when:
        // URL will fail, but it should try URL first
        TemplateResolver.resolve(project, 'copilot', templateFile.absolutePath, 'invalid-url')

        then:
        thrown(Exception)
    }

    def "resolve uses filePath when URL is null"() {
        given:
        def templateFile = new File(tempDir.toFile(), 'test.template')
        templateFile.text = """
[properties]
key=value
"""

        when:
        def template = TemplateResolver.resolve(project, 'copilot', templateFile.absolutePath, null)

        then:
        template != null
        template.source.startsWith('file:')
    }

    def "resolve uses templateName when both URL and filePath are null"() {
        when:
        TemplateResolver.resolve(project, 'non-existent', null, null)

        then:
        thrown(IllegalArgumentException)
    }

    // ====== EDGE CASES ======

    def "loadFromFile handles template with all sections"() {
        given:
        def templateFile = new File(tempDir.toFile(), 'complete.template')
        templateFile.text = """
[properties]
prop=value

[dependencies]
com.example:lib:1.0.0

[modules]
mod1
"""

        when:
        def template = TemplateResolver.loadFromFile(templateFile.absolutePath)

        then:
        template.properties.size() == 1
        template.dependencies.size() == 1
        template.modules.size() == 1
    }

    def "loadFromFile sets correct source"() {
        given:
        def templateFile = new File(tempDir.toFile(), 'test.template')
        templateFile.text = "[properties]\nkey=value"

        when:
        def template = TemplateResolver.loadFromFile(templateFile.absolutePath)

        then:
        template.source == "file:${templateFile.absolutePath}"
    }

    // ====== PROMPT USER SELECTION TESTS ======

    def "promptUserSelection with valid selection returns template"() {
        given:
        def input = "1\n"
        def oldSystemIn = System.in
        System.in = new ByteArrayInputStream(input.bytes)

        when:
        def template = TemplateResolver.promptUserSelection(project)

        then:
        template != null
        template.name == 'copilot'

        cleanup:
        System.in = oldSystemIn
    }

    def "promptUserSelection with selection 2 returns base template"() {
        given:
        def input = "2\n"
        def oldSystemIn = System.in
        System.in = new ByteArrayInputStream(input.bytes)

        when:
        def template = TemplateResolver.promptUserSelection(project)

        then:
        template != null
        template.name == 'base'

        cleanup:
        System.in = oldSystemIn
    }

    def "promptUserSelection with empty input throws exception"() {
        given:
        def input = "\n"
        def oldSystemIn = System.in
        System.in = new ByteArrayInputStream(input.bytes)

        when:
        TemplateResolver.promptUserSelection(project)

        then:
        thrown(IllegalArgumentException)

        cleanup:
        System.in = oldSystemIn
    }

    def "promptUserSelection with non-numeric input throws exception"() {
        given:
        def input = "invalid\n"
        def oldSystemIn = System.in
        System.in = new ByteArrayInputStream(input.bytes)

        when:
        TemplateResolver.promptUserSelection(project)

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains('Invalid selection')

        cleanup:
        System.in = oldSystemIn
    }

    def "promptUserSelection with selection out of range throws exception"() {
        given:
        def input = "99\n"
        def oldSystemIn = System.in
        System.in = new ByteArrayInputStream(input.bytes)

        when:
        TemplateResolver.promptUserSelection(project)

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains('Invalid selection')

        cleanup:
        System.in = oldSystemIn
    }

    def "promptUserSelection with zero selection throws exception"() {
        given:
        def input = "0\n"
        def oldSystemIn = System.in
        System.in = new ByteArrayInputStream(input.bytes)

        when:
        TemplateResolver.promptUserSelection(project)

        then:
        thrown(IllegalArgumentException)

        cleanup:
        System.in = oldSystemIn
    }

    def "promptUserSelection with selection 4 returns development template"() {
        given:
        def input = "4\n"
        def oldSystemIn = System.in
        System.in = new ByteArrayInputStream(input.bytes)

        when:
        def template = TemplateResolver.promptUserSelection(project)

        then:
        template != null
        template.name == 'development'

        cleanup:
        System.in = oldSystemIn
    }
}
