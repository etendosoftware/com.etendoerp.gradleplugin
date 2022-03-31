package com.etendoerp.jars.modules.metadata


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

    // Set of dependencies used to fill the pom.xml
    DependencySet dependencies

    // Contains all the configurations of the project
    // All the dependencies are obtained from the configurations to create te pom.xml
    List<Configuration> configurations

    Project moduleProject

    ModuleBuildMetadata(Project project, String moduleName) {
        super(project, moduleName)
    }

    void loadMetadata() {
        this.configurations = new ArrayList<>()

        moduleProject = project.findProject(":${PublicationUtils.BASE_MODULE_DIR}:$moduleName")

        if (moduleProject == null) {
            throw new IllegalArgumentException("The gradle project :$moduleName does not exists.")
        }

        this.group = moduleProject.group
        this.version = moduleProject.version
        this.repository = moduleProject.repository

        // Get all the configurations defined in the subproject
        this.configurations = DependencyUtils.loadListOfConfigurations(moduleProject)

        // This configuration is used to store all the subproject dependencies
        def configContainer = moduleProject.configurations.findByName(PublicationUtils.MODULE_DEPENDENCY_CONTAINER)
        this.dependencies = configContainer.dependencies

        // Load all the dependencies defined in the build.gradle of a subproject
        DependencyUtils.loadDependenciesFromConfigurations(this.configurations, this.dependencies)

        artifactId = moduleName.toString().replace(group + ".", "")
    }

    @Override
    Node createDependenciesNode() {
        def dependenciesNode = new Node(null, "dependencies")
        if (this.dependencies != null) {
            this.dependencies.each {
                def dependencyNode = dependenciesNode.appendNode("dependency")

                dependencyNode.appendNode("groupId", it.group)
                dependencyNode.appendNode("artifactId", it.name)
                dependencyNode.appendNode("version", it.version)

                // Check if the extension is a zip type
                it.artifacts.each { art ->
                    def ext = art.extension
                    if (ext && ext == ZIP_TYPE) {
                        dependencyNode.appendNode("type", ext as String)
                        return
                    }
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

        validateConfigurations(this.configurations)

    }

    /**
     * Validate the dependencies of a resolvable configuration.
     * @param configurations
     */
    void validateConfigurations(List<Configuration> configurations) {
        configurations.each {
            if (it != null && it.canBeResolved) {
                def dependencies = it.getIncoming().getResolutionResult().getAllDependencies()
                dependencies.each {
                    if (it instanceof UnresolvedDependencyResult) {
                        def unresolved = it as UnresolvedDependencyResult
                        throw unresolved.getFailure()
                    }
                }
            }
        }
    }


}
