package com.etendoerp.setup

import groovy.json.JsonBuilder
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option

/**
 * Task to list all installed modules (artifacts and git repositories)
 * 
 * Usage:
 *   ./gradlew setup.listModules                    (table format)
 *   ./gradlew setup.listModules --format=json      (JSON format)
 */
class SetupListModulesTask extends DefaultTask {

    @Input
    @Optional
    @Option(option = "format", description = "Output format: table, json")
    String format = "table"

    SetupListModulesTask() {
        group = 'setup'
        description = 'List all installed modules (artifacts and git repositories)'
    }

    @TaskAction
    void execute() {
        try {
            def artifacts = ModuleManager.listInstalledArtifacts(project)
            def gitModules = ModuleManager.listInstalledGitModules(project)
            
            if (format == "json") {
                outputJson(artifacts, gitModules)
            } else {
                outputTable(artifacts, gitModules)
            }
            
        } catch (Exception e) {
            project.logger.error("Failed to list modules: ${e.message}", e)
            throw e
        }
    }
    
    /**
     * Output modules in table format (human-readable)
     */
    private void outputTable(List<String> artifacts, List<ModuleManager.GitModuleInfo> gitModules) {
        println "\n=== Installed Modules ==="
        println ""
        
        // Artifacts section
        println "Artifacts (from artifacts.list.COMPILATION.gradle):"
        if (artifacts.isEmpty()) {
            println "  (none)"
        } else {
            artifacts.eachWithIndex { artifact, index ->
                println "  ${index + 1}. ${artifact}"
            }
        }
        println ""
        
        // Git modules section
        println "Git Modules (from modules/ directory):"
        if (gitModules.isEmpty()) {
            println "  (none)"
        } else {
            gitModules.eachWithIndex { module, index ->
                println "  ${index + 1}. ${module.name}"
                println "     - Path: ${module.path}"
                println "     - Branch: ${module.currentBranch ?: 'unknown'}"
                println "     - Remote: ${module.remoteUrl ?: 'unknown'}"
                println ""
            }
        }
        
        // Summary
        println "Total: ${artifacts.size()} artifact${artifacts.size() == 1 ? '' : 's'}, ${gitModules.size()} git module${gitModules.size() == 1 ? '' : 's'}"
        println ""
    }
    
    /**
     * Output modules in JSON format (machine-readable)
     */
    private void outputJson(List<String> artifacts, List<ModuleManager.GitModuleInfo> gitModules) {
        def result = [
            artifacts: artifacts,
            gitModules: gitModules.collect { module ->
                [
                    name: module.name,
                    path: module.path,
                    branch: module.currentBranch,
                    remoteUrl: module.remoteUrl
                ]
            },
            summary: [
                totalArtifacts: artifacts.size(),
                totalGitModules: gitModules.size()
            ]
        ]
        
        def json = new JsonBuilder(result)
        println json.toPrettyString()
    }
}
