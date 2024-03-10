package com.etendoerp.publication.buildfile.update

import com.etendoerp.core.CoreMetadata
import com.etendoerp.publication.PublicationUtils
import com.etendoerp.publication.buildfile.BuildMetadata
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency

/**
 * Class to update build metadata
 */
class BuildMetadataUpdate {

    static final String UPDATE_MODULE_BUILD = 'updateModuleBuildDependency'
    static final String DEPENDENCY_PROP = 'dependency'

    static final String LOWER_BOUND_PROP = 'lowerBound'
    static final String LOWER_BOUND_INCLUSIVE = 'lowerBoundInclusive'

    static final String UPPER_PROP = 'upperBound'
    static final String UPPER_BOUND_INCLUSIVE = 'upperBoundInclusive'

    static final String EXACT_VERSION = 'exactVersion'

    /**
     * Load method to update build metadata
     * @param project The project to update
     */
    static void load(Project project) {
        project.tasks.register(UPDATE_MODULE_BUILD) {
            doLast {
                String dependencyName = project.findProperty(DEPENDENCY_PROP) ?: "${CoreMetadata.DEFAULT_ETENDO_CORE_GROUP}.${CoreMetadata.DEFAULT_ETENDO_CORE_NAME}"
                def moduleProject = project.findProject(":${PublicationUtils.BASE_MODULE_DIR}")
                if (!moduleProject) {
                    throw new IllegalStateException("* The project :${PublicationUtils.BASE_MODULE_DIR} does not exists.")
                }
                List<Project> moduleSubprojects = moduleProject.subprojects.toList()
                for (Project subProject : moduleSubprojects) {
                    def subProjectDependencies = subProject.configurations*.dependencies
                    def dependencies = []
                    subProjectDependencies.each {
                        dependencies.addAll(it.findAll {
                            "${it.group}.${it.name}".equalsIgnoreCase(dependencyName)
                        })
                    }
                    if (dependencies) {
                        project.logger.info('*** Updating dependencies versions ***')
                        for (Dependency dependency : dependencies) {
                            updateBuildGradleDependency(project, subProject, dependency)
                        }
                    }
                }
            }
        }
    }

    /**
     * Update build.gradle file with dependency
     * @param mainProject The main project
     * @param subProject The sub project
     * @param dependency The dependency to update
     */
    static void updateBuildGradleDependency(Project mainProject, Project subProject, Dependency dependency) {
        String projectLocationPath = subProject.projectDir.absolutePath
        // Get the 'build.gradle' file
        String buildGradleLocation = 'build.gradle'
        File originalBuildGradle = new File(projectLocationPath, buildGradleLocation)
        if (originalBuildGradle.exists()) {
            updateBuildGradle(mainProject, subProject, originalBuildGradle, dependency)
        }
    }

    /**
     * Update build.gradle file content
     * @param mainProject The main project
     * @param subProject The sub project
     * @param buildGradleFile The build.gradle file
     * @param dependency The dependency to update
     */
    static void updateBuildGradle(Project mainProject, Project subProject, File buildGradleFile, Dependency dependency) {
        String buildGradleText = buildGradleFile.text
        String auxBuildFile = ''
        boolean dependenciesBlock = false
        buildGradleText.eachLine {
            // Update dependencies
            if (it.startsWith(BuildMetadata.DEPENDENCIES)) {
                dependenciesBlock = true
            }
            if (dependenciesBlock) {
                it = containsDependency(mainProject, subProject, it, dependency)
            }
            auxBuildFile += "${it}\n"
        }
        buildGradleFile.text = auxBuildFile
    }

    /**
     * Check if line contains dependency and update version
     * @param mainProject The main project
     * @param subProject The sub project
     * @param line The line to check
     * @param dependency The dependency to update
     * @return The updated line
     */
    static String containsDependency(Project mainProject, Project subProject, String line, Dependency dependency) {
        String originalVersion = dependency.version
        if (line.contains(dependency.group) && line.contains(dependency.name) && line.contains(originalVersion)) {
            String newVersion = updateVersionRanges(mainProject, originalVersion)
            String newLine = line.replace(originalVersion, newVersion)
            mainProject.logger.info('********** UPDATE DEPENDENCY **********')
            mainProject.logger.info('* Subproject: ${subProject.path}')
            mainProject.logger.info('* OLD: ${line}')
            mainProject.logger.info('* NEW: ${newLine}')
            mainProject.logger.info('***************************************')
            line = newLine
        }
        return line
    }

    /**
     * Update version ranges for a given version
     * @param project The project
     * @param version The version to update
     * @return The updated version
     */
    static String updateVersionRanges(Project project, String version) {
        String lowerBound = project.findProperty(LOWER_BOUND_PROP)
        String lowerBoundInclusive = project.findProperty(LOWER_BOUND_INCLUSIVE) ?: 'false'
        String upperBound = project.findProperty(UPPER_PROP)
        String upperBoundInclusive = project.findProperty(UPPER_BOUND_INCLUSIVE) ?: 'false'
        String exactVersion = project.findProperty(EXACT_VERSION)
        if (exactVersion) {
            return exactVersion
        }
        String lowerBoundSymbol = (lowerBoundInclusive.toBoolean()) ? '[' : '('
        String upperBoundSymbol = (upperBoundInclusive.toBoolean()) ? ']' : ')'
        if (lowerBound) {
            if (version.contains(',')) {
                version = lowerBoundSymbol + lowerBound + version[version.indexOf(',')..-1]
            } else {
                version = clearVersionSymbols(version)
                version = lowerBoundSymbol + lowerBound + ',' + version + upperBoundSymbol
            }
        }
        if (upperBound) {
            if (version.contains(',')) {
                version = version[0..version.lastIndexOf(',') + 1] + upperBound + upperBoundSymbol
            } else {
                version = clearVersionSymbols(version)
                version = lowerBoundSymbol + version + ',' + upperBound + upperBoundSymbol
            }
        }
        return version
    }

    /**
     * Clear version symbols
     * @param version The version to clear
     * @return The cleared version
     */
    static String clearVersionSymbols(String version) {
        return version
                .replace('(', '')
                .replace('[', '')
                .replace(')', '')
                .replace(']', '')
    }
}