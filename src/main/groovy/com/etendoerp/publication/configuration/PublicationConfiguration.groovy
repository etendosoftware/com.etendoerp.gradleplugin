package com.etendoerp.publication.configuration

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

        // Load local publication tasks
        loadPublicationTasksToMainProject(PublishTaskGenerator.PUBLISH_LOCAL_TASK, PublicationLoader.PUBLISH_LOCAL_DUMMY_TASK)

        // Load maven publication tasks
        loadPublicationTasksToMainProject(PublishTaskGenerator.PUBLISH_MAVEN_TASK, PublicationLoader.PUBLISH_MAVEN_DUMMY_TASK)
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

    void loadPublicationTasksToMainProject(String subprojectPublicationTaskName, String mainProjectPublicationTaskName) {
      //
      String bundle = project.findProperty("pkg") as String
      if (!bundle) {
          throw new IllegalArgumentException("* The 'pkg' property is not set. Please provide a valid bundle name to publish.")
      } else {
          def buildGradleFile = project.file("modules/${bundle}/extension-modules.gradle")
          List<String> deps = []
          if (buildGradleFile.exists()) {
              def buildGradleContent = buildGradleFile.text
              def pattern = /git@[\w.-]+:([\w.-]+\/)?([\w.-]+)\.git/
              def matcher = buildGradleContent =~ pattern
              matcher.each { List<String> match -> // Each match is a list of strings: [fullMatch, optionalGroup/, projectName]
                  String depIdentifier = null
                  if (match.size() == 3) { // [fullMatch, groupPath, projectName]
                      depIdentifier = match[2] // The project name part
                  } else if (match.size() == 2) { // [fullMatch, projectName] (if groupPath was empty)
                       depIdentifier = match[1] // This case might need refinement based on actual git URLs
                  }
                  if (depIdentifier) {
                      deps.add(depIdentifier.toString()) // Ensure it's a String
                      project.logger.lifecycle("[PublicationConfiguration] Found dependency: '${depIdentifier}' in bundle '${bundle}'")
                  } else {
                      project.logger.warn("[PublicationConfiguration] Skipping invalid dependency format in bundle '${bundle}': ${match[0]}")
                  }
              }
              deps.add(bundle) // Also add the main bundle itself to the list
          } else {
              throw new IllegalArgumentException("* The bundle '${bundle}' file 'modules/${bundle}/extension-modules.gradle' does not exist.")
          }
          List<Task> subprojectPublicationTasks = []
          List<String> foundSubprojects = []
          deps.each { String subprojectName ->
              def subproject = project.findProject(":modules:${subprojectName}")
              if (!subproject) {
                  project.logger.warn("* The subproject '${subprojectName}' does not exist in the project. Skipping it.")
              } else {
                  if (!subproject.tasks.findByName(subprojectPublicationTaskName)) {
                      throw new IllegalArgumentException("* The subproject '${subprojectName}' is missing the publication task '${subprojectPublicationTaskName}'.")
                  }
                  project.logger.info("* Adding the task '${subprojectPublicationTaskName}' from the '${subprojectName}' to be runned.")
                  subprojectPublicationTasks.add(subproject.tasks.getByName(subprojectPublicationTaskName))
                  foundSubprojects.add(subprojectName)
              }
          }
          for (int i = 0; i < subprojectPublicationTasks.size() - 1; i++) {
              def currentTask = subprojectPublicationTasks[i]
              def nextTask = subprojectPublicationTasks[i + 1]
              nextTask.mustRunAfter(currentTask)
          }
          for (int i = 0; i < deps.size() - 1; i++) {
              String subprojectName = deps.get(i)
              def subproject = project.findProject(":modules:${subprojectName}")
              def jarTask = subproject.tasks.findByName("jar")
              def sourcesJarTask = subproject.tasks.findByName("sourcesJar")
              if(!jarTask || !sourcesJarTask) {
                  throw new IllegalArgumentException("* The subproject '${subprojectName}' does not contain the 'jar' or 'sourcesJar' task. Please ensure it is a valid Java project.")
              }
              sourcesJarTask?.mustRunAfter(jarTask)
              for (int j = i + 1; j < deps.size() - 1; j++) {
                  def dependentSubprojectName = deps.get(j)
                  def dependentSubproject = project.findProject(":modules:${dependentSubprojectName}")
                  def compileJavaTask = dependentSubproject.tasks.findByName("compileJava")
                  if (jarTask && sourcesJarTask && compileJavaTask) {
                      compileJavaTask.mustRunAfter([jarTask, sourcesJarTask])
                  }
              }
          }

          // Add each task to the main project local publication
          def mainPublicationTask = project.tasks.findByName(mainProjectPublicationTaskName)
          for (def subprojectTask : subprojectPublicationTasks) {
              mainPublicationTask.dependsOn(subprojectTask)
          }
      }
    }
}
