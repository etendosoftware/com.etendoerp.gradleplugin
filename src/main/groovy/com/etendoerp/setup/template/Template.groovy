package com.etendoerp.setup.template

/**
 * Represents a template with its configuration sections
 */
class Template {
    String name
    String source
    Map<String, String> properties = [:]
    /** Ordered list of property keys and comment lines as they appear in the template. */
    List<String> propertyOrder = []
    List<String> dependencies = []
    List<String> modules = []

    Template() {}

    Template(String name) {
        this.name = name
    }

    @Override
    String toString() {
        return "Template[name=${name}, source=${source}, properties=${properties.size()}, dependencies=${dependencies.size()}, modules=${modules.size()}]"
    }
}
