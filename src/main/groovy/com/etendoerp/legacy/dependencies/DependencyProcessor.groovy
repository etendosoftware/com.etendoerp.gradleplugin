package com.etendoerp.legacy.dependencies

import com.etendoerp.EtendoPluginExtension
import com.etendoerp.core.CoreMetadata
import com.etendoerp.core.CoreType
import com.etendoerp.jars.modules.metadata.DependencyUtils
import com.etendoerp.legacy.dependencies.container.ArtifactDependency
import com.etendoerp.legacy.dependencies.container.DependencyContainer
import com.etendoerp.legacy.dependencies.container.DependencyType
import com.etendoerp.legacy.dependencies.container.EtendoJarModuleArtifact
import com.etendoerp.modules.ModuleUtils
import com.etendoerp.modules.ModulesConfigurationUtils
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency

/**
 * Class used to contain and process the Maven and Etendo dependencies.
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
class DependencyProcessor {

    final static String RESOLUTION_CONTAINER = "resolutionContainer"

    Project project
    CoreMetadata coreMetadata
    DependencyContainer dependencyContainer

    DependencyProcessor(Project project, CoreMetadata coreMetadata) {
        this.project = project
        this.coreMetadata = coreMetadata
        this.dependencyContainer = new DependencyContainer(project, coreMetadata)
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

        def extension = project.extensions.findByType(EtendoPluginExtension)

        def performResolutionConflicts = extension.performResolutionConflicts
        def applyDependenciesToMainProject = extension.applyDependenciesToMainProject

        def rootProjectConfigurations = DependencyUtils.loadListOfConfigurations(project)

        // Clean the 'build/etendo/modules/' dir
        ModuleUtils.cleanBuildModules(project)

        if (coreMetadata.coreType == CoreType.SOURCES) {
            // Exclude from the root project the Core JAR dependency (included also from transitivity)
            rootProjectConfigurations.each {
                ResolverDependencyUtils.excludeCoreDependencies(project, it, true)
            }

            if (coreMetadata.supportJars) {
                // Resolve and extract Etendo modules
                loadDependenciesFiles(true, performResolutionConflicts, true)
                etendoDependenciesFiles.addAll(collectDependenciesFiles(this.dependencyContainer.etendoDependenciesJarFiles, applyDependenciesToMainProject))
            } else {
                // The core does not support Jars, ignore performing resolution conflicts.
                loadDependenciesFiles(false, false, true)
            }
            mavenDependenciesFiles.addAll(collectDependenciesFiles(this.dependencyContainer.mavenDependenciesFiles, applyDependenciesToMainProject))
            mavenDependenciesFiles.addAll(
              project.configurations.runtimeClasspath.collect { File f -> f }
            )
        } else if (coreMetadata.coreType == CoreType.JAR) {
            // When the core is in JAR the Etendo core dependency will be already applied
            loadDependenciesFiles(false, performResolutionConflicts, false)

            // Collect the core dependency
            etendoDependenciesFiles.add(collectCoreJarDependency())

            // Collect module dependency
            etendoDependenciesFiles.addAll(collectDependenciesFiles(this.dependencyContainer.etendoDependenciesJarFiles, applyDependenciesToMainProject))
            mavenDependenciesFiles.addAll(collectDependenciesFiles(this.dependencyContainer.mavenDependenciesFiles, applyDependenciesToMainProject))
        } else {
            // Exclude the core from each dependency to prevent downloading it when the Core is undefined
            rootProjectConfigurations.each {
                ResolverDependencyUtils.excludeCoreDependencies(project, it, false)
            }
        }

        dependencies.addAll(mavenDependenciesFiles)
        dependencies.addAll(etendoDependenciesFiles)
        return dependencies
    }

    Map<String, List<ArtifactDependency>> performResolutionConflict(Configuration container, boolean addCoreToResolution, boolean filterCoreDependency) {
        // Create a temporal configuration container used to perform resolution conflicts
        def resolutionContainer = container.copyRecursive()

        // Add the core dependency when is in Sources and supports Jars
        if (addCoreToResolution) {
            def group = coreMetadata.coreGroup
            def name = coreMetadata.coreName
            def version = coreMetadata.coreVersion
            def core = "${group}:${name}:${version}"
            resolutionContainer.dependencies.add(project.dependencies.create(core))
        }

        // Perform the resolution conflict versions
        return ResolutionUtils.performResolutionConflicts(project, resolutionContainer, filterCoreDependency, true)
    }

    /**
     * Collect all the external dependencies files defined by the user.
     * Filters Maven and Etendo dependencies.
     *
     * @param addCoreToResolution Adds the core dependency when the resolution version conflict is performed
     * @param performResolutionConflicts Perform the resolution version conflict
     * @param filterCoreDependency filter the core dependency to prevent download a new version.(When core in SOURCES)
     */
    void loadDependenciesFiles(boolean addCoreToResolution, boolean performResolutionConflicts , boolean filterCoreDependency) {
        // Load all project and subproject dependencies in a custom configuration container
        Configuration container = ResolverDependencyUtils.loadResolutionDependencies(project)

        // Load the CORE dependencies
        if (coreMetadata.coreType == CoreType.SOURCES && project.extensions.findByType(EtendoPluginExtension).loadCoreDependencies) {
            ResolutionUtils.loadCoreDependencies(project, coreMetadata, container)
        }

        ArtifactDependency coreArtifactDependency = null

        if (performResolutionConflicts) {
            // The resolution conflict returns all the 'selected' dependencies (matched versions)
            def artifactDependencies = performResolutionConflict(container, addCoreToResolution, false)

            // TODO: Improvement - Save the 'artifactDependencies' in the project properties, to use later to verify if a module
            // To be extracted contains conflicts.

            // Obtain the 'selected' Core version
            String currentCoreDependency = "${this.coreMetadata.coreGroup}:${this.coreMetadata.coreName}"
            coreArtifactDependency = ResolverDependencyUtils.getCoreDependency(project, currentCoreDependency ,artifactDependencies)
        }

        if (this.coreMetadata.coreType == CoreType.SOURCES) {
            updateContainerPreFilterCoreSources(container, coreArtifactDependency)
        } else if (this.coreMetadata.coreType == CoreType.JAR) {
            updateContainerPreFilterCoreJar(container, coreArtifactDependency)
        }

        // Contains a map between the subproject name "group:artifact" and the subproject
        Map<String, Project> subprojectNames = ModulesConfigurationUtils.getSubprojectNames(project)

        // Configure the container to substitute all the dependencies already in sources.
        ModulesConfigurationUtils.configureSubstitutions(project, [container], subprojectNames)

        // Filter maven and Etendo dependencies
        this.dependencyContainer.configuration = container
        this.dependencyContainer.filterDependenciesFiles()

        // Configure the subprojects to use the filtered dependencies
        def artifacts = this.dependencyContainer.etendoDependenciesJarFiles.entrySet().collect({
            it.value
        }) + this.dependencyContainer.mavenDependenciesFiles.entrySet().collect({
            it.value
        })

        ModulesConfigurationUtils.configureVersionReplacer(project, DependencyContainer.parseArtifactDependency(project, artifacts))
    }

    void updateContainerPreFilterCoreSources(Configuration container, ArtifactDependency coreArtifactDependency) {
        // Exclude from the configuration the CORE dependency
        ResolverDependencyUtils.excludeCoreDependencies(project, container, true)

        // TODO: Improvement
        //  Add the Core dependencies (defined in the pom.xml of the core),
        //  if the core in Sources does not contain the sources JARs dependencies (user should provide a flag).

    }

    void updateContainerPreFilterCoreJar(Configuration container, ArtifactDependency coreArtifactDependency) {
        // Exclude from each Dependency in the configuration the CORE dependency
        ResolverDependencyUtils.excludeCoreFromDependencies(project, container)

        // If the coreArtifactDependency is not defined (resolution not performed)
        // or the Core dependency has conflicts
        if (!coreArtifactDependency || (coreArtifactDependency && coreArtifactDependency.hasConflicts)) {
            // Clear all the Core dependencies and left the one defined by the user.
            container.dependencies.removeIf({
                def dependencyName = "${it.group}:${it.name}"
                return ResolutionUtils.isCoreDependency(dependencyName)
            })

            Dependency coreDependency = CoreMetadata.getCoreDependency(project)
            if (coreDependency) {
                project.logger.info("***********************************************")
                project.logger.info("* The core dependency to resolve will be the one defined by the user")
                project.logger.info("* Core dependency '${coreDependency.group}:${coreDependency.name}:${coreDependency.version}'")
                project.logger.info("***********************************************")
                container.dependencies.add(coreDependency)
                selectCoreVersion(project, container, coreDependency.group, coreDependency.name, coreDependency.version)
            } else {
                project.logger.info("***********************************************")
                project.logger.info("* The core dependency is not defined.")
                project.logger.info("***********************************************")
            }

        } else {
            // If the core does not have conflicts, update the Core dependency with the resolved 'selected' dependency
            project.logger.info("***********************************************")
            project.logger.info("* Core dependency resolved to use '${coreArtifactDependency.displayName}'")
            project.logger.info("***********************************************")
            selectCoreVersion(project, container, coreArtifactDependency.group, coreArtifactDependency.name, coreArtifactDependency.version)
        }
    }

    static void selectCoreVersion(Project project, Configuration container, String coreGroup, String coreName, String version) {
        container.resolutionStrategy.eachDependency({details ->
            if (details.requested.group == coreGroup && details.requested.name == coreName) {
                details.useVersion(version)
                details.because("CORE dependency resolution.")
            }
        })
    }

    /**
     * Collect the files mapped to a dependency (JARs files).
     * Etendo JARs files are extracted.
     * @param dependenciesFiles
     * @param applyDependencyToMainProject
     * @return
     */
    List<File> collectDependenciesFiles(Map<String, ArtifactDependency> dependenciesFiles, boolean applyDepToMainProject) {
        List<File> collection = []

        for (def entry in dependenciesFiles) {
            ArtifactDependency artifactDependency = entry.value
            def auxApply = applyDepToMainProject

            // Extract Etendo dependency
            if (artifactDependency.type == DependencyType.ETENDOJARMODULE && artifactDependency instanceof EtendoJarModuleArtifact) {
                artifactDependency.extract()

                // Prevent adding the JAR file to the WebContent
                if (!artifactDependency.extracted) {
                    continue
                }
            }

            // The 'dependenciesFiles' should contain all the declared artifacts (transitive ones included)
            if (auxApply) {
                this.applyDependencyToMainProject(artifactDependency, false)
            }

            collection.add(artifactDependency.locationFile)
        }

        return collection
    }

    /**
     * Collects and extracts the Etendo core JAR dependency
     * @return
     */
    File collectCoreJarDependency() {

        ArtifactDependency coreArtifact = this.dependencyContainer.etendoCoreDependencyFile

        if (!coreArtifact) {
            throw new IllegalArgumentException("Error collecting the Etendo core JAR file")
        }

        // a verification should be done to see if the version is MINOR or MAJOR that the current installed,
        // If the version is MAJOR, a WARNING should be showed to the user to UPDATE the core.
        // If the version is MINOR, the core should not be extracted (the user can use a force flag)
        // If the core is not extracted, a Exception should be thrown with the problem and how to fix it
        // (because the user can run a clean and the 'build.xml' will never be loaded)

        coreArtifact.extract()

        if (coreArtifact.extracted) {
            applyDependencyToMainProject(coreArtifact, true)
        }

        return coreArtifact.locationFile
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

    void applyDependencyToMainProject(ArtifactDependency artifactDependency, boolean transitivity) {
        project.dependencies {
            implementation("${artifactDependency.displayName}") {
                transitive = transitivity
            }
        }
    }

}