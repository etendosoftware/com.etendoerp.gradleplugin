package com.etendoerp.core

import com.etendoerp.EtendoPluginExtension
import com.etendoerp.jars.JarCoreGenerator
import com.etendoerp.jars.modules.metadata.DependencyUtils
import com.etendoerp.legacy.dependencies.EtendoArtifactMetadata
import com.etendoerp.legacy.dependencies.container.DependencyType
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency

/**
 * Class used to store Core information
 *
 * Type: SOURCES - JAR - UNDEFINED
 * SOURCES: The core is in SOURCES when 'modules_core' and 'src-core' directories exists.
 * JAR: The core is in JAR when the directories 'modules_core' and 'src-core' does not exists,
 * and when the core dependency is defined in the build.gradle of the main project (implementation 'com.etendoerp.platform:etendo-core)
 * UNDEFINED: When the core is not in SOURCES or JAR
 *
 * Status: RESOLVED - UNRESOLVED - TOBERESOLVED
 * RESOLVED: When the core is in SOURCES is automatically resolved,
 * or in JAR and has been extracted (contains the 'build.xml' in the 'build/etendo' directory)
 * UNRESOLVED: When the core is not in SOURCES or JAR
 * TOBERESOLVED: When the core is in JAR (contains the Etendo core dependency but is not already extracted)
 *
 * coreVersion: Used to perform the resolution version conflicts.
 *
 */
class CoreMetadata {

    public final static String CLASSIC_ETENDO_CORE_GROUP = "com.smf.classic.core"
    public final static String CLASSIC_ETENDO_CORE_NAME  = "ob"

    public final static String DEFAULT_ETENDO_CORE_GROUP = "com.etendoerp.platform"
    public final static String DEFAULT_ETENDO_CORE_NAME  = "etendo-core"

    Project project
    CoreType coreType
    CoreStatus coreStatus

    boolean supportJars

    String coreGroup
    String coreName
    String coreVersion
    String coreId

    Dependency coreDependency

    CoreMetadata(Project project) {
        this.project = project
        this.loadMetadata()
    }

    CoreMetadata(Project project, boolean load) {
        this.project
    }

    void loadMetadata() {
        // Resolve core type
        resolveCoreType()

        // Resolve core status
        resolveCoreStatus()

        // Resolve core id
        resolveCoreId()

        // Resolve support jars
        resolveCoreSupportJars()

        inform()
    }

    void inform() {
        project.logger.info("*****************************************************")
        project.logger.info("Defined core: ${this.coreGroup}:${this.coreName}")
        project.logger.info("Core version: ${this.coreVersion}")
        project.logger.info("Core type: ${this.coreType}")
        project.logger.info("Core status: ${this.coreStatus}")
        project.logger.info("Core support JARs: ${this.supportJars}")
        project.logger.info("*****************************************************")
    }

    boolean loadMetadataFromPropertiesFile(String parentLocation) {
        EtendoArtifactMetadata etendoArtifactMetadata = new EtendoArtifactMetadata(project, DependencyType.ETENDOCOREJAR)
        def status = etendoArtifactMetadata.loadMetadataFile(parentLocation)

        if (!status) {
            project.logger.info("The core metadata information could not be loaded from the '${EtendoArtifactMetadata.METADATA_FILE}' file")
            return false
        }

        this.coreGroup = etendoArtifactMetadata.group
        this.coreName = etendoArtifactMetadata.name
        this.coreVersion = etendoArtifactMetadata.version

        loadCoreMetadataId()
        return true
    }

    void loadMetadataFromExtension() {
        def extension = project.extensions.findByType(EtendoPluginExtension)
        this.coreGroup = extension.coreGroup
        this.coreName = extension.coreName
        this.coreVersion = extension.coreVersion
        loadCoreMetadataId()
    }

    // Used when the core is in JARs and the 'artifact.properties' file does not exists
    void loadMetadataFromCoreDependency() {
        if (this.coreDependency) {
            this.coreGroup = this.coreDependency.group
            this.coreName = this.coreDependency.name
            this.coreVersion = this.coreDependency.version
            loadCoreMetadataId()
        }
    }

    void loadCoreMetadataId() {
        this.coreId = "${this.coreGroup}:${this.coreName}:${this.coreVersion}"
    }

