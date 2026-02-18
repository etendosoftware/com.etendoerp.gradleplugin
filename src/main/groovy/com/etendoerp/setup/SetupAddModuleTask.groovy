package com.etendoerp.setup

import com.etendoerp.setup.applicator.ModuleApplicator
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option

/**
 * Task to add individual modules (artifacts or git repositories)
 * 
 * Usage:
 *   ./gradlew setup.addModule --artifact=group:artifact:version
 *   ./gradlew setup.addModule --git=url --branch=branch
 */
class SetupAddModuleTask extends DefaultTask {

    @Input
    @Optional
    @Option(option = "artifact", description = "Artifact coordinate (group:artifact:version)")
    String artifact

    @Input
    @Optional
    @Option(option = "git", description = "Git repository URL")
    String git

    @Input
    @Optional
    @Option(option = "branch", description = "Git branch (default: main/master)")
    String branch = null

    SetupAddModuleTask() {
        group = 'setup'
        description = 'Add a module (artifact or git repository) to the project'
    }

    @TaskAction
    void execute() {
        try {
            // Validate: only one option must be provided
            if (!artifact && !git) {
                throw new IllegalArgumentException(
                    "You must provide either --artifact or --git option.\n\n" +
                    "Usage:\n" +
                    "  ./gradlew setup.addModule --artifact=group:artifact:version\n" +
                    "  ./gradlew setup.addModule --git=url --branch=branch\n\n" +
                    "Examples:\n" +
                    "  ./gradlew setup.addModule --artifact=com.etendoerp:warehouse:2.1.0\n" +
                    "  ./gradlew setup.addModule --git=https://github.com/user/repo.git --branch=develop"
                )
            }
            
            if (artifact && git) {
                throw new IllegalArgumentException(
                    "You cannot provide both --artifact and --git options. Choose one.\n\n" +
                    "For artifacts, use:\n" +
                    "  ./gradlew setup.addModule --artifact=group:artifact:version\n\n" +
                    "For git repositories, use:\n" +
                    "  ./gradlew setup.addModule --git=url --branch=branch"
                )
            }
            
            if (artifact) {
                addArtifact()
            } else {
                addGitModule()
            }
            
        } catch (Exception e) {
            project.logger.error("Failed to add module: ${e.message}", e)
            throw e
        }
    }
    
    /**
     * Add an artifact module
     */
    private void addArtifact() {
        // Validate artifact format
        if (!artifact.matches(/^[\w\.\-]+:[\w\.\-]+:[\w\.\-]+$/)) {
            throw new IllegalArgumentException(
                "Invalid artifact format: ${artifact}\n\n" +
                "Expected format: group:artifact:version\n" +
                "Example: com.etendoerp:copilot:1.0.0\n\n" +
                "Your artifact must have three parts separated by colons:\n" +
                "  - group: ${artifact.split(':')[0] ?: '(missing)'}\n" +
                "  - artifact: ${artifact.split(':').size() > 1 ? artifact.split(':')[1] : '(missing)'}\n" +
                "  - version: ${artifact.split(':').size() > 2 ? artifact.split(':')[2] : '(missing)'}"
            )
        }
        
        println "\nAdding artifact module: ${artifact}"
        
        // Check if already installed
        if (ModuleManager.isArtifactInstalled(project, artifact)) {
            println "*** artifact: ${artifact}"
            println "*** (already in artifacts list)"
            println "\nModule already exists. Skipped."
            return
        }
        
        // Add using ModuleApplicator
        ModuleApplicator.apply(project, [artifact])
        
        println "\nModule added successfully!"
    }
    
    /**
     * Add a git module
     */
    private void addGitModule() {
        println "\nAdding git module: ${git}"
        
        // Extract module name
        String moduleName = ModuleManager.extractModuleName(git)
        
        // Check if already installed
        if (ModuleManager.isGitModuleInstalled(project, moduleName)) {
            println "*** git: ${git} (already cloned in modules/${moduleName})"
            println "\nModule already exists. Skipped."
            return
        }
        
        // Build git spec
        String gitSpec = branch 
            ? "git::${git}::branch=${branch}" 
            : "git::${git}"
        
        // Add using ModuleApplicator
        ModuleApplicator.apply(project, [gitSpec])
        
        println "\nModule cloned successfully!"
    }
}
