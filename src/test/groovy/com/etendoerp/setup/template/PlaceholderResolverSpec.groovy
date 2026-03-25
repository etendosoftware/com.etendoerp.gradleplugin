package com.etendoerp.setup.template

import spock.lang.Specification

/**
 * Tests for PlaceholderResolver
 */
class PlaceholderResolverSpec extends Specification {

    // ====== PLACEHOLDER DETECTION TESTS ======

    def "detectPlaceholders finds all placeholders in properties"() {
        given:
        def properties = [
            'key1': '{placeholder1}',
            'key2': 'literal-value',
            'key3': 'prefix-{placeholder2}-suffix',
            'key4': '{placeholder1}'
        ]

        when:
        def result = PlaceholderResolver.detectPlaceholders(properties)

        then:
        result.size() == 2
        result.contains('placeholder1')
        result.contains('placeholder2')
    }

    def "detectPlaceholders returns empty set when no placeholders"() {
        given:
        def properties = [
            'key1': 'value1',
            'key2': 'value2'
        ]

        when:
        def result = PlaceholderResolver.detectPlaceholders(properties)

        then:
        result.isEmpty()
    }

    def "detectPlaceholders handles empty properties"() {
        when:
        def result = PlaceholderResolver.detectPlaceholders([:])

        then:
        result.isEmpty()
    }

    def "detectPlaceholders finds placeholders with dots and underscores"() {
        given:
        def properties = [
            'key': '{context.url}',
            'key2': '{openai.api.key}',
            'key3': '{some_value}'
        ]

        when:
        def result = PlaceholderResolver.detectPlaceholders(properties)

        then:
        result.size() == 3
        result.contains('context.url')
        result.contains('openai.api.key')
        result.contains('some_value')
    }

    def "detectPlaceholders finds multiple placeholders in a single value"() {
        given:
        def properties = [
            'key': '{host}:{port}/{name}'
        ]

        when:
        def result = PlaceholderResolver.detectPlaceholders(properties)

        then:
        result.size() == 3
        result.contains('host')
        result.contains('port')
        result.contains('name')
    }

    // ====== HAS PLACEHOLDERS TESTS ======

    def "hasPlaceholders returns true when template has placeholders"() {
        given:
        def template = new Template('test')
        template.properties = ['key': '{value}']

        expect:
        PlaceholderResolver.hasPlaceholders(template)
    }

    def "hasPlaceholders returns false when template has no placeholders"() {
        given:
        def template = new Template('test')
        template.properties = ['key': 'literal']

        expect:
        !PlaceholderResolver.hasPlaceholders(template)
    }

    def "hasPlaceholders returns false when properties are null"() {
        given:
        def template = new Template('test')
        template.properties = null

        expect:
        !PlaceholderResolver.hasPlaceholders(template)
    }

    def "hasPlaceholders returns false when properties are empty"() {
        given:
        def template = new Template('test')
        template.properties = [:]

        expect:
        !PlaceholderResolver.hasPlaceholders(template)
    }

    // ====== DERIVE CONTEXT VALUES TESTS ======

    def "deriveContextValues extracts name and host from URL"() {
        given:
        def values = [:]

        when:
        PlaceholderResolver.deriveContextValues('http://clienthost/mycompanyname', values)

        then:
        values['context.name'] == 'mycompanyname'
        values['context.host'] == 'http://clienthost/'
    }

    def "deriveContextValues handles URL with trailing slash"() {
        given:
        def values = [:]

        when:
        PlaceholderResolver.deriveContextValues('http://clienthost/mycompanyname/', values)

        then:
        values['context.name'] == 'mycompanyname'
    }

    def "deriveContextValues handles URL without path"() {
        given:
        def values = [:]

        when:
        PlaceholderResolver.deriveContextValues('http://clienthost', values)

        then:
        values['context.name'] == 'etendo'
        values['context.host'] != null
    }

    def "deriveContextValues handles URL with multiple path segments"() {
        given:
        def values = [:]

        when:
        PlaceholderResolver.deriveContextValues('http://server.example.com/path/to/etendo', values)

        then:
        values['context.name'] == 'etendo'
        values['context.host'] == 'http://server.example.com/path/to/'
    }

    def "deriveContextValues handles HTTPS URL"() {
        given:
        def values = [:]

        when:
        PlaceholderResolver.deriveContextValues('https://secure.example.com/erp', values)

        then:
        values['context.name'] == 'erp'
        values['context.host'] == 'https://secure.example.com/'
    }

    def "deriveContextValues handles URL with port number"() {
        given:
        def values = [:]

        when:
        PlaceholderResolver.deriveContextValues('http://myhost:8080/myapp', values)

        then:
        values['context.name'] == 'myapp'
        values['context.host'] == 'http://myhost:8080/'
    }

