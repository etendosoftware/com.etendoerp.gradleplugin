package com.etendoerp.setup.template

import spock.lang.Specification

/**
 * Tests for TemplateSection enum
 */
class TemplateSectionSpec extends Specification {

    def "enum has correct section names"() {
        expect:
        TemplateSection.PROPERTIES.sectionName == 'properties'
        TemplateSection.DEPENDENCIES.sectionName == 'dependencies'
        TemplateSection.MODULES.sectionName == 'modules'
    }

    def "fromString returns correct section"() {
        expect:
        TemplateSection.fromString('properties') == TemplateSection.PROPERTIES
        TemplateSection.fromString('dependencies') == TemplateSection.DEPENDENCIES
        TemplateSection.fromString('modules') == TemplateSection.MODULES
    }

    def "fromString returns null for unknown section"() {
        expect:
        TemplateSection.fromString('unknown') == null
        TemplateSection.fromString('invalid') == null
    }

    def "fromString is case sensitive"() {
        expect:
        TemplateSection.fromString('PROPERTIES') == null
        TemplateSection.fromString('Properties') == null
    }

    def "values returns all sections"() {
        when:
        def sections = TemplateSection.values()

        then:
        sections.length == 3
        sections.contains(TemplateSection.PROPERTIES)
        sections.contains(TemplateSection.DEPENDENCIES)
        sections.contains(TemplateSection.MODULES)
    }
}
