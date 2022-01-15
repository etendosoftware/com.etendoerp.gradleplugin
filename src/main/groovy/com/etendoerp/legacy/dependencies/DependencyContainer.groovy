package com.etendoerp.legacy.dependencies

import com.etendoerp.EtendoPluginExtension
import com.etendoerp.core.CoreMetadata
import com.etendoerp.core.CoreType
import com.etendoerp.jars.modules.metadata.DependencyUtils
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.DependencySet

/**
 * Class used to contain the Maven and Etendo dependencies.
 * The dependencies are mapped to JARs files.
 * The JARs files could be used by Ant (added to the classpath) and copied to the WebContent dir.
 *
 * Depending of the Core type and version different logic is applied.
 *
 * -Core in SOURCES
 *  JARs not supported:
 *      Only the maven dependencies should be resolved and returned to be used by the Ant classpath.
 *  JARs supported:
 *      Maven and Etendo dependencies should be returned.
 *      Resolution conflicts should be performed.
 *      To perform the resolution the current Etendo core sources version should be used.
 *      Etendo JARs modules will be extracted.
 *
 * -Core in JARs.
 *      Maven and Etendo dependencies should be returned.
 *      Resolution conflicts should be performed.
 *      Etendo JARs modules will be extracted.
 *      Etendo core JAR will be extracted in case of new version.
 *
 */
class DependencyContainer {

    final static String RESOLUTION_CONTAINER = "resolutionContainer"

    Project project
    CoreMetadata coreMetadata

    // Map containing has key the module name
    Map<String, ArtifactDependency> mavenDependenciesFiles
    Map<String, ArtifactDependency> etendoDependenciesJarFiles
    Map<String, ArtifactDependency> etendoDependenciesZipFiles
    Map<String, Dependency> dependenciesMap

    ArtifactDependency etendoCoreDependencyFile

    DependencyContainer(Project project, CoreMetadata coreMetadata) {
        this.project = project
        this.coreMetadata = coreMetadata
        this.mavenDependenciesFiles     = new HashMap<>()
        this.etendoDependenciesJarFiles = new HashMap<>()
        this.etendoDependenciesZipFiles = new HashMap<>()
    }

    /**
     * Process the defined dependencies obtaining the corresponding JARs files.
     * The obtained files will be added to the Ant classpath and copied to the WebContent dir.
     * @return List<File> List of JARs files
     */
    List<File> processJarFiles() {
        List<File> dependencies = []
        List<File> mavenDependenciesFiles = []
        List<File> etendoDependenciesFiles = []

        this.mavenDependenciesFiles = new HashMap<>()
        this.etendoDependenciesJarFiles = new HashMap<>()
        this.etendoDependenciesZipFiles = new HashMap<>()
        this.dependenciesMap = new HashMap<>()

        def extension = project.extensions.findByType(EtendoPluginExtension)

        def performResolutionConflicts = extension.performResolutionConflicts
        def applyDependenciesToMainProject = extension.applyDependenciesToMainProject

        if (coreMetadata.coreType == CoreType.SOURCES ) {
            if (coreMetadata.supportJars) {
                // Resolve and extract Etendo modules
                loadDependenciesFiles(true, performResolutionConflicts, true)
                etendoDependenciesFiles.addAll(collectDependenciesFiles(this.etendoDependenciesJarFiles, applyDependenciesToMainProject))
            } else {
                // The core does not support Jars, ignore performing resolution conflicts.
                loadDependenciesFiles(false, false, true)
            }
            mavenDependenciesFiles.addAll(collectDependenciesFiles(this.mavenDependenciesFiles, applyDependenciesToMainProject))
        }

        if (coreMetadata.coreType == CoreType.JAR) {
            // When the core is in JAR the Etendo core dependency will be already applied
            loadDependenciesFiles(false, performResolutionConflicts, false)

            // Collect the core dependency
            etendoDependenciesFiles.add(collectCoreJarDependency())

            // Collect module dependency
            etendoDependenciesFiles.addAll(collectDependenciesFiles(this.etendoDependenciesJarFiles, applyDependenciesToMainProject))
            mavenDependenciesFiles.addAll(collectDependenciesFiles(this.mavenDependenciesFiles, applyDependenciesToMainProject))
        }

        dependencies.addAll(mavenDependenciesFiles)
        dependencies.addAll(etendoDependenciesFiles)
        return dependencies
    }

