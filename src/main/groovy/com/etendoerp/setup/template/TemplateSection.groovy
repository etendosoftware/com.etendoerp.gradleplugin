package com.etendoerp.setup.template

/**
 * Enum representing the different sections in a template file
 */
enum TemplateSection {
    PROPERTIES('properties'),
    DEPENDENCIES('dependencies'),
    MODULES('modules')

    final String sectionName

    TemplateSection(String sectionName) {
        this.sectionName = sectionName
    }

    static TemplateSection fromString(String name) {
        return values().find { it.sectionName == name }
    }
}
