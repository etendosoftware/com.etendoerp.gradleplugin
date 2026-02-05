package com.etendoerp.setup.template

import spock.lang.Specification

/**
 * Tests for Template class
 */
class TemplateSpec extends Specification {

    def "constructor creates empty template"() {
        when:
        def template = new Template()

        then:
        template.name == null
        template.source == null
        template.properties.isEmpty()
        template.dependencies.isEmpty()
        template.modules.isEmpty()
    }

    def "constructor with name sets name"() {
        when:
        def template = new Template('test-template')

        then:
        template.name == 'test-template'
        template.properties.isEmpty()
        template.dependencies.isEmpty()
        template.modules.isEmpty()
    }

    def "can set and get properties"() {
        given:
        def template = new Template('test')

        when:
        template.properties = ['key1': 'value1', 'key2': 'value2']

        then:
        template.properties.size() == 2
        template.properties['key1'] == 'value1'
        template.properties['key2'] == 'value2'
    }

    def "can add properties individually"() {
        given:
        def template = new Template('test')

        when:
        template.properties['prop1'] = 'val1'
        template.properties['prop2'] = 'val2'

        then:
        template.properties.size() == 2
    }

    def "can set and get dependencies"() {
        given:
        def template = new Template('test')

        when:
        template.dependencies = ['dep1', 'dep2', 'dep3']

        then:
        template.dependencies.size() == 3
        template.dependencies.contains('dep1')
    }

    def "can add dependencies individually"() {
        given:
        def template = new Template('test')

        when:
        template.dependencies << 'dep1'
        template.dependencies << 'dep2'

        then:
        template.dependencies.size() == 2
    }

    def "can set and get modules"() {
        given:
        def template = new Template('test')

        when:
        template.modules = ['mod1', 'mod2']

        then:
        template.modules.size() == 2
        template.modules.contains('mod1')
    }

    def "can add modules individually"() {
        given:
        def template = new Template('test')

        when:
        template.modules << 'mod1'
        template.modules << 'mod2'

        then:
        template.modules.size() == 2
    }

    def "toString returns formatted string"() {
        given:
        def template = new Template('my-template')
        template.source = 'file:/path/to/template'
        template.properties = ['key': 'value']
        template.dependencies = ['dep1', 'dep2']
        template.modules = ['mod1']

        when:
        def result = template.toString()

        then:
        result.contains('my-template')
        result.contains('file:/path/to/template')
        result.contains('properties=1')
        result.contains('dependencies=2')
        result.contains('modules=1')
    }

    def "can set source"() {
        given:
        def template = new Template('test')

        when:
        template.source = 'resources'

        then:
        template.source == 'resources'
    }
}