    Map<String, ArtifactDependency> performResolutionConflict(Configuration container, boolean addCoreToResolution, boolean filterCoreDependency) {
        // Create a temporal configuration container used to perform resolution conflicts
        def resolutionContainer = project.configurations.create(RESOLUTION_CONTAINER)

        List<Configuration> configurationsToLoad = new ArrayList<>()
        configurationsToLoad.add(container)
        // Add the core dependency when is in Sources and supports Jars
        if (addCoreToResolution) {
            def group = coreMetadata.coreGroup
            def name = coreMetadata.coreName
            def version = coreMetadata.coreVersion
            def core = "${group}:${name}:${version}"
            project.dependencies.add(RESOLUTION_CONTAINER, core)
        }

        DependencySet resolutionDependencySet = resolutionContainer.dependencies

        // Load source modules dependencies
        Configuration sourceModules = ResolutionUtils.loadSourceModulesDependenciesResolution(project)
        configurationsToLoad.add(sourceModules)

        // Load all the dependencies from the container and sourceModules to the created resolutionContainer
        DependencyUtils.loadDependenciesFromConfigurations(configurationsToLoad, resolutionDependencySet)

        // Perform the resolution conflict versions
        return ResolutionUtils.dependenciesResolutionConflict(project, resolutionContainer, filterCoreDependency)
    }

    /**
     * Collect all the external dependencies files defined by the user.
     * Filters Maven and Etendo dependencies.
     *
     * @param addCoreToResolution Adds the core dependency when the resolution version conflict is performed
     * @param performResolutionConflicts Perform the resolution version conflict
     * @param filterCoreDependency filter the core dependency to prevent download a new version.(When core in SOURCES)
     */
    void loadDependenciesFiles(boolean addCoreToResolution, boolean performResolutionConflicts ,boolean filterCoreDependency) {
        // Load all project and subproject dependencies in a custom configuration container
        Configuration container = ResolverDependencyUtils.loadAllDependencies(project)

        if (performResolutionConflicts) {
            def artifactDependencies = performResolutionConflict(container, addCoreToResolution, filterCoreDependency)
            container = ResolverDependencyUtils.updateConfigurationDependencies(project, container, artifactDependencies, filterCoreDependency, false)
        }

        if (filterCoreDependency) {
            ResolverDependencyUtils.excludeCoreDependencies(project, container)
        }

        // Filter maven and Etendo dependencies
        ResolverDependencyUtils.filterDependenciesFiles(project, container, this)
    }

    /**
     * Collect the files mapped to a dependency (JARs files).
     * Etendo JARs files are extracted.
     * @param dependenciesFiles
     * @param applyDependencyToMainProject
     * @return
     */
    List<File> collectDependenciesFiles(Map<String, ArtifactDependency> dependenciesFiles, boolean applyDependencyToMainProject) {
        List<File> collection = []
        List<Dependency> dependencies = []

        dependenciesFiles.each {
            ArtifactDependency artifactDependency = it.value
            collection.add(artifactDependency.locationFile)
            Dependency dependency = artifactDependency.dependency
            if (dependency) {
                dependencies.add(dependency)
            }

            // Extract Etendo dependency
            if (artifactDependency.type == DependencyType.ETENDOJARMODULE) {
                artifactDependency.extract()
            }
        }

        if (applyDependencyToMainProject) {
            applyDependenciesToMainProject(dependencies)
        }

        return collection
    }

    /**
     * Collects and extracts the Etendo core JAR dependency
     * @return
     */
    File collectCoreJarDependency() {
        if (!this.etendoCoreDependencyFile) {
            throw new IllegalArgumentException("Error collecting the Etendo core JAR file")
        }

        this.etendoCoreDependencyFile.extract()
        return this.etendoCoreDependencyFile.locationFile
    }

    /**
     * Hack to load all the project and subproject dependencies to the 'root' project.
     * This allow defining dependencies in the 'build.gradle' file of submodules and being recognized
     * in all the project, simulating the legacy behavior.
     *
     * Only the major version of a dependency will be used, this is because the project sets all the
     * 'modules' in the main 'sourceSets', making the project and subprojects act like one project.
     *
     * PROS: If the project is considered like only one, there is not 'circular dependencies'.
     *
     * CONS: If two modules are using the same library with different version, the major one is taking
     * into account.
     *
     */
    void applyDependenciesToMainProject(List<Dependency> dependencies) {
        for (Dependency dependency : dependencies) {
            if (dependency) {
                project.dependencies {
                    implementation(dependency)
                }
            }
        }
    }


}
