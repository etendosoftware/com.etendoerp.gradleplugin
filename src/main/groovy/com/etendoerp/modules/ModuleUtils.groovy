package com.etendoerp.modules

import com.etendoerp.EtendoPluginExtension
import com.etendoerp.gradleutils.GradleUtils
import com.etendoerp.gradleutils.ProjectProperty
import com.etendoerp.jars.PathUtils
import com.etendoerp.legacy.ant.AntMenuHelper
import com.etendoerp.legacy.dependencies.ResolverDependencyUtils
import com.etendoerp.legacy.dependencies.container.ArtifactDependency
import com.etendoerp.legacy.modules.expand.ExpandUtils
import com.etendoerp.publication.PublicationUtils
import com.etendoerp.publication.configuration.EntryProjects
import com.etendoerp.publication.configuration.PublicationConfigurationUtils
import groovy.io.FileType
import org.gradle.api.Project

class ModuleUtils {

    static void loadSourcesProjectDependencies(Project mainProject, Map<String, Project> subprojectsMap) {
        subprojectsMap.each {
            loadSourceProjectDependencies(mainProject, it.value, subprojectsMap)
        }
    }

    /**
     * Load each 'subProject' with the source project dependencies.
     * The dependencies are stored with a custom property.
     * @param mainProject
     * @param subProject
     * @param subprojectsMap
     */
    static void loadSourceProjectDependencies(Project mainProject, Project subProject, Map<String, Project> subprojectsMap) {
        Map<String, Project> sourceProjects = new TreeMap<String, Project>(String.CASE_INSENSITIVE_ORDER)

        // Load the 'subProject' dependencies
        def configurationContainer = ResolverDependencyUtils.loadSubprojectDefaultDependencies(mainProject, subProject, false)

        configurationContainer.dependencies.each {
            String projectName = "${it.group}.${it.name}"
            def subproject = subprojectsMap.get(projectName)
            if (subproject) {
                sourceProjects.put(projectName, subproject)
            }
        }
        GradleUtils.loadProjectProperty(mainProject, subProject, ProjectProperty.SOURCE_MODULES_DEPENDENCY, sourceProjects)
    }

    /**
     * Given a 'subProject', obtains all the source project dependencies tree.
     * @param mainProject
     * @param subProject
     * @param subprojectsList
     * @return Map of the source module subproject name and the {@link Project}
     */
    static Map<String, Project> sourceProjectDependenciesTree(Project mainProject, Project subProject, Map<String, Project> subprojectsList) {

        loadSourcesProjectDependencies(mainProject, subprojectsList)

        Queue<Map.Entry<String, Project>> processedProjects = new LinkedList<>()
        Queue<Map.Entry<String, Project>> unprocessedProjects = new LinkedList<>()

        unprocessedProjects.add(new EntryProjects<String, Project>(PublicationUtils.loadModuleName(mainProject, subProject).get(), subProject))

        while (!unprocessedProjects.isEmpty()) {
            def subprojectEntry = unprocessedProjects.poll()
            Project subprojectToProcess = subprojectEntry.value

            Optional subprojectDependencies = GradleUtils.getProjectProperty(mainProject, subprojectToProcess, ProjectProperty.SOURCE_MODULES_DEPENDENCY)

            if (subprojectDependencies.isPresent()) {
                subprojectDependencies.get().each {
                    def entryProject = new EntryProjects(it.key, it.value)
                    // Adds the project to the unprocessed queue only if was not already processed
                    if (!processedProjects.contains(entryProject) && !unprocessedProjects.contains(entryProject)) {
                        unprocessedProjects.add(entryProject)
                    }
                }
            }
            processedProjects.add(subprojectEntry)
        }

        // Convert to map
        return PublicationConfigurationUtils.queueToMap(processedProjects)
    }

    static Map<String, Project> collectSourceModulesToUninstall(Project mainProject, Project moduleToUninstall, Map<String, Project> subprojectsList) {
        // Collect all the source modules dependencies from the module to uninstall
        def sourceProjectDependencies = sourceProjectDependenciesTree(mainProject, moduleToUninstall, subprojectsList)

        // Validate the modules to uninstall
        // If the module to uninstall is dependency of other module that is not gonna be uninstalled, clear it from the list.
        return filterValidModulesToUninstall(mainProject, moduleToUninstall, sourceProjectDependencies, subprojectsList)
    }

