package com.etendoerp.publication.configuration.pom

enum PomConfigurationType {
    MULTIPLE_PUBLISH("MULTIPLE_PUBLISH",
            PomConfigurationContainer.SUBPROJECT_DEPENDENCIES_CONFIGURATION_CONTAINER,
            "INTERNAL_MULTIPLE_PUBLISH_CONFIGURATION_CONTAINER",
            "INTERNAL_MULTIPLE_PUBLISH_DEPENDENCIES_CONTAINER",
    ),

    private final String type
    private final String externalConfiguration
    private final String internalConfigurationProperty
    private final String internalDependenciesProperty

    PomConfigurationType(String type, String externalConfiguration, String internalConfigurationProperty, String internalDependenciesProperty) {
        this.type = type
        this.externalConfiguration = externalConfiguration
        this.internalConfigurationProperty = internalConfigurationProperty
        this.internalDependenciesProperty = internalDependenciesProperty
    }

    String getType() {
        return type
    }

    /**
     * Name of the configuration used to store a subproject dependency.
     * This configuration can be used by the users in the 'build.gradle' to specify
     * dependencies between subprojects (modules)
     * @return
     */
    String getExternalConfiguration() {
        return externalConfiguration
    }

    /**
     * Name of the configuration used to store the updated version of a subproject dependency
     * @return
     */
    String getInternalConfigurationProperty() {
        return internalConfigurationProperty
    }

    /**
     * Name of the property used to store in a subproject a Map of the dependencies between projects (modules)
     * @return
     */
    String getInternalDependenciesProperty() {
        return internalDependenciesProperty
    }

    static boolean containsExternalConfiguration(String externalConfiguration) {
        values()*.externalConfiguration.contains(externalConfiguration)
    }

    static boolean containsInternalDependencies(String internalDependenciesProperty) {
        values()*.internalDependenciesProperty.contains(internalDependenciesProperty)
    }

    static boolean containsInternalConfiguration(String internalConfigurationProperty) {
        values()*.internalConfigurationProperty.contains(internalConfigurationProperty)
    }

    static boolean containsType(String type) {
        values()*.type.contains(type)
    }

    String toString() {
        "${type} - ${externalConfiguration} - ${internalConfigurationProperty} - ${internalDependenciesProperty}"
    }
}