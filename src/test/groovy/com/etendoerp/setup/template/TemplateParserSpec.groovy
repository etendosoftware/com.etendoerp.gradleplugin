package com.etendoerp.setup.template

import spock.lang.Specification

/**
 * Tests for TemplateParser
 */
class TemplateParserSpec extends Specification {

    def "parse creates template with name"() {
        given:
        def content = """
[properties]
key=value
"""

        when:
        def template = TemplateParser.parse(content, 'test-template')

        then:
        template != null
        template.name == 'test-template'
    }

    def "parse handles empty content"() {
        when:
        def template = TemplateParser.parse('', 'empty')

        then:
        template != null
        template.name == 'empty'
        template.properties.isEmpty()
        template.dependencies.isEmpty()
        template.modules.isEmpty()
    }

    def "parse handles comments"() {
        given:
        def content = """
# This is a comment
[properties]
# Another comment
key=value
"""

        when:
        def template = TemplateParser.parse(content, 'test')

        then:
        template.properties.size() == 1
        template.properties['key'] == 'value'
    }

    def "parse extracts properties correctly"() {
        given:
        def content = """
[properties]
prop1=value1
prop2=value2
prop3=value3
"""

        when:
        def template = TemplateParser.parse(content, 'test')

        then:
        template.properties.size() == 3
        template.properties['prop1'] == 'value1'
        template.properties['prop2'] == 'value2'
        template.properties['prop3'] == 'value3'
    }

    def "parse handles properties with equals in value"() {
        given:
        def content = """
[properties]
url=http://example.com?param=value&other=test
"""

        when:
        def template = TemplateParser.parse(content, 'test')

        then:
        template.properties['url'] == 'http://example.com?param=value&other=test'
    }

    def "parse extracts dependencies correctly"() {
        given:
        def content = """
[dependencies]
com.example:lib1:1.0.0
com.example:lib2:2.0.0
org.test:module:3.0.0
"""

        when:
        def template = TemplateParser.parse(content, 'test')

        then:
        template.dependencies.size() == 3
        template.dependencies.contains('com.example:lib1:1.0.0')
        template.dependencies.contains('com.example:lib2:2.0.0')
        template.dependencies.contains('org.test:module:3.0.0')
    }

    def "parse extracts modules correctly"() {
        given:
        def content = """
[modules]
com.example:module1:1.0.0
git::https://github.com/user/repo.git::branch=main
"""

        when:
        def template = TemplateParser.parse(content, 'test')

        then:
        template.modules.size() == 2
        template.modules[0] == 'com.example:module1:1.0.0'
        template.modules[1] == 'git::https://github.com/user/repo.git::branch=main'
    }

    def "parse handles multiple sections"() {
        given:
        def content = """
[properties]
key1=value1

[dependencies]
dep1

[modules]
mod1
"""

        when:
        def template = TemplateParser.parse(content, 'test')

        then:
        template.properties.size() == 1
        template.dependencies.size() == 1
        template.modules.size() == 1
    }

    def "parse throws exception for unknown section"() {
        given:
        def content = """
[unknown]
something
"""

        when:
        TemplateParser.parse(content, 'test')

        then:
        thrown(IllegalArgumentException)
    }

    def "parse throws exception for invalid property format"() {
        given:
        def content = """
[properties]
invalid-no-equals
"""

        when:
        TemplateParser.parse(content, 'test')

        then:
        thrown(IllegalArgumentException)
    }

    def "parse handles empty lines"() {
        given:
        def content = """
[properties]

key1=value1

key2=value2

"""

        when:
        def template = TemplateParser.parse(content, 'test')

        then:
        template.properties.size() == 2
    }

    def "parse trims whitespace from property keys and values"() {
        given:
        def content = """
[properties]
  key1  =  value1  
key2=value2
"""

        when:
        def template = TemplateParser.parse(content, 'test')

        then:
        template.properties['key1'] == 'value1'
        template.properties['key2'] == 'value2'
    }
}
