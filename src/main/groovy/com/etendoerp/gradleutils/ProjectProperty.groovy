package com.etendoerp.gradleutils

/**
 * Class used to store and maintain a register of all the project and subproject properties (project.ext values)
 */
enum ProjectProperty {

    /**
     *  Property used to store the source modules dependencies of a project
     */
    SOURCE_MODULES_DEPENDENCY ("SOURCE_MODULES_DEPENDENCY"),

    /**
     * Property used to store the valid projects (with the 'group' and 'artifact' properties in the build.gradle)
     */
    VALID_SOURCE_MODULES ("VALID_SOURCE_MODULES"),

    /**
     * Property used to store a map of the subprojects names "group:artifact" and the subproject itself
     */
    SOURCE_MODULES_MAP("SOURCE_MODULES_MAP")

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
