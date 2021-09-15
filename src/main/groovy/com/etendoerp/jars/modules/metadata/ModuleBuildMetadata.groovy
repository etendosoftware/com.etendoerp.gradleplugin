package com.etendoerp.jars.modules.metadata

import com.etendoerp.jars.FileExtensions
import com.etendoerp.jars.modules.ModuleJarGenerator
import com.etendoerp.publication.PublicationUtils
import org.gradle.api.Project
import org.gradle.api.artifacts.DependencySet
import org.gradle.api.artifacts.result.UnresolvedDependencyResult
import org.gradle.api.artifacts.Configuration

/**
 * This class is used to obtain all the necessary information to publish.
 * The information will be took from a 'build.gradle' file (a Gradle Project object).
 */
class ModuleBuildMetadata extends ModuleMetadata {

    DependencySet dependencies
    Configuration configuration
    Project moduleProject

    ModuleBuildMetadata(Project project, String moduleName) {
        super(project, moduleName)
    }

    void loadMetadata() {

        moduleProject = project.findProject(":${PublicationUtils.BASE_MODULE_DIR}:$moduleName")

        if (moduleProject == null) {
            throw new IllegalArgumentException("The gradle project :$moduleName does not exists.")
        }

        this.group = moduleProject.group
        this.version = moduleProject.version
        this.repository = moduleProject.repository

        this.configuration = moduleProject.configurations.getByName(CONFIGURATION_NAME)

        if (configuration != null) {
            this.dependencies = configuration.dependencies
        }

        artifactId = moduleName.toString().replace(group + ".", "")
    }

    @Override
    Node createDependenciesNode() {
        def dependenciesNode = new Node(null, "dependencies")
        this.dependencies.each {
            def dependencyNode = dependenciesNode.appendNode("dependency")

            dependencyNode.appendNode("groupId", it.group)
            dependencyNode.appendNode("artifactId", it.name)
            dependencyNode.appendNode("version", it.version)

            // Check if the extension is a zip type
            it.artifacts.each { art ->
                def ext = art.extension
                if (ext && ext == FileExtensions.ZIP) {
                    dependencyNode.appendNode("type", ext as String)
                    return
                }
            }
        }
        return dependenciesNode
    }

    @Override
    String getDependenciesValues() {
        // TODO: format
        return this.dependencies.toString()
    }

    /**
     * Verifies that all the dependencies set by a user exists and are resolvable.
     */
    @Override
    void validateDependencies() {

        PublicationUtils.configureProjectRepositories(project, moduleProject)

        if (project.hasProperty(PublicationUtils.OMIT_DEPENDENCY_VERIFICATION)) {
            project.logger.info("WARNING: Omitting module dependencies verification.")
            project.logger.info("The generated 'pom.xml' file could have incorrect dependencies.")
            return
        }

        if (configuration != null) {
            def dependencies = configuration.getIncoming().getResolutionResult().getAllDependencies()
            dependencies.each {
                if (it instanceof UnresolvedDependencyResult) {
                    def unresolved = it as UnresolvedDependencyResult
                    throw unresolved.getFailure()
                }
            }
        }
    }
}
