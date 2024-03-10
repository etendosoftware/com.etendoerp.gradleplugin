package com.etendoerp.legacy.dependencies.container

import com.etendoerp.EtendoPluginExtension
import com.etendoerp.core.CoreMetadata
import com.etendoerp.jars.JarCoreGenerator
import com.etendoerp.legacy.dependencies.ResolverDependencyUtils
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ResolveException
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.file.FileTree
import org.gradle.api.specs.Specs
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier

class DependencyContainer {

    Project project
    CoreMetadata coreMetadata
    Configuration configuration

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
        this.dependenciesMap            = new HashMap<>()
    }

    DependencyContainer(Project project, CoreMetadata coreMetadata, Configuration configuration) {
        this(project, coreMetadata)
        this.configuration = configuration
    }

    static void showUnresolvedArtifactsException(Project project, ResolveException resolveException) {
        project.logger.error('* Error resolving the dependencies')
        project.logger.error("* ERROR: ${resolveException.getMessage()}")

        resolveException.causes.each {
            Throwable t = it
            project.logger.error("* -> ${t.getMessage()}")
        }

        project.logger.error("${EtendoPluginExtension.ignoreUnresolvedArtifactsMessage()}")
    }

    /**
     * Filters the external dependencies by type:
     *  - MAVEN
     *  - ETENDOJARMODULE
     *  - ETENDOZIPMODULE
     *  - ETENDOCORE
     */
    void filterDependenciesFiles() {
        this.dependenciesMap = ResolverDependencyUtils.loadDependenciesMap(this.project, this.configuration)
        Set<ResolvedArtifact> resolvedArtifacts = [].toSet() as Set<ResolvedArtifact>
        try {
            resolvedArtifacts = this.configuration.resolvedConfiguration.resolvedArtifacts
        } catch (ResolveException re) {
            def extension = project.extensions.findByType(EtendoPluginExtension)
            if (!extension.ignoreUnresolvedArtifacts) {
                // Show the warning
                showUnresolvedArtifactsException(this.project, re)
                throw re
            }
            resolvedArtifacts = this.configuration.resolvedConfiguration.lenientConfiguration.getArtifacts(Specs.satisfyAll())
        }

        // TODO: Improvement, detect the unresolved dependencies if the flag 'ignoreUnresolvedArtifacts'
        // Use the resolved dependencies  'this.configuration.resolvedConfiguration.lenientConfiguration.getFiles(Specs.satisfyAll())'
        // Remove from the ':compileClasspath' the unresolved dependencies to prevent failing
        for (ResolvedArtifact resolvedArtifact : resolvedArtifacts) {
            if (resolvedArtifact.id.componentIdentifier instanceof DefaultModuleComponentIdentifier) {
                ArtifactDependency artifactDependency = getArtifactDependency(project, resolvedArtifact)

                // Get the 'Dependency' object
                artifactDependency.dependency = this.dependenciesMap.get(artifactDependency.moduleName)
                switch (artifactDependency.type) {
                    case DependencyType.ETENDOCOREJAR:
                        this.etendoCoreDependencyFile = artifactDependency
                        break
                    case DependencyType.ETENDOJARMODULE:
                        this.etendoDependenciesJarFiles.put(artifactDependency.moduleName, artifactDependency)
                        break
                    case DependencyType.ETENDOZIPMODULE:
                        this.etendoDependenciesZipFiles.put(artifactDependency.moduleName, artifactDependency)
                        break
                    default:
                        this.mavenDependenciesFiles.put(artifactDependency.moduleName, artifactDependency)
                        break
                }
            }
        }
    }

    static ArtifactDependency getArtifactDependency(Project project, ResolvedArtifact resolvedArtifact) {
        // If the extension is 'zip' then could be a Etendo module or Core in zip format
        if (resolvedArtifact.extension == 'zip') {
            // TODO - Improvement: Verify that is a Etendo artifact
            String fileName = resolvedArtifact.file.absolutePath

            if (fileName.contains(CoreMetadata.CLASSIC_ETENDO_CORE_GROUP) || fileName.contains(CoreMetadata.DEFAULT_ETENDO_CORE_NAME)) {
                return new EtendoCoreZipArtifact(project, resolvedArtifact)
            }

            return new EtendoZipModuleArtifact(project, resolvedArtifact)
        }

        // Check if the file is a Etendo module
        FileTree unzipDependency = project.zipTree(resolvedArtifact.file)
        def etendoModulesTree = unzipDependency.matching {
            include "${ArtifactDependency.JAR_ETENDO_MODULE_LOCATION}"
        }

        // The dependency file is a Etendo module
        if (etendoModulesTree && etendoModulesTree.size() >= 1) {
            // The jar is the Etendo core
            if (resolvedArtifact.file.name.contains(JarCoreGenerator.ETENDO_CORE)) {
                return new EtendoCoreJarArtifact(project, resolvedArtifact)
            } else {
                return new EtendoJarModuleArtifact(project, resolvedArtifact)
            }
        }

        // The default Artifact is a Maven dependency
        return new MavenArtifact(project, resolvedArtifact)
    }

    /**
     * Returns a Map containing the names (group:name) of an Artifact
     * @param project
     * @param artifacts
     * @return
     */
    static Map<String, ArtifactDependency> parseArtifactDependency(Project project, Collection<ArtifactDependency> artifacts) {
        Map<String, ArtifactDependency> map = new TreeMap<>(String.CASE_INSENSITIVE_ORDER)

        artifacts.stream().filter {
            it.group != null && !it.group.isBlank() && it.name != null && !it.name.isBlank()
        }.each {
            String name = "${it.group}:${it.name}"
            map.put(name.toString(), it)
        }
        return map
    }
}