    def "deriveContextValues handles URL with only a trailing slash path"() {
        given:
        def values = [:]

        when:
        PlaceholderResolver.deriveContextValues('http://clienthost/', values)

        then:
        // Path is '/' so it falls into the else branch: defaults to 'etendo'
        values['context.name'] == 'etendo'
        values['context.host'] == 'http://clienthost/'
    }

    def "deriveContextValues handles URL without path and appends trailing slash to host"() {
        given:
        def values = [:]

        when:
        PlaceholderResolver.deriveContextValues('http://clienthost', values)

        then:
        values['context.name'] == 'etendo'
        values['context.host'] == 'http://clienthost/'
    }

    def "deriveContextValues handles URL with trailing slash on path segment"() {
        given:
        def values = [:]

        when:
        PlaceholderResolver.deriveContextValues('http://clienthost/mycompanyname/', values)

        then:
        values['context.name'] == 'mycompanyname'
        values['context.host'] == 'http://clienthost/'
    }

    def "deriveContextValues uses fallback for malformed URL"() {
        given:
        def values = [:]

        when:
        // A string with spaces is not a valid URI, triggering the catch fallback
        PlaceholderResolver.deriveContextValues('http://host with spaces/myapp', values)

        then:
        // Fallback uses simple string splitting
        values['context.name'] == 'myapp'
        values['context.host'] == 'http://host with spaces/'
    }

    def "deriveContextValues fallback defaults to etendo when URL has no meaningful path"() {
        given:
        def values = [:]

        when:
        // Triggers fallback; lastSlash is at index 4 ('://x'), indexOf('://') + 2 = 4
        // So lastSlash (4) is NOT > indexOf('://') + 2 (4), hits the else branch
        PlaceholderResolver.deriveContextValues('ht://x', values)

        then:
        values['context.name'] == 'etendo'
        values['context.host'] != null
    }

    // ====== SUBSTITUTE PLACEHOLDERS TESTS ======

    def "substitutePlaceholders replaces all occurrences"() {
        given:
        def properties = [
            'url'  : 'http://host/{context.name}',
            'host' : '{context.url}',
            'key'  : '{openai.api.key}'
        ]
        def values = [
            'context.name'  : 'myapp',
            'context.url'   : 'http://example.com/myapp',
            'openai.api.key': 'sk-12345'
        ]

        when:
        PlaceholderResolver.substitutePlaceholders(properties, values)

        then:
        properties['url'] == 'http://host/myapp'
        properties['host'] == 'http://example.com/myapp'
        properties['key'] == 'sk-12345'
    }

    def "substitutePlaceholders leaves values without placeholders unchanged"() {
        given:
        def properties = [
            'literal': 'no-placeholders-here',
            'with'   : '{placeholder}'
        ]
        def values = ['placeholder': 'resolved']

        when:
        PlaceholderResolver.substitutePlaceholders(properties, values)

        then:
        properties['literal'] == 'no-placeholders-here'
        properties['with'] == 'resolved'
    }

    def "substitutePlaceholders handles empty values map"() {
        given:
        def properties = ['key': '{unresolved}']

        when:
        PlaceholderResolver.substitutePlaceholders(properties, [:])

        then:
        // Placeholder remains if no value provided
        properties['key'] == '{unresolved}'
    }

    def "substitutePlaceholders resolves multiple placeholders in a single value"() {
        given:
        def properties = [
            'combined': '{protocol}://{host}:{port}/{path}'
        ]
        def values = [
            'protocol': 'https',
            'host'    : 'example.com',
            'port'    : '443',
            'path'    : 'erp'
        ]

        when:
        PlaceholderResolver.substitutePlaceholders(properties, values)

        then:
        properties['combined'] == 'https://example.com:443/erp'
    }

    def "substitutePlaceholders handles empty properties map"() {
        given:
        def properties = [:]

        when:
        PlaceholderResolver.substitutePlaceholders(properties, ['key': 'value'])

        then:
        properties.isEmpty()
    }

    // ====== COLLECT USER INPUT TESTS ======

    def "collectUserInput reads values from stdin"() {
        given:
        def input = "http://myhost/myapp\nsk-testkey\n"
        def oldSystemIn = System.in
        System.in = new ByteArrayInputStream(input.bytes)

        def placeholders = ['context.url', 'openai.api.key'] as LinkedHashSet

        when:
        def values = PlaceholderResolver.collectUserInput(placeholders, PlaceholderResolver.SERVER_PROMPTS)

        then:
        values['context.url'] == 'http://myhost/myapp'
        values['context.name'] == 'myapp'
        values['context.host'] == 'http://myhost/'
        values['openai.api.key'] == 'sk-testkey'

        cleanup:
        System.in = oldSystemIn
    }