    /**
     * Filter those modules that will be uninstalled that are dependency of the source modules to keep.
     * If a dependency is removed from the list to uninstall, also the transitives should be filtered.
     * @param mainProject
     * @param moduleToUninstall
     * @param subProjectsToUninstall
     * @param subprojectsList
     * @return
     */
    static Map<String, Project> filterValidModulesToUninstall(Project mainProject, Project moduleToUninstall, Map<String, Project> subProjectsToUninstall, Map<String, Project> subprojectsList) {
        Map<String, Project> subProjectsToUninstallClone = subProjectsToUninstall.clone() as Map<String, Project>
        Map<String, Project> subprojectsListClone = subprojectsList.clone() as Map<String, Project>
        subprojectsListClone.keySet().removeAll(subProjectsToUninstallClone.keySet())
        def sourceModulesToKeep = subprojectsListClone

        Queue<Map.Entry<String, Project>> processedProjects = new LinkedList<>()
        Queue<Map.Entry<String, Project>> unprocessedProjects = new LinkedList<>()

        sourceModulesToKeep.each {
            unprocessedProjects.add(new EntryProjects<String, Project>(PublicationUtils.loadModuleName(mainProject, it.value).get(), it.value))
        }

        String forceProp = (mainProject.findProperty(ExpandUtils.FORCE_PROPERTY) ?: "false")

        while (!unprocessedProjects.isEmpty()) {
            def subprojectEntry = unprocessedProjects.poll()
            Project subprojectToProcess = subprojectEntry.value

            Optional subprojectDependenciesOptional = GradleUtils.getProjectProperty(mainProject, subprojectToProcess, ProjectProperty.SOURCE_MODULES_DEPENDENCY)

            if (subprojectDependenciesOptional.isPresent()) {

                def subprojectDependencies = subprojectDependenciesOptional.get()

                // The module to uninstall is a dependency of other source module to keep
                if (subprojectDependencies.containsKey(PublicationUtils.loadModuleName(moduleToUninstall)) && !forceProp.toBoolean()) {
                    throw new IllegalAccessException("* The module ${moduleToUninstall} could not be uninstalled because is a dependency of ${subprojectToProcess}. \n" +
                                                     "* To force the uninstall run the task with '-P${ExpandUtils.FORCE_PROPERTY}=true'. ")
                }

                subprojectDependencies.each {
                    // If the dependency is in the list to be uninstalled.
                    // remove it and add it to the unprocessed queue to also obtain the transitive dependencies
                    if (subProjectsToUninstallClone.containsKey(it.key) && !PublicationUtils.loadModuleName(moduleToUninstall).equalsIgnoreCase(it.key)) {
                        mainProject.logger.info("* Excluding project '${it.key}' from uninstall as is a dependency of '${subprojectToProcess}'")
                        subProjectsToUninstallClone.remove(it.key)
                        def entryProject = new EntryProjects(it.key, it.value)
                        if (!processedProjects.contains(entryProject) && !unprocessedProjects.contains(entryProject)) {
                            unprocessedProjects.add(entryProject)
                        }
                    }
                }
            }
            processedProjects.add(subprojectEntry)
        }
        return subProjectsToUninstallClone
    }

    static boolean shouldUninstallModulesMenu(Project mainProject, Project moduleToUninstall, Map<String, Project> subProjectsToUninstall) {
        String message = modulesToUninstallMessage(mainProject, moduleToUninstall, subProjectsToUninstall)
        return AntMenuHelper.confirmationMenu(mainProject, message)
    }

    static String modulesToUninstallMessage(Project mainProject,Project moduleToUninstall, Map<String, Project> subProjectsToUninstall) {
        StringBuilder stringBuilder = new StringBuilder()
        subProjectsToUninstall.each {
            stringBuilder.append("* Module: ${it.key} \n")
        }
        return """
        |*************** MODULES TO UNINSTALL ***************
        |${stringBuilder.toString()}
        |* The modules will be deleted from the source modules directory.
        |* To reflect the changes in the database you need to run a 'update.database'
        |""".stripMargin()
    }

    static void deleteModules(Project mainProject, Map<String, Project> subProjectsToUninstall) {
        subProjectsToUninstall.each {
            mainProject.delete(it.value.projectDir)
        }
    }

    static void cleanBuildModules(Project mainProject) {
        File buildModulesDir = new File(PathUtils.createPath(mainProject.buildDir.absolutePath, PublicationUtils.ETENDO, PublicationUtils.BASE_MODULE_DIR))

        if (buildModulesDir.exists()) {
            mainProject.logger.info("* Cleaning directory: ${buildModulesDir.absolutePath}")
            mainProject.delete(buildModulesDir)
        }
    }

    static Map<String, File> getJarModules(Project mainProject) {
        Map<String, File> jarModules = new TreeMap<>(String.CASE_INSENSITIVE_ORDER)
        File jarModulesLocation = new File(mainProject.buildDir, "etendo" + File.separator + "modules")

        if (jarModulesLocation.exists()) {
            jarModulesLocation.traverse(type: FileType.DIRECTORIES, maxDepth: 0) {
                jarModules.put(it.name, it)
            }
        }
        return jarModules
    }

    static Map<String, Project> loadValidSubprojectDependencies(Project mainProject) {
       return GradleUtils.loadProjectProperty(mainProject, mainProject, ProjectProperty.VALID_SOURCE_MODULES, getValidSubprojectDependencies(mainProject))
    }

    static Map<String, Project> getValidSubprojectDependencies(Project mainProject) {
        Map<String, Project> validSubprojects = new TreeMap<>(String.CASE_INSENSITIVE_ORDER)

        // Load module subprojects
        def baseModuleProject = mainProject.findProject(":${PublicationUtils.BASE_MODULE_DIR}")

        if (baseModuleProject) {
            // Filter the valid Etendo modules.
            validSubprojects = PublicationConfigurationUtils.generateProjectMap(baseModuleProject.subprojects.findAll({
                PublicationUtils.loadModuleName(mainProject, it).isPresent()
            }).collect())
        }

        return validSubprojects
    }

    static boolean isValidSubproject(Project mainProject, Project subProject) {
        return PublicationUtils.loadModuleName(mainProject, subProject).isPresent()
    }

}
