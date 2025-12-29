package com.etendoerp.legacy.dependencies

import com.etendoerp.jars.FileExtensions
import com.etendoerp.jars.PathUtils
import com.etendoerp.legacy.dependencies.container.DependencyType
import com.etendoerp.legacy.utils.ModulesUtils
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

    final static String SRC_DB     = "src-db"
    final static String DATABASE   = "database"
    final static String SOURCEDATA = "sourcedata"
    final static String AD_MODULE   = "AD_MODULE"

    final static String JAVAPACKAGE = "javapackage"
    final static String GROUP       = "group"
    final static String NAME        = "name"
    final static String VERSION     = "version"

    final static String AD_MODULE_LOCATION = "${SRC_DB}${File.separator}${DATABASE}${File.separator}${SOURCEDATA}${File.separator}${AD_MODULE}${FileExtensions.XML}"

    Project project
    String group
    String name
    String version
    String javaPackage
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

    boolean loadMetadataUsingXML(String locationPath) {
        if (!locationPath) {
            project.logger.error("The location path to load the Etendo Artifact properties from the XML file is not defined. Type: ${this.type.toString()}")
            return false
        }

        File locationFile = new File(locationPath)

        if (!locationFile.exists()) {
            return false
        }

        String XMLLocation = PathUtils.createPath(
                locationPath,
                SRC_DB,
                DATABASE,
                SOURCEDATA
        ).concat(AD_MODULE).concat(FileExtensions.XML)

        return loadMetadataFromXML(XMLLocation)
    }

    boolean loadMetadataFromXML(String locationXML) {
        if (!locationXML) {
            project.logger.error("The location path to load the Etendo Artifact properties from the XML file is not defined. Type: ${this.type.toString()}")
            return false
        }

        File locationFile = new File(locationXML)

        if (!locationFile || !locationFile.exists()) {
            project.logger.debug("The XML file '${locationXML}' does not exists. Type: ${this.type.toString()}")
            return false
        }

        def ad_module = new XmlParser().parse(locationFile)

        def moduleNode = ad_module[AD_MODULE]
        javaPackage = moduleNode[JAVAPACKAGE.toUpperCase()].text()
        version     = moduleNode[VERSION.toUpperCase()].text()

        group = splitGroup(project, javaPackage)
        name  = splitArtifact(project, javaPackage)

        return true
    }

    static String splitGroup(Project project, String javaPackage){
        String group = ""
        try {
            ArrayList<String> parts = javaPackage.split('\\.')
            group = parts[0]+"."+parts[1]
        } catch (Exception e) {
            project.logger.debug("Error splitting the group")
            project.logger.debug("ERROR: ${e.message}")
        }
        return group
    }

    static String splitArtifact(Project project, String javaPackage){
        String artifact = ""
        try {
            ArrayList<String> parts = javaPackage.split("\\.")
            if (parts.size() >= 2) {
                parts = parts.subList(2, parts.size())
                return parts.join(".")
            }
        } catch (Exception e) {
            project.logger.debug("Error splitting the artifact")
            project.logger.debug("ERROR: ${e.message}")
        }
        return artifact
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
            project.logger.debug("The Etendo Artifact Properties in '${locationPath}' does not exists. Type: ${this.type.toString()}")
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
            return false
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
