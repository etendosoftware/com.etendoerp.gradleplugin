package com.etendoerp.publication.buildfile

import com.etendoerp.core.CoreMetadata
import com.etendoerp.jars.FileExtensions
import com.etendoerp.jars.PathUtils
import com.etendoerp.jars.modules.metadata.DependencyUtils
import com.etendoerp.legacy.utils.DependenciesUtils
import com.etendoerp.legacy.utils.ModulesUtils
import com.etendoerp.publication.PublicationUtils
import com.etendoerp.publication.configuration.pom.PomConfigurationContainer
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.api.internal.artifacts.dependencies.DefaultExternalModuleDependency
import org.w3c.dom.NodeList

import java.time.Instant

/**
 * Class used to contain all the information of the AD_MODULE.xml file from a Etendo module.
 */
class BuildMetadata {

    static final String SRC_DB                 = "src-db"
    static final String DATABASE               = "database"
    static final String SOURCEDATA             = "sourcedata"
    static final String AD_MODULE              = "AD_MODULE"
    static final String AD_MODULE_DEPENDENCY   = "AD_MODULE_DEPENDENCY"
    static final String AD_MODULE_ID           = "AD_MODULE_ID"
    static final String AD_DEPENDENT_MODULE_ID = "AD_DEPENDENT_MODULE_ID"

    static final String JAVAPACKAGE   = "javapackage"
    static final String GROUP         = "group"
    static final String ARTIFACT      = "artifact"
    static final String VERSION       = "version"
    static final String DESCRIPTION   = "description"
    static final String REPOSITORY    = "repository"
    static final String CONFIGURATION = "configuration"
    static final String DEPENDENCIES  = "dependencies"
    static final String MODULENAME    = "modulename"

    /**
     * Name of properties that the used can pass by the command line
     */
    static final String CORE_GROUP_PROPERTY   = "coreGroup"
    static final String CORE_NAME_PROPERTY    = "coreName"
    static final String CORE_VERSION_PROPERTY = "coreVersion"

    /**
     *
     */
    static final String DEFAULT_CORE_VERSION_DEPENDENCY = "[22.1.0, 22.1.1)"

    // Properties used to fill the build.gradle.template
    static final String DATE = "date"
    static final String TASK = "task"

    Project project
    String javaPackage
    String version
    String description
    String group
    String artifact
    String adModuleId
    String repository
    String srcFile

    String moduleName
    String moduleLocation

    File buildGradleTemplateFile
    Map<String, BuildMetadata> subprojectDependencies
    BuildMetadataContainer buildMetadataContainer

    /**
     * Flag used to search the subproject dependencies from the 'AD_MODULE_DEPENDENCY.xml' file
     */
    boolean processSubprojectDependencies = false

    boolean addCoreDependency = false

    BuildMetadata(Project project, String moduleName, String repositoryName, File buildGradleTemplateFile) {
        this.project = project
        this.moduleName = moduleName
        this.repository = "${PublicationUtils.BASE_REPOSITORY_URL}$repositoryName"
        this.buildGradleTemplateFile = buildGradleTemplateFile
        this.subprojectDependencies = new TreeMap<>(String.CASE_INSENSITIVE_ORDER)
        loadModuleLocation(moduleName)
        loadSrcFile()
        loadBuildMetadata()
    }

    BuildMetadata(Project project, String moduleName, String repositoryName, File buildGradleTemplateFile, boolean processSubprojectDependencies) {
        this(project, moduleName, repositoryName, buildGradleTemplateFile)
        this.processSubprojectDependencies = processSubprojectDependencies
    }

    BuildMetadata(Project project, String moduleName, String repositoryName, File buildGradleTemplateFile, boolean processSubprojectDependencies, BuildMetadataContainer buildMetadataContainer) {
        this(project, moduleName, repositoryName, buildGradleTemplateFile, processSubprojectDependencies)
        this.buildMetadataContainer = buildMetadataContainer
    }

