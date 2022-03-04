package com.etendoerp.legacy.dependencies

import com.etendoerp.legacy.dependencies.container.DependencyType
import org.gradle.api.Project

import java.time.Instant

/**
 * Class used to load or create the properties file of a Etendo Artifact.
 * The Etendo artifact could be a Etendo source module, the Etendo core JAR or Etendo core sources.
 * The created or loaded file is used to obtain the necessary information to perform resolution conflicts.
 */
class EtendoArtifactMetadata {

    final static String METADATA_FILE = "etendo.artifact.properties"

    final static String ARTIFACT = "artifact"
    final static String DATE     = "date"

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

    EtendoArtifactMetadata(Project project, DependencyType type, String group, String name, String version) {
        this(project, type)
        this.group = group
        this.name = name
        this.version = version
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
            project.logger.info("The Etendo Artifact Properties in '${locationPath}' does not exists. Type: ${this.type.toString()}")
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
            project.logger.error("The location '${locationPath}' does not exists. Type: ${this.type.toString()}")
            return false
        }

        def template = getClass().getClassLoader().getResourceAsStream("${METADATA_FILE}.template")

        if (!template) {
            project.logger.error("The ${METADATA_FILE} template does not exists.")
            return
        }

        def templateFile = File.createTempFile(METADATA_FILE,"template")
        templateFile.text = template.text

        Map<String, Object> properties = new HashMap<>()
        properties.put(ARTIFACT, this)
        properties.put(DATE, Instant.now().toString())

        project.copy {
            from(templateFile.absolutePath)
            into(locationFile)
            rename { String filename ->
                return METADATA_FILE
            }
            expand(properties)
        }

        return true
    }

}
