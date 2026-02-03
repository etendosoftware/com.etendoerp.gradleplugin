package com.etendoerp.setup

import com.etendoerp.setup.applicator.TemplateApplicator
import com.etendoerp.setup.template.Template
import com.etendoerp.setup.template.TemplateResolver
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option

/**
 * Main task for applying configuration templates
 * 
 * Usage:
 *   ./gradlew setup.applyTemplates                              (interactive mode)
 *   ./gradlew setup.applyTemplates --template=copilot           (from resources)
 *   ./gradlew setup.applyTemplates --file=/path/to/template     (from file)
 *   ./gradlew setup.applyTemplates --url=https://...            (from URL)
 */
class SetupApplyTemplatesTask extends DefaultTask {

    @Input
    @Optional
    @Option(option = "template", description = "Template name from resources")
    String template

    @Input
    @Optional
    @Option(option = "file", description = "Template from local file path")
    String file

    @Input
    @Optional
    @Option(option = "url", description = "Template from remote URL")
    String url

    SetupApplyTemplatesTask() {
        group = 'setup'
        description = 'Apply configuration templates to the project'
    }

    @TaskAction
    void execute() {
        try {
            // Resolve template from the specified source
            Template resolvedTemplate = TemplateResolver.resolve(project, template, file, url)
            
            if (!resolvedTemplate) {
                throw new IllegalStateException("Failed to resolve template")
            }
            
            project.logger.info("Resolved template: ${resolvedTemplate}")
            
            // Apply the template
            TemplateApplicator.apply(project, resolvedTemplate)
            
        } catch (Exception e) {
            project.logger.error("Failed to apply template: ${e.message}", e)
            throw e
        }
    }
}
