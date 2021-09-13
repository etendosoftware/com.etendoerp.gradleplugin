package com.etendoerp.jars.modules.metadata

import com.etendoerp.jars.FileExtensions
import com.etendoerp.jars.modules.ModuleJarGenerator
import org.gradle.api.Project
import org.gradle.api.artifacts.DependencySet

/**
 * This class is used to obtain all the necessary information to publish.
 * The information will be took from a 'build.gradle' file (a Gradle Project object).
 */
class ModuleBuildMetadata extends ModuleMetadata {

    DependencySet dependencies

    ModuleBuildMetadata(Project project, String moduleName) {
        super(project, moduleName)
    }

    void loadMetadata() {

        def moduleProject = project.findProject(":${ModuleJarGenerator.BASE_MODULE_DIR}:$moduleName")

        if (moduleProject == null) {
            throw new IllegalArgumentException("The gradle project :$moduleName does not exists.")
        }

        this.group = moduleProject.group
        this.version = moduleProject.version
        this.repository = moduleProject.repository
        project.logger.info("repo::: ${repository}")
        def configuration = moduleProject.configurations.getByName(CONFIGURATION_NAME)

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
}
