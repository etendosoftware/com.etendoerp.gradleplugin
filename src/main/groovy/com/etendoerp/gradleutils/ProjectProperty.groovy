package com.etendoerp.gradleutils

/**
 * Class used to store and maintain a register of all the project and subproject properties (project.ext values)
 */
enum ProjectProperty {
    SOURCE_MODULES_DEPENDENCY ("SOURCE_MODULES_DEPENDENCY")

    private final String property

    ProjectProperty(String property) {
        this.property = property
    }

    String getProperty() {
        return property
    }

    static boolean containsProperty(String property) {
        values()*.property.contains(property)
    }

    String toString() {
        property
    }
}
