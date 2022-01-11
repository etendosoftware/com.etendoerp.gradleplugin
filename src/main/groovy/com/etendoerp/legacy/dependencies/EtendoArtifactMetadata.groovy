package com.etendoerp.legacy.dependencies

import org.gradle.api.Project

/**
 * Class used to load or create the properties file of a Etendo Artifact.
 * The Etendo artifact could be a Etendo source module, the Etendo core JAR or Etendo core sources.
 * The created or loaded file is used to obtain the necessary information to perform resolution conflicts.
 */
class EtendoArtifactMetadata {

    final static String METADATA_FILE = "etendo.artifact.properties"

    final static String GROUP_PROPERTY   = "artifact.group"
    final static String NAME_PROPERTY    = "artifact.name"
    final static String VERSION_PROPERTY = "artifact.version"
    final static String TYPE_PROPERTY    = "artifact.type"

    Project project
    String group
    String name
    String version
    File locationFile
    DependencyType type
    Properties propertiesMetadata

    EtendoArtifactMetadata(Project project, DependencyType type) {
        this.project = project
        this.type = type
    }

    boolean loadMetadataFile(String locationPath) {

        if (!locationPath) {
            project.logger.error("The location path to load the Etendo Artifact properties is not defined. Type: ${this.type.toString()}")
            return false
        }

        File locationFile = new File(locationPath)
        File propertiesLocation = null

        if (locationFile.exists()) {
            propertiesLocation = new File(locationFile, METADATA_FILE)
        }

        if (!propertiesLocation || !propertiesLocation.exists()) {
            project.logger.error("The Etendo Artifact Properties '${propertiesLocation.absolutePath}' does not exists. Type: ${this.type.toString()}")
            return false
        }

        this.locationFile = propertiesLocation
        def properties = new Properties()
        properties.load(new FileInputStream(propertiesLocation))
        this.propertiesMetadata = properties

        def propertiesToSearch = [GROUP_PROPERTY, NAME_PROPERTY, VERSION_PROPERTY]
        Map loadedProperties = loadProperties(propertiesToSearch)

        this.group   = loadedProperties.get(GROUP_PROPERTY)
        this.name    = loadedProperties.get(NAME_PROPERTY)
        this.version = loadedProperties.get(VERSION_PROPERTY)

        if (this.version == null) {
            return false
        }

        return true
    }

    Map loadProperties(List<String> propertiesToSearch) {
        Map propertiesValues = new HashMap()
        for (String property : propertiesToSearch) {
            if (this.propertiesMetadata == null || !this.propertiesMetadata.containsKey(property) || this.propertiesMetadata[property] == null) {
                project.logger.error("The file ${this.locationFile.absolutePath} does not contain the '${property}' property.")
                continue
            }
            propertiesValues.put(property, this.propertiesMetadata[property])
        }
        return propertiesValues
    }


    boolean createMetadataFile(String locationPath) {
        if (!locationPath) {
            project.logger.error("The location path to create the Etendo Artifact properties is not defined. Type: ${this.type.toString()}")
            return false
        }

        File locationFile = new File(locationPath)

        if (!locationFile.exists()) {
            project.logger.error("The location '${locationFile}' does not exists. Type: ${this.type.toString()}")
            return false
        }

        File propertiesFile = new File(locationFile, METADATA_FILE)
        propertiesFile.text = ""

        propertiesFile << """
        This file contains metadata information about the Etendo artifact to perform resolution conflicts.

        ${GROUP_PROPERTY}=${this.group}
        ${NAME_PROPERTY}=${this.name}
        ${VERSION_PROPERTY}=${this.version}
        ${TYPE_PROPERTY}=${this.type}
        """

        return true
    }

}
