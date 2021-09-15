package com.etendoerp.publication.buildfile

import com.etendoerp.jars.FileExtensions
import com.etendoerp.jars.PathUtils
import com.etendoerp.legacy.utils.ModulesUtils
import com.etendoerp.publication.PublicationUtils
import org.gradle.api.Project


/**
 * Class used to contain all the information of the AD_MODULE.xml file from a Etendo module.
 */
class BuildMetadata {

    final static String SRC_DB     = "src-db"
    final static String DATABASE   = "database"
    final static String SOURCEDATA = "sourcedata"
    final static String AD_MODULE  = "AD_MODULE"

    final static String JAVAPACKAGE   = "javapackage"
    final static String GROUP         = "group"
    final static String VERSION       = "version"
    final static String DESCRIPTION   = "description"
    final static String REPOSITORY    = "repository"
    final static String CONFIGURATION = "configuration"
    
    Project project
    String javaPackage
    String version
    String description
    String group
    String repository
    String srcFile

    String moduleName
    String moduleLocation

    BuildMetadata(Project project, String moduleName, String repositoryName) {
        this.project = project
        this.moduleName = moduleName
        this.repository = "${PublicationUtils.BASE_REPOSITORY_URL}$repositoryName"
        loadModuleLocation(moduleName)
        loadSrcFile()
        loadBuildMetadata()
    }

    void loadModuleLocation(String moduleName) {
        moduleLocation = PathUtils.createPath(
                project.rootDir.absolutePath,
                PublicationUtils.BASE_MODULE_DIR,
                moduleName
        )

        if(!project.file(moduleLocation).exists()) {
            throw new IllegalArgumentException("The module '${moduleLocation}' does not exists.")
        }
    }

    void loadSrcFile() {
        srcFile = PathUtils.createPath(
                moduleLocation,
                SRC_DB,
                DATABASE,
                SOURCEDATA
        ).concat(AD_MODULE).concat(".${FileExtensions.XML}")

        if (!project.file(srcFile).exists()) {
            throw new IllegalArgumentException("The source file '${srcFile}' does not exists.")
        }
    }

    void loadBuildMetadata() {
        def ad_module = new XmlParser().parse(srcFile)

        def moduleNode = ad_module[AD_MODULE]

        javaPackage = moduleNode[JAVAPACKAGE.toUpperCase()].text()
        version     = moduleNode[VERSION.toUpperCase()].text()
        description = moduleNode[DESCRIPTION.toUpperCase()].text()

        group =  ModulesUtils.splitGroup(javaPackage)

    }

    /**
     * Generates a map of the properties used to fill the 'build.gradle.template' file.
     * @return
     */
    Map<String, ?> generatePropertiesMap() {
        Map<String, ?> map = new HashMap()

        map.put(GROUP       , group)
        map.put(VERSION     , version)
        map.put(DESCRIPTION , description)
        map.put(REPOSITORY  , repository)
        map.put(CONFIGURATION , PublicationUtils.CONFIGURATION_NAME)

        return  map
    }

}
