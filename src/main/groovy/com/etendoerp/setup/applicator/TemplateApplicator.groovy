package com.etendoerp.setup.applicator

import com.etendoerp.setup.template.Template
import org.gradle.api.Project

/**
 * Orchestrates the application of a template to a project
 */
class TemplateApplicator {

    /**
     * Apply a template to the project
     * @param project The Gradle project
     * @param template The template to apply
     */
    static void apply(Project project, Template template) {
        println "\nApplying template: ${template.name}"
        
        // Create backups before modifying files
        createBackups(project)
        
        // Apply properties section
        if (template.properties && !template.properties.isEmpty()) {
            println "  [properties] -> gradle.properties"
            PropertyApplicator.apply(project, template.properties)
        }
        
        // Apply dependencies section
        if (template.dependencies && !template.dependencies.isEmpty()) {
            println "  [dependencies] -> build.gradle"
            DependencyApplicator.apply(project, template.dependencies)
        }
        
        // Apply modules section
        if (template.modules && !template.modules.isEmpty()) {
            println "  [modules]"
            ModuleApplicator.apply(project, template.modules)
        }
        
        println "\nTemplate '${template.name}' applied successfully"
    }

    /**
     * Create backup of files that will be modified
     */
    private static void createBackups(Project project) {
        String timestamp = new Date().format('yyyyMMdd_HHmmss')
        File backupDir = new File(project.rootDir, ".template-backups")
        backupDir.mkdirs()
        
        // Backup gradle.properties
        File propsFile = new File(project.rootDir, 'gradle.properties')
        if (propsFile.exists()) {
            File propsBackup = new File(backupDir, "gradle.properties.${timestamp}")
            propsBackup.text = propsFile.text
            project.logger.info("Created backup: ${propsBackup.absolutePath}")
        }
        
        // Backup build.gradle
        File buildFile = new File(project.rootDir, 'build.gradle')
        if (buildFile.exists()) {
            File buildBackup = new File(backupDir, "build.gradle.${timestamp}")
            buildBackup.text = buildFile.text
            project.logger.info("Created backup: ${buildBackup.absolutePath}")
        }
    }
}
