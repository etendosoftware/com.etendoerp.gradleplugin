package com.etendoerp.publication.buildfile

import com.etendoerp.jars.FileExtensions
import com.etendoerp.jars.PathUtils
import com.etendoerp.legacy.utils.DependenciesUtils
import com.etendoerp.legacy.utils.ModulesUtils
import com.etendoerp.publication.PublicationUtils
import org.gradle.api.Project
import java.time.Instant

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
    final static String DEPENDENCIES  = "dependencies"

    // Properties used to fill the build.gradle.template
    final static String DATE = "date"
    final static String TASK = "task"

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
        ).concat(AD_MODULE).concat(FileExtensions.XML)

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

        map.put(TASK, ModuleBuildTemplateLoader.CREATE_MODULE_BUILD)
        map.put(DATE, Instant.now().toString())

        map.put(GROUP         , group)
        map.put(VERSION       , version)
        map.put(DESCRIPTION   , description)
        map.put(REPOSITORY    , repository)

        def dependencies = DependenciesUtils.generatePomDependencies(project, moduleName, PublicationUtils.CONFIGURATION_NAME)
        map.put(DEPENDENCIES  , dependencies)

        return  map
    }

}
