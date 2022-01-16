package com.etendoerp.legacy.dependencies.container

import com.etendoerp.core.CoreMetadata
import com.etendoerp.jars.JarCoreGenerator
import com.etendoerp.legacy.dependencies.ResolverDependencyUtils
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.file.FileTree

class DependencyContainer {

    Project project
    CoreMetadata coreMetadata
    Configuration configuration

    // Map containing has key the module name
    Map<String, ArtifactDependency> mavenDependenciesFiles
    Map<String, ArtifactDependency> etendoDependenciesJarFiles
    Map<String, ArtifactDependency> etendoDependenciesZipFiles
    Map<String, Dependency> dependenciesMap

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

    /**
     * Filters the external dependencies by type:
     *  - MAVEN
     *  - ETENDOJARMODULE
     *  - ETENDOZIPMODULE
     *  - ETENDOCORE
     */
    void filterDependenciesFiles() {
        this.dependenciesMap = ResolverDependencyUtils.loadDependenciesMap(this.project, this.configuration)
        def resolvedArtifacts = this.configuration.resolvedConfiguration.resolvedArtifacts

        for (ResolvedArtifact resolvedArtifact : resolvedArtifacts) {
            ArtifactDependency artifactDependency = getArtifactDependency(project, resolvedArtifact)
            // Get the 'Dependency' object
            artifactDependency.dependency = this.dependenciesMap.get(artifactDependency.moduleName)
            switch (artifactDependency.type) {
                case DependencyType.ETENDOCORE:
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

    static ArtifactDependency getArtifactDependency(Project project, ResolvedArtifact resolvedArtifact) {
        // If the extension is 'zip' then could be a Etendo module in zip format
        if (resolvedArtifact.extension == "zip") {
            // TODO: Verify that is a Etendo artifact
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
                return new EtendoCoreArtifact(project, resolvedArtifact)
            } else {
                return new EtendoJarModuleArtifact(project, resolvedArtifact)
            }
        }

        // The default Artifact is a Maven dependency
        return new MavenArtifact(project, resolvedArtifact)
    }

}
