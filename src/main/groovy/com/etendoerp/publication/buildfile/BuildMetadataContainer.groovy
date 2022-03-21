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
    Map<String, BuildMetadata> moduleSubprojectsMetadataById

    Map<String, BuildMetadata> moduleSubprojectsMetadataByName

    BuildMetadataContainer(Project project, String repositoryName, File buildGradleTemplateFile) {
        this.project = project
        this.repositoryName = repositoryName
        this.buildGradleTemplateFile = buildGradleTemplateFile
        this.moduleSubprojectsMetadataById = new TreeMap<>(String.CASE_INSENSITIVE_ORDER)
        this.moduleSubprojectsMetadataByName = new TreeMap<>(String.CASE_INSENSITIVE_ORDER)
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
            BuildMetadata metadata = new BuildMetadata(project, moduleName, repositoryName, buildGradleTemplateFile)
            metadata.buildMetadataContainer = this
            this.moduleSubprojectsMetadataByName.put(metadata.javaPackage, metadata)
            this.moduleSubprojectsMetadataById.put(metadata.adModuleId, metadata)
            metadata.addCoreDependency = true
        }
    }

    void createSubprojectsBuildFile() {
        createSubprojectsBuildFileFromMap(this.moduleSubprojectsMetadataByName, true)
    }

    static void createSubprojectsBuildFileFromMap(Map<String, BuildMetadata> map, boolean addSubprojectDependencies) {
        map.each {
            BuildMetadata metadata = it.value
            metadata.processSubprojectDependencies = addSubprojectDependencies
            metadata.createBuildFile()
        }
    }

    void createCustomSubprojectBuildFile(String name) {
        BuildMetadata metadata = verifyBundle(this.project)

        // If the metadata is not defined, then is not a bundle
        if (!metadata) {
            BuildFileUtils.verifyModuleLocation(this.project, name)
            metadata = this.moduleSubprojectsMetadataByName.get(name)
        }

        if (!metadata) {
            throw new IllegalArgumentException("* The metadata for the module '${name}' does not exists.\n" +
                    "Make sure that the module with name '${name}' exists in the SOURCE modules directory.")
        }

        metadata.createBuildFile()
    }

    BuildMetadata verifyBundle(Project project) {
        Project bundle = BuildFileUtils.getBundleSubproject(project)
        BuildMetadata metadata = null
        if (bundle) {
            String name = PublicationUtils.loadModuleName(project, bundle).orElse(project.findProperty(BuildFileUtils.BUNDLE_PROPERTY) as String)
            metadata = this.moduleSubprojectsMetadataByName.get(name)
            if (!metadata) {
                throw new IllegalArgumentException("* The bundle '${bundle}' is not loaded in the subproject list.")
            }
            metadata.loadBundleProject(bundle)
            metadata.processSubprojectDependencies = true
        }
        return metadata
    }

}
