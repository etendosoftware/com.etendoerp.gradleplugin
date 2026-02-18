package com.etendoerp.setup

import org.gradle.api.Project

/**
 * Loader for setup-related tasks
 */
class SetupLoader {
    
    /**
     * Register setup tasks in the project
     * @param project The Gradle project
     */
    static void load(Project project) {
        project.tasks.register("setup.applyTemplates", SetupApplyTemplatesTask)
        project.tasks.register("setup.addModule", SetupAddModuleTask)
        project.tasks.register("setup.listModules", SetupListModulesTask)
        project.tasks.register("setup.githubAuth", GithubAuthTask)
    }
}