    boolean resolveCoreSupportJars() {
        boolean supportJars = false

        if (this.coreType == CoreType.JAR) {
            supportJars = true
        } else if (this.coreType == CoreType.SOURCES && this.coreStatus == CoreStatus.RESOLVED) {
            def extension = project.extensions.findByType(EtendoPluginExtension)
            supportJars = extension.supportJars
        }

        this.supportJars = supportJars
        return supportJars
    }

    int parseCoreVersion(String version) {
        int versionNumber = 0

        if (!version) {
            project.logger.error("The version is not defined.")
            return versionNumber
        }

        try {
            String versionPrefix = version
            if (version.contains(".")) {
                def versionSplit = version?.split("\\.")
                if (versionSplit.size() >= 1) {
                    versionPrefix = versionSplit[0]
                }
            }
            versionNumber = versionPrefix.toInteger()
        } catch (Exception e) {
            project.logger.info("Error parsing the core version")
            project.logger.info(e.getMessage())
        }
        return versionNumber
    }

    String resolveCoreId() {
        if (this.coreType == CoreType.SOURCES) {
            getResolvedSourcesCoreId()
        } else if (this.coreType == CoreType.JAR) {
            if (this.coreStatus == CoreStatus.RESOLVED) {
                getResolvedJarCoreId()
            } else {
                getUnresolvedJarCoreId()
            }
        } else {
            getUnresolvedSourcesCoreId()
        }
    }

    String getResolvedSourcesCoreId() {
        File root = project.rootDir
        // Try to load the metadata from the artifact.properties file
        def loaded = loadMetadataFromPropertiesFile(root.absolutePath)

        // Load the metadata from the plugin extension
        if (!loaded) {
            loadMetadataFromExtension()
        }

        return this.coreId
    }

    String getResolvedJarCoreId() {
        File root = new File(project.buildDir, "etendo")
        // Try to load the metadata from the artifact.properties file
        def loaded = loadMetadataFromPropertiesFile(root.absolutePath)

        // Load the metadata from the core Dependency
        if (!loaded) {
            loadMetadataFromCoreDependency()
        }

        return this.coreId
    }

    // Use the plugin extension
    String getUnresolvedSourcesCoreId() {
        loadMetadataFromExtension()
        return this.coreId
    }

    // Use the plugin extension or defined version
    String getUnresolvedJarCoreId() {
        loadMetadataFromCoreDependency()
        return this.coreId
    }

    CoreType resolveCoreType() {
        CoreType type

        if (isCoreInSources()) {
            type = CoreType.SOURCES
        } else if (isCoreInJars()) {
            type = CoreType.JAR
            this.coreDependency = getCoreDependency(this.project)
        } else {
            type = CoreType.UNDEFINED
        }

        this.coreType = type
        return type
    }

    CoreStatus resolveCoreStatus() {
        CoreStatus status

        if (this.coreType == CoreType.SOURCES) {
            // The core is in sources when contains 'src-core' and 'modules_core' directories
            status = CoreStatus.RESOLVED
        } else if (this.coreType == CoreType.JAR) {
            // Check if the core in JAR is already resolved
            if (coreInJarResolved()) {
                status = CoreStatus.RESOLVED
            } else {
                status = CoreStatus.TOBERESOLVED
            }
        } else {
            status = CoreStatus.UNRESOLVED
        }

        this.coreStatus = status
        return status
    }

    /**
     * The core in Jars is resolved when the 'build.xml' file is in the 'build/etendo' directory
     * @return
     */
    boolean coreInJarResolved() {
        def buildFile = new File(project.buildDir.absolutePath + File.separator + 'etendo' + File.separator + 'build.xml')
        return buildFile.exists()
    }

    boolean isCoreInSources() {
        def modulesCoreLocation = project.file("modules_core")
        def srcCoreLocation = project.file("src-core")
        return modulesCoreLocation.exists() && srcCoreLocation.exists()
    }

    // Sources have priority over jars
    boolean isCoreInJars() {
        return !isCoreInSources() && containsCoreDependency()
    }

    boolean containsCoreDependency() {
        Dependency coreDependency = getCoreDependency(this.project)

        return coreDependency != null
    }


    static Dependency getCoreDependency(Project project){
        Dependency coreDependency = null

        def baseProjectConfigurations = DependencyUtils.loadListOfConfigurations(project)
        for (Configuration configuration : baseProjectConfigurations) {
            for (Dependency dependency : configuration.allDependencies) {
                if (dependency.name == JarCoreGenerator.ETENDO_CORE) {
                    coreDependency = dependency
                }
            }
        }
        return coreDependency
    }

}
