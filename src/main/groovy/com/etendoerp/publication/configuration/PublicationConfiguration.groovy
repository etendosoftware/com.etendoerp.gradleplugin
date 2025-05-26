package com.etendoerp.publication.configuration

import com.etendoerp.gradleutils.GradleUtils
import com.etendoerp.publication.PublicationLoader
import com.etendoerp.publication.PublicationUtils
import com.etendoerp.publication.configuration.pom.PomConfigurationContainer
import com.etendoerp.publication.configuration.pom.PomConfigurationType
import com.etendoerp.publication.taskloaders.PublicationTaskLoader
import com.etendoerp.publication.taskloaders.PublishTaskGenerator
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.ProjectPublicationRegistry
import org.gradle.api.internal.project.DefaultProject
import org.gradle.internal.os.OperatingSystem
import org.gradle.process.ExecSpec

class PublicationConfiguration {

    /**
     * Property used to store the default dependencies obtained from the 'java' plugin
     */
    static final String DEFAULT_DEPENDENCIES_CONTAINER = "defaultDependenciesContainer"

    /**
     * Property used to verify if is used by the user as a command line parameter to run the recursive publication
     */
    static final String RECURSIVE_PUBLICATION_PROPERTY = "recursive"

    /**
     * Property used to update automatically the passed project to publish, or the detected leafs.
     */
    static final String RECURSIVE_UPDATE_LEAF = "updateLeaf"

    static final String PUBLISHED_FLAG = "publishedFlag"

    /**
     * Property used to ignore the publication of projects not containing the necessary information
     */
    static final String IGNORE_INVALID_PROJECTS_PROPERTY = "ignoreInvalidProjects"


    Project project
    ProjectPublicationRegistry projectPublicationRegistry

    PublicationConfiguration(Project project){
        this.project = project
        this.projectPublicationRegistry = ((DefaultProject) project).services.get(ProjectPublicationRegistry.class)
    }

    void configurePublication() {
        // Load publication tasks
        List<String> publicationTasks = [
                PublicationLoader.PUBLISH_VERSION_TASK
        ]

        // Identify the tasks being ran
        def taskNames = project.gradle.startParameter.taskNames

        taskNames.each {
            if (it == PublicationLoader.PUBLISH_VERSION_TASK || it == ":${PublicationLoader.PUBLISH_VERSION_TASK}") {
                configurePublishVersion()
            } else if (it == PublicationLoader.PUBLISH_ALL_MODULES_TASK || it == ":${PublicationLoader.PUBLISH_ALL_MODULES_TASK}") {
                configurePublishAll()
            }
        }
    }

    void configurePublishAll() {
        // Get all the submodules to publish
        def moduleProject = project.findProject(":${PublicationUtils.BASE_MODULE_DIR}")
        List<Project> moduleSubprojects = moduleProject.subprojects.toList()
        def updateLeaf = project.findProperty(RECURSIVE_UPDATE_LEAF) ? true : false

        loadSubprojectPublicationTasks(moduleSubprojects, true, updateLeaf)
    }

    void configurePublishVersion() {
        def submodule = PublicationConfigurationUtils.loadSubproject(project)
        if (!PublicationTaskLoader.validateSubmodulePublicationTasks(this.project, submodule)) {
            throw new IllegalArgumentException("* The subproject '${submodule}' does not contains the necessary information to publish.")
        }

        def recursivePublication = project.findProperty(RECURSIVE_PUBLICATION_PROPERTY) ? true : false
        def updateLeaf = project.findProperty(RECURSIVE_UPDATE_LEAF) ? true : false

        loadSubprojectPublicationTasks([submodule], recursivePublication, updateLeaf)
    }

    static def filterValidAndInvalidProjectsToPublish(Project project, List<Project> subProjects) {
        List<Project> validProjectsToPublish = []
        List<Project> invalidProjectsToPublish = []

        subProjects.each {
            (PublicationTaskLoader.validateSubmodulePublicationTasks(project, it)) ? validProjectsToPublish.add(it) : invalidProjectsToPublish.add(it)
        }

        return [validProjectsToPublish, invalidProjectsToPublish]
    }

    static def filterAndValidateProjectsToPublish(Project project, List<Project> subProjects) {
        def (List<Project> validProjectsToPublish, List<Project> invalidProjectsToPublish) = filterValidAndInvalidProjectsToPublish(project, subProjects)
        boolean ignoreInvalidProjects = project.findProperty(IGNORE_INVALID_PROJECTS_PROPERTY) ? true : false

        if (invalidProjectsToPublish && !invalidProjectsToPublish.isEmpty() && !ignoreInvalidProjects) {
            throw new IllegalArgumentException("* The publication can not be ran because there is some module projects with missing information to publish. Modules: ${invalidProjectsToPublish.toList()}.\n" +
                    "* You can force the publication with the flag '-P${IGNORE_INVALID_PROJECTS_PROPERTY}=true'. WARNING: This can lead to inconsistent dependencies.")
        }

        return [validProjectsToPublish, invalidProjectsToPublish]
    }

    void loadSubprojectPublicationTasks(List<Project> subprojects, boolean recursivePublication, boolean updateSubprojectsLeafVersion) {
        def (List<Project> validProjectsToPublish, List<Project> invalidProjectsToPublish) = filterAndValidateProjectsToPublish(project, subprojects)

        def subprojectsToPublish = validProjectsToPublish

        if (recursivePublication) {
            Map<String, Project> subprojectsMap = PublicationConfigurationUtils.generateProjectMap(validProjectsToPublish)
            subprojectsToPublish = processLeafProjects(subprojectsMap, updateSubprojectsLeafVersion)

            // Check if the 'subprojectToPublish' contains new projects that can be invalid to publish
            (validProjectsToPublish, invalidProjectsToPublish) = filterAndValidateProjectsToPublish(project, subprojectsToPublish)
            subprojectsToPublish = validProjectsToPublish
        }

        // Mark the subprojects which will be published
        subprojectsToPublish.each {
            it.ext.set(PUBLISHED_FLAG, true)
        }

        // Load magen publication tasks
        loadPublicationTasksToMainProject(subprojectsToPublish)

    }

