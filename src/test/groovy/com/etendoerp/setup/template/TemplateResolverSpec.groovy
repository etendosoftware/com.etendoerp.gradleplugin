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

    def "listAvailableTemplates returns local and server"() {
        when:
        def templates = TemplateResolver.listAvailableTemplates()

        then:
        templates != null
        templates.size() == 2
        templates.contains('local')
        templates.contains('server')
    }

    def "loadFromResources loads local template"() {
        when:
        def template = TemplateResolver.loadFromResources('local')

        then:
        template != null
        template.name == 'local'
        template.source == 'resources'
        template.properties['docker_com.etendoerp.mainui'] == 'true'
        template.properties['copilot.host'] == 'localhost'
        template.properties['copilot.port'] == '5005'
        template.properties['etendo.host'] == 'http://localhost:8080/{context.name}'
        template.properties['etendo.host.docker'] == 'http://host.docker.internal:8080/{context.name}'
        template.properties['etendo.classic.url'] == 'http://host.docker.internal:8080/{context.name}'
        template.properties['etendo.classic.host'] == 'https://localhost:8080/{context.name}'
        template.properties['sso.middleware.redirectUri'] == 'http://localhost:8080/{context.name}/secureApp/LoginHandler.html'
    }

    def "loadFromResources loads server template with placeholders"() {
        when:
        def template = TemplateResolver.loadFromResources('server')

        then:
        template != null
        template.name == 'server'
        template.source == 'resources'
        // Server template should have placeholders
        template.properties['etendo.classic.host'] == '{context.url}'
        template.properties['next.public.app.url'] == '{context.host}'
        template.properties['openai.api.key'] == '{openai.api.key}'
        template.properties['copilot.port'] == '5005'
        template.properties['etendo.host'] == 'http://localhost:80/{context.name}'
        template.properties['etendo.host.docker'] == 'http://host.docker.internal:80/{context.name}'
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
        TemplateResolver.resolve(project, 'local', templateFile.absolutePath, 'invalid-url')

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
        def template = TemplateResolver.resolve(project, 'local', templateFile.absolutePath, null)

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

    def "resolve loads local template with interactive placeholder resolution"() {
        given:
        def input = "sk-Qbn-K2u8vJfT4eyXmW8pdw\n"
        def oldSystemIn = System.in
        System.in = new ByteArrayInputStream(input.bytes)

        when:
        def template = TemplateResolver.resolve(project, 'local', null, null)

        then:
        template != null
        template.name == 'local'
        template.properties['openai.api.key'] == 'sk-Qbn-K2u8vJfT4eyXmW8pdw'
        // context.name should default to 'etendo' when no gradle.properties exists
        template.properties['etendo.host'] == 'http://localhost:8080/etendo'
        template.properties['etendo.host.docker'] == 'http://host.docker.internal:8080/etendo'
        template.properties['etendo.classic.url'] == 'http://host.docker.internal:8080/etendo'
        template.properties['etendo.classic.host'] == 'https://localhost:8080/etendo'
        template.properties['sso.middleware.redirectUri'] == 'http://localhost:8080/etendo/secureApp/LoginHandler.html'

        cleanup:
        System.in = oldSystemIn
    }

    def "resolve local template uses context.name from gradle.properties when set"() {
        given:
        def propsFile = new File(tempDir.toFile(), 'gradle.properties')
        propsFile.text = "context.name=myapp\n"
        def input = "sk-testkey\n"
        def oldSystemIn = System.in
        System.in = new ByteArrayInputStream(input.bytes)

        when:
        def template = TemplateResolver.resolve(project, 'local', null, null)

        then:
        template != null
        template.name == 'local'
        template.properties['etendo.host'] == 'http://localhost:8080/myapp'
        template.properties['etendo.host.docker'] == 'http://host.docker.internal:8080/myapp'
        template.properties['etendo.classic.url'] == 'http://host.docker.internal:8080/myapp'
        template.properties['etendo.classic.host'] == 'https://localhost:8080/myapp'
        template.properties['sso.middleware.redirectUri'] == 'http://localhost:8080/myapp/secureApp/LoginHandler.html'

        cleanup:
        System.in = oldSystemIn
    }

    def "resolve local template defaults context.name to etendo when not in gradle.properties"() {
        given:
        // No gradle.properties in tempDir (clean state)
        def input = "sk-testkey\n"
        def oldSystemIn = System.in
        System.in = new ByteArrayInputStream(input.bytes)

        when:
        def template = TemplateResolver.resolve(project, 'local', null, null)

        then:
        template != null
        template.name == 'local'
        template.properties['etendo.host'] == 'http://localhost:8080/etendo'
        template.properties['sso.middleware.redirectUri'] == 'http://localhost:8080/etendo/secureApp/LoginHandler.html'

        cleanup:
        System.in = oldSystemIn
    }

    def "resolve local template defaults context.name to etendo when gradle.properties exists but context.name is not set"() {
        given:
        def propsFile = new File(tempDir.toFile(), 'gradle.properties')
        propsFile.text = "bbdd.sid=mydb\n"
        def input = "sk-testkey\n"
        def oldSystemIn = System.in
        System.in = new ByteArrayInputStream(input.bytes)

        when:
        def template = TemplateResolver.resolve(project, 'local', null, null)

        then:
        template != null
        template.name == 'local'
        template.properties['etendo.host'] == 'http://localhost:8080/etendo'
        template.properties['sso.middleware.redirectUri'] == 'http://localhost:8080/etendo/secureApp/LoginHandler.html'

        cleanup:
        System.in = oldSystemIn
    }

    def "resolve local template handles context.name with custom value and spaces trimmed"() {
        given:
        def propsFile = new File(tempDir.toFile(), 'gradle.properties')
        propsFile.text = "context.name=  customctx  \n"
        def input = "sk-testkey\n"
        def oldSystemIn = System.in
        System.in = new ByteArrayInputStream(input.bytes)

        when:
        def template = TemplateResolver.resolve(project, 'local', null, null)

        then:
        template != null
        template.name == 'local'
        template.properties['etendo.host'] == 'http://localhost:8080/customctx'
        template.properties['etendo.host.docker'] == 'http://host.docker.internal:8080/customctx'
        template.properties['etendo.classic.url'] == 'http://host.docker.internal:8080/customctx'
        template.properties['etendo.classic.host'] == 'https://localhost:8080/customctx'
        template.properties['sso.middleware.redirectUri'] == 'http://localhost:8080/customctx/secureApp/LoginHandler.html'

        cleanup:
        System.in = oldSystemIn
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

    def "promptUserSelection with valid selection 1 returns local template"() {
        given:
        def input = "1\n"
        def oldSystemIn = System.in
        System.in = new ByteArrayInputStream(input.bytes)

        when:
        def template = TemplateResolver.promptUserSelection(project)

        then:
        template != null
        template.name == 'local'

        cleanup:
        System.in = oldSystemIn
    }

    def "promptUserSelection with selection 2 returns server template"() {
        given:
        def input = "2\n"
        def oldSystemIn = System.in
        System.in = new ByteArrayInputStream(input.bytes)

        when:
        def template = TemplateResolver.promptUserSelection(project)

        then:
        template != null
        template.name == 'server'

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

    def "promptUserSelection with negative selection throws exception"() {
        given:
        def input = "-1\n"
        def oldSystemIn = System.in
        System.in = new ByteArrayInputStream(input.bytes)

        when:
        TemplateResolver.promptUserSelection(project)

        then:
        thrown(IllegalArgumentException)

        cleanup:
        System.in = oldSystemIn
    }

    // ====== RESOLVE WITH PLACEHOLDER INTEGRATION TESTS ======

    def "resolve with server template triggers placeholder resolution"() {
        given:
        // No gradle.properties in tempDir → context.name defaults to 'etendo'
        // User now enters only the host, not the full URL including context path
        def input = "http://myhost\nsk-testkey123\n"
        def oldSystemIn = System.in
        System.in = new ByteArrayInputStream(input.bytes)

        when:
        def template = TemplateResolver.resolve(project, 'server', null, null)

        then:
        template != null
        template.name == 'server'
        // context.url = host + '/' + context.name (default 'etendo')
        template.properties['etendo.classic.host'] == 'http://myhost/etendo'
        // context.host = host + '/'
        template.properties['next.public.app.url'] == 'http://myhost/'
        template.properties['openai.api.key'] == 'sk-testkey123'

        cleanup:
        System.in = oldSystemIn
    }

    def "resolve with server template derives context.name into docker URL"() {
        given:
        // No gradle.properties in tempDir → context.name defaults to 'etendo'
        // etendo.classic.url has {context.name} pre-substituted to 'etendo' before user input
        def input = "http://prodserver\nsk-prodkey\n"
        def oldSystemIn = System.in
        System.in = new ByteArrayInputStream(input.bytes)

        when:
        def template = TemplateResolver.resolve(project, 'server', null, null)

        then:
        template != null
        // etendo.classic.url had {context.name} substituted to 'etendo' (the default)
        template.properties['etendo.classic.url'] == 'http://host.docker.internal:80/etendo'

        cleanup:
        System.in = oldSystemIn
    }

    def "resolve server template uses context.name from gradle.properties"() {
        given:
        def propsFile = new File(tempDir.toFile(), 'gradle.properties')
        propsFile.text = "context.name=myapp\n"
        def input = "http://prodserver\nsk-prodkey\n"
        def oldSystemIn = System.in
        System.in = new ByteArrayInputStream(input.bytes)

        when:
        def template = TemplateResolver.resolve(project, 'server', null, null)

        then:
        template != null
        // {context.name} pre-substituted to 'myapp'; user enters only host
        template.properties['etendo.classic.url'] == 'http://host.docker.internal:80/myapp'
        // context.url = host + '/' + context.name
        template.properties['etendo.classic.host'] == 'http://prodserver/myapp'
        // context.host = host + '/'
        template.properties['next.public.app.url'] == 'http://prodserver/'
        template.properties['sso.middleware.redirectUri'] == 'http://prodserver/myapp/secureApp/LoginHandler.html'
        // {context.name} pre-substituted to 'myapp' for copilot host properties
        template.properties['etendo.host'] == 'http://localhost:80/myapp'
        template.properties['etendo.host.docker'] == 'http://host.docker.internal:80/myapp'

        cleanup:
        System.in = oldSystemIn
    }

    def "resolve server template defaults context.name to etendo when not in gradle.properties"() {
        given:
        // No gradle.properties in tempDir
        def input = "http://myserver\nsk-key\n"
        def oldSystemIn = System.in
        System.in = new ByteArrayInputStream(input.bytes)

        when:
        def template = TemplateResolver.resolve(project, 'server', null, null)

        then:
        template != null
        // context.name defaults to 'etendo'; context.url = host + '/' + 'etendo'
        template.properties['etendo.classic.host'] == 'http://myserver/etendo'
        template.properties['sso.middleware.redirectUri'] == 'http://myserver/etendo/secureApp/LoginHandler.html'

        cleanup:
        System.in = oldSystemIn
    }

    // ====== LOAD FROM FILE WITH PLACEHOLDERS ======

    def "resolve from file with placeholders triggers interactive resolution"() {
        given:
        def templateFile = new File(tempDir.toFile(), 'custom.template')
        templateFile.text = """
[properties]
app.url={context.url}
api.key={openai.api.key}
"""
        def input = "http://custom/app\nsk-custom\n"
        def oldSystemIn = System.in
        System.in = new ByteArrayInputStream(input.bytes)

        when:
        def template = TemplateResolver.resolve(project, null, templateFile.absolutePath, null)

        then:
        template != null
        template.properties['app.url'] == 'http://custom/app'
        template.properties['api.key'] == 'sk-custom'

        cleanup:
        System.in = oldSystemIn
    }

    def "resolve from file without placeholders does not prompt"() {
        given:
        def templateFile = new File(tempDir.toFile(), 'simple.template')
        templateFile.text = """
[properties]
static.key=static.value
"""

        when:
        def template = TemplateResolver.resolve(project, null, templateFile.absolutePath, null)

        then:
        template != null
        template.properties['static.key'] == 'static.value'
    }

    // ====== EDGE CASE: EMPTY TEMPLATE FILE ======

    def "loadFromFile handles template with empty properties section"() {
        given:
        def templateFile = new File(tempDir.toFile(), 'empty-props.template')
        templateFile.text = "[properties]\n"

        when:
        def template = TemplateResolver.loadFromFile(templateFile.absolutePath)

        then:
        template != null
        template.properties.isEmpty()
    }
}