    def "collectUserInput throws exception for empty input"() {
        given:
        def input = "\n"
        def oldSystemIn = System.in
        System.in = new ByteArrayInputStream(input.bytes)

        def placeholders = ['context.url'] as LinkedHashSet

        when:
        PlaceholderResolver.collectUserInput(placeholders, PlaceholderResolver.SERVER_PROMPTS)

        then:
        thrown(IllegalArgumentException)

        cleanup:
        System.in = oldSystemIn
    }

    def "collectUserInput throws exception for whitespace-only input"() {
        given:
        def input = "   \n"
        def oldSystemIn = System.in
        System.in = new ByteArrayInputStream(input.bytes)

        def placeholders = ['context.url'] as LinkedHashSet

        when:
        PlaceholderResolver.collectUserInput(placeholders, PlaceholderResolver.SERVER_PROMPTS)

        then:
        thrown(IllegalArgumentException)

        cleanup:
        System.in = oldSystemIn
    }

    def "collectUserInput prompts for all SERVER_PROMPTS keys even if not in placeholders set"() {
        given:
        // Only context.url is in the placeholder set, but openai.api.key
        // is in SERVER_PROMPTS and should still be prompted
        def input = "http://host/app\nsk-key\n"
        def oldSystemIn = System.in
        System.in = new ByteArrayInputStream(input.bytes)

        def placeholders = ['context.url'] as LinkedHashSet

        when:
        def values = PlaceholderResolver.collectUserInput(placeholders, PlaceholderResolver.SERVER_PROMPTS)

        then:
        values['context.url'] == 'http://host/app'
        values['openai.api.key'] == 'sk-key'

        cleanup:
        System.in = oldSystemIn
    }

    def "collectUserInput with preResolved context.name builds context.url from host and context.name"() {
        given:
        def input = "http://myhost\nsk-testkey\n"
        def oldSystemIn = System.in
        System.in = new ByteArrayInputStream(input.bytes)

        def placeholders = ['context.url', 'openai.api.key'] as LinkedHashSet

        when:
        def values = PlaceholderResolver.collectUserInput(placeholders, PlaceholderResolver.SERVER_PROMPTS, ['context.name': 'myapp'])

        then:
        // context.url is built from host + '/' + context.name
        values['context.url'] == 'http://myhost/myapp'
        // context.host is host + '/'
        values['context.host'] == 'http://myhost/'
        values['openai.api.key'] == 'sk-testkey'

        cleanup:
        System.in = oldSystemIn
    }

    def "collectUserInput with preResolved context.name strips trailing slash from host"() {
        given:
        def input = "http://myhost/\nsk-testkey\n"
        def oldSystemIn = System.in
        System.in = new ByteArrayInputStream(input.bytes)

        def placeholders = ['context.url', 'openai.api.key'] as LinkedHashSet

        when:
        def values = PlaceholderResolver.collectUserInput(placeholders, PlaceholderResolver.SERVER_PROMPTS, ['context.name': 'etendo'])

        then:
        // Trailing slash on input host should be stripped before building context.url
        values['context.url'] == 'http://myhost/etendo'
        values['context.host'] == 'http://myhost/'

        cleanup:
        System.in = oldSystemIn
    }

    def "collectUserInput reads values from stdin using LOCAL_PROMPTS"() {
        given:
        def input = "sk-localtestkey\n"
        def oldSystemIn = System.in
        System.in = new ByteArrayInputStream(input.bytes)

        def placeholders = ['openai.api.key'] as LinkedHashSet

        when:
        def values = PlaceholderResolver.collectUserInput(placeholders, PlaceholderResolver.LOCAL_PROMPTS)

        then:
        values['openai.api.key'] == 'sk-localtestkey'

        cleanup:
        System.in = oldSystemIn
    }

    // ====== INTEGRATION: SERVER TEMPLATE DETECTION ======

    def "server template from resources has placeholders"() {
        when:
        def template = TemplateResolver.loadFromResources('server')

        then:
        PlaceholderResolver.hasPlaceholders(template)
    }

    def "local template from resources has placeholders"() {
        when:
        def template = TemplateResolver.loadFromResources('local')

        then:
        PlaceholderResolver.hasPlaceholders(template)
    }

    def "local template placeholders match expected set"() {
        given:
        def template = TemplateResolver.loadFromResources('local')

        when:
        def placeholders = PlaceholderResolver.detectPlaceholders(template.properties)

        then:
        placeholders.contains('openai.api.key')
        placeholders.contains('context.name')
        placeholders.size() == 2
    }

    def "server template placeholders match expected set"() {
        given:
        def template = TemplateResolver.loadFromResources('server')

        when:
        def placeholders = PlaceholderResolver.detectPlaceholders(template.properties)

        then:
        placeholders.contains('context.url')
        placeholders.contains('context.name')
        placeholders.contains('context.host')
        placeholders.contains('openai.api.key')
    }
}