    String getCoreGroup() {
        String group = this.project.findProperty(CORE_GROUP_PROPERTY)
        if (group == null) {
            group = CoreMetadata.DEFAULT_ETENDO_CORE_GROUP
        }
        return group
    }

    String getCoreName() {
        String name = this.project.findProperty(CORE_NAME_PROPERTY)
        if (name == null) {
            name = CoreMetadata.DEFAULT_ETENDO_CORE_NAME
        }
        return name
    }

    String getCoreVersion() {
        String version = this.project.findProperty(CORE_VERSION_PROPERTY)
        if (version == null) {
            version = DEFAULT_CORE_VERSION_DEPENDENCY
        }
        return version
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
        adModuleId  = moduleNode[AD_MODULE_ID.toUpperCase()].text()

        group    = ModulesUtils.splitGroup(javaPackage)
        artifact = ModulesUtils.splitArtifact(javaPackage)
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
        map.put(ARTIFACT      , artifact)
        map.put(VERSION       , version)
        map.put(DESCRIPTION   , description)
        map.put(REPOSITORY    , repository)
        map.put(MODULENAME    , moduleName)

        def dependencies = DependenciesUtils.generatePomDependencies(project, moduleName, PublicationUtils.CONFIGURATION_NAME)

        if (processSubprojectDependencies) {
            dependencies += generateSubprojectDependencies()
        }

        if (this.addCoreDependency) {
            dependencies += generateCoreDependencies()
        }
        map.put(DEPENDENCIES  , dependencies)
        map.put(CONFIGURATION, PublicationUtils.MODULE_DEPENDENCY_CONTAINER)

        return  map
    }

    String generateCoreDependencies() {
        return "   ${DependencyUtils.IMPLEMENTATION}('${getCoreGroup()}:${getCoreName()}:${getCoreVersion()}') \n"
    }

    void loadSubprojectDependencies() {
        String adModuleDependencyPath = PathUtils.createPath(
                moduleLocation,
                SRC_DB,
                DATABASE,
                SOURCEDATA
        ).concat(AD_MODULE_DEPENDENCY).concat(FileExtensions.XML)

        File adModuleDependencyFile = new File(adModuleDependencyPath)

        if (!adModuleDependencyFile || !adModuleDependencyFile.exists()) {
            project.logger.info("* The '${AD_MODULE_DEPENDENCY}' file from the module '${moduleLocation}' does not exists.")
            return
        }

        // Get the dependencies declared in the AD_MODULE_DEPENDENCY.xml file
        def ad_module_dependency = new XmlParser().parse(adModuleDependencyFile)

        NodeList moduleDependencyNode = ad_module_dependency[AD_MODULE_DEPENDENCY] as NodeList
        moduleDependencyNode.each {dep ->
            String dependentModuleId = dep[AD_DEPENDENT_MODULE_ID]?.text()
            if (dependentModuleId && this.buildMetadataContainer.moduleSubprojectsMetadata.containsKey(dependentModuleId)) {
                BuildMetadata metadata = this.buildMetadataContainer.moduleSubprojectsMetadata.get(dependentModuleId)
                String name = "${metadata.group}.${metadata.artifact}"
                this.subprojectDependencies.put(name, metadata)
            }
        }
    }

    String generateSubprojectDependencies() {
        loadSubprojectDependencies()
        String dependencies = "\n"
        String configuration = PomConfigurationContainer.SUBPROJECT_DEPENDENCIES_CONFIGURATION_CONTAINER
        this.subprojectDependencies.each {
            BuildMetadata dependencyMetadata = it.value
            dependencies += "   ${configuration}('${dependencyMetadata.group}:${dependencyMetadata.artifact}:${dependencyMetadata.version}') \n"
        }
        return dependencies
    }

    void createBuildFile() {
        project.copy {
            from(buildGradleTemplateFile.absolutePath)
            into(this.moduleLocation)
            rename { String filename ->
                return ModuleBuildTemplateLoader.BUILD_FILE
            }
            expand(this.generatePropertiesMap())
        }
    }

}