    List<Project> processLeafProjects(Map<String, Project> subProjectsLeafs, boolean updateSubprojectsLeafVersion, List<String> extraConfigurations=[]) {
        Queue<Map.Entry<String, Project>> processedProjects = new LinkedList<>()
        Queue<Map.Entry<String, Project>> unprocessedProjects = new LinkedList<>()
        PomConfigurationType pomType = PomConfigurationType.MULTIPLE_PUBLISH

        // Load the leaf subprojects to the unprocessed list
        for (def subproject : subProjectsLeafs) {
            unprocessedProjects.add(new EntryProjects<String, Project>(subproject.key, subproject.value))
        }

        // Load module subprojects
        def moduleProject = project.findProject(":${PublicationUtils.BASE_MODULE_DIR}")
        def moduleSubprojects = moduleProject.subprojects.toList()

        // Load subproject dependencies
        PublicationConfigurationUtils.loadSubprojectDependencies(this.project, moduleSubprojects, pomType, extraConfigurations)

        while (!unprocessedProjects.isEmpty()) {
            def subprojectEntry = unprocessedProjects.poll()
            String subProjectName = subprojectEntry.key
            Project subprojectToProcess = subprojectEntry.value

            def pomContainer = PomConfigurationContainer.getPomContainer(this.project, subprojectToProcess, pomType)
            pomContainer.recursivePublication = true

            /**
             * Checks if the version should be updated if:
             * The updateSubprojectsLeafVersion flag is set to true or
             * The subproject not belong to the 'leafs' passed as parameter.
             */
            boolean updateSubproject = updateSubprojectsLeafVersion || !subProjectsLeafs.containsKey(subProjectName)
            if (updateSubproject) {
                pomContainer.versionContainer.upgradeVersion()
            }
            // Search if is dependency of other subproject
            List<Project> projectDependencies = PublicationConfigurationUtils.verifyProjectDependency(this.project, subprojectToProcess, moduleSubprojects, pomType)

            for (def projectDependency : projectDependencies) {
                // Verify that the 'projectDependency' contains the necessary information to publish
                if (PublicationTaskLoader.validateSubmodulePublicationTasks(this.project, projectDependency)) {
                    def name = "${projectDependency.group}.${projectDependency.artifact}"
                    def entryProject = new EntryProjects(name, projectDependency)

                    // Adds the project to the unprocessed queue only if was not already processed
                    if (!processedProjects.contains(entryProject) && !unprocessedProjects.contains(entryProject)) {
                        unprocessedProjects.add(entryProject)
                    }
                } else {
                    throw new IllegalArgumentException("* The subproject '${projectDependency}' does not contains the necessary information to publish.\n" +
                            "* The subproject is a parent dependency of the '${subprojectToProcess}'.")
                }
            }
            processedProjects.add(subprojectEntry)
        }

        return PublicationConfigurationUtils.queueToList(processedProjects)
    }

    void loadPublicationTasksToMainProject(List<Project> subprojects) {
      String bundle = project.findProperty("pkg") as String
      if (!bundle) {
        throw new IllegalArgumentException("* The 'pkg' property is not set. Please provide a valid bundle name to publish.")
      } else {
        def buildGradleFile = project.file("modules/${bundle}/extension-modules.gradle")
        def deps = []
        if (buildGradleFile.exists()) {
          def buildGradleContent = buildGradleFile.text
          def pattern = /git@[\w.-]+:([\w.-]+)\/([\w.-]+)\.git/
          def matcher = buildGradleContent =~ pattern
          matcher.each { match ->
            if (match.size() == 3) {
              def depIdentifier = "${match[2]}".toString()
              deps.add(depIdentifier)
              project.logger.lifecycle("[PublicationConfiguration] Found dependency: '${depIdentifier}' in bundle '${bundle}'")
            } else {
              project.logger.warn("[PublicationConfiguration] Skipping invalid dependency format in bundle '${bundle}': ${match}")
            }
          }
          deps.add(bundle)
        } else {
          throw new IllegalArgumentException("* The bundle '${bundle}' does not contain a valid 'extension-modules.gradle' file.")
        }
        deps.each { depIdentifier ->
          project.logger.lifecycle("[PublicationConfiguration] Preparing to fork Gradle task for dependency: '${depIdentifier}'")
          if(!new File(project.rootDir, "modules/${depIdentifier}").exists()) {
            project.logger.warn("[PublicationConfiguration] Skipping non-existing module: '${depIdentifier}'")
            return
          }
          String taskPath = ":modules:${depIdentifier}:publish"
          try {
            project.exec { ExecSpec execSpec ->
              execSpec.workingDir = project.rootDir
              project.logger.info("[PublicationConfiguration] Executing forked command for ${taskPath}")
              if (OperatingSystem.current().isWindows()) {
                execSpec.commandLine 'cmd', '/c', "gradlew.bat", taskPath, "--console=plain", "--info"
              } else {
                execSpec.commandLine './gradlew', taskPath, "--console=plain", "--info"
              }
              execSpec.standardOutput = System.out
              execSpec.errorOutput = System.err
            }
            project.logger.lifecycle("[PublicationConfiguration] Forked task for ${taskPath} completed.")
          } catch (Exception e) {
            project.logger.error("[PublicationConfiguration] Failed to execute forked task for ${taskPath}. Error: ${e.message}", e)
          }
        }
      }
    }
}
