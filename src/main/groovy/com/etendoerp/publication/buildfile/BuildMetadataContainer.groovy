package com.etendoerp.publication.buildfile

import com.etendoerp.jars.PathUtils
import com.etendoerp.publication.PublicationUtils
import groovy.io.FileType
import org.gradle.api.Project

class BuildMetadataContainer {

    Project project
    String repositoryName
    File buildGradleTemplateFile

    /**
     * Map used to store the AD_MODULE_ID of a module with the 'BuildMetadata' information.
     * This is used to obtain the dependencies between subprojects from the 'AD_MODULE_DEPENDENCY.xml' file
     */
    Map<String, BuildMetadata> moduleSubprojectsMetadata

    BuildMetadataContainer(Project project, String repositoryName, File buildGradleTemplateFile) {
        this.project = project
        this.repositoryName = repositoryName
        this.buildGradleTemplateFile = buildGradleTemplateFile
        this.moduleSubprojectsMetadata = new TreeMap<>(String.CASE_INSENSITIVE_ORDER)
    }

    void loadSubprojectMetadata() {
        String modulesLocation = PathUtils.createPath(
                project.rootDir.absolutePath,
                PublicationUtils.BASE_MODULE_DIR
        )

        File modulesLocationFile = new File(modulesLocation)

        if (!modulesLocationFile || !modulesLocationFile.exists()) {
            project.logger.info("* The location '${modulesLocation}' to load the source modules does not exists.")
            return
        }

        List<File> sourceModules = new ArrayList<>()
        // Add the source modules
        modulesLocationFile.traverse(type: FileType.DIRECTORIES, maxDepth: 0) {
            sourceModules.add(it)
        }

        for (File sourceModule : sourceModules) {
            String moduleName = sourceModule.name
            BuildMetadata metadata = new BuildMetadata(project, moduleName, repositoryName, buildGradleTemplateFile, true, this)
            metadata.addCoreDependency = true
            this.moduleSubprojectsMetadata.put(metadata.adModuleId, metadata)
        }
    }

    void createSubprojectsBuildFile() {
        this.moduleSubprojectsMetadata.each {
            BuildMetadata metadata = it.value
            metadata.createBuildFile()
        }
    }

}
