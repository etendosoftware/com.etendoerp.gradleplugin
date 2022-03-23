package com.etendoerp.publication.configuration

import com.etendoerp.gradleutils.GradleUtils
import com.etendoerp.publication.PublicationLoader
import com.etendoerp.publication.PublicationUtils
import com.etendoerp.publication.configuration.pom.PomConfigurationContainer
import com.etendoerp.publication.configuration.pom.PomConfigurationType
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
     * Name of the configuration used to add dependencies of another subprojects.
     * Can be used in the generated build.gradle by the users to specify dependencies between subprojects (modules).
     */
    static final String SUBPROJECT_DEPENDENCIES_CONFIGURATION_CONTAINER = "subprojectDependenciesContainer"

    /**
     * Property used to verify if is used by the user as a command line parameter to run the recursive publication
     */
    static final String RECURSIVE_PUBLICATION_PROPERTY = "recursive"

    /**
     * Property used to update automatically the passed project to publish, or the detected leafs.
     */
    static final String RECURSIVE_UPDATE_LEAF = "updateLeaf"

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
            }
        }
    }

    void configurePublishVersion() {
        def submodule = PublicationConfigurationUtils.loadSubproject(project)
        def recursivePublication = project.findProperty(RECURSIVE_PUBLICATION_PROPERTY) ? true : false
        def updateLeaf = project.findProperty(RECURSIVE_UPDATE_LEAF) ? true : false

        loadSubprojectPublicationTasks([submodule], recursivePublication, updateLeaf)
    }

    void loadSubprojectPublicationTasks(List<Project> subprojects, boolean recursivePublication, boolean updateSubprojectsLeafVersion) {
        def subprojectsToPublish = subprojects

        if (recursivePublication) {
            Map<String, Project> subprojectsMap = PublicationConfigurationUtils.generateProjectMap(subprojects)
            subprojectsToPublish = processLeafProjects(subprojectsMap, updateSubprojectsLeafVersion)
        }

        // Load local publication tasks
        loadPublicationTasksToMainProject(subprojectsToPublish, PublishTaskGenerator.PUBLISH_LOCAL_TASK, PublicationLoader.PUBLISH_LOCAL_DUMMY_TASK)

        // Load maven publication tasks
        loadPublicationTasksToMainProject(subprojectsToPublish, PublishTaskGenerator.PUBLISH_MAVEN_TASK, PublicationLoader.PUBLISH_MAVEN_DUMMY_TASK)
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
                def name = "${projectDependency.group}.${projectDependency.artifact}"
                def entryProject = new EntryProjects(name, projectDependency)

                // Adds the project to the unprocessed queue only if was not already processed
                if (!processedProjects.contains(entryProject) && !unprocessedProjects.contains(entryProject)) {
                    unprocessedProjects.add(entryProject)
                }
            }

            processedProjects.add(subprojectEntry)
        }

        return PublicationConfigurationUtils.queueToList(processedProjects)
    }

    void loadPublicationTasksToMainProject(List<Project> subprojects, String subprojectPublicationTaskName, String mainProjectPublicationTaskName) {
        // Get the local publication tasks
        List<Task> subprojectPublicationTasks = []

        for (Project subproject : subprojects) {
            def subprojectTask = subproject.tasks.findByName(subprojectPublicationTaskName)
            if (!subprojectTask) {
                throw new IllegalArgumentException("* The subproject '${subproject}' is missing the publication task '${subprojectPublicationTaskName}'.")
            }
            project.logger.info("* Adding the task '${subprojectTask.name}' from the '${subproject}' to be runned.")
            subprojectPublicationTasks.add(subprojectTask)
        }

        // Set task order
        GradleUtils.setTaskOrder(project, subprojectPublicationTasks)

        // Add each task to the main project local publication
        def mainPublicationTask = project.tasks.findByName(mainProjectPublicationTaskName)
        for (def subprojectTask : subprojectPublicationTasks) {
            mainPublicationTask.dependsOn(subprojectTask)
        }
    }

}
