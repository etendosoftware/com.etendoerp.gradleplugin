package com.etendoerp.setup.template

import org.gradle.api.Project

/**
 * Resolves templates from different sources: resources, files, or URLs
 */
class TemplateResolver {

    /**
     * Resolve a template from the specified source
     * @param project The Gradle project
     * @param templateName Template name from resources
     * @param filePath Local file path
     * @param url Remote URL
     * @return Resolved Template
     */
    static Template resolve(Project project, String templateName, String filePath, String url) {
        if (url) {
            return loadFromUrl(url)
        } else if (filePath) {
            return loadFromFile(filePath)
        } else if (templateName) {
            return loadFromResources(templateName)
        } else {
            return promptUserSelection(project)
        }
    }

    /**
     * Load template from resources
     */
    static Template loadFromResources(String name) {
        def resourcePath = "/templates/${name}.template"
        def stream = TemplateResolver.class.getResourceAsStream(resourcePath)
        
        if (!stream) {
            throw new IllegalArgumentException("Template '${name}' not found in resources. Available templates: ${listAvailableTemplates().join(', ')}")
        }
        
        String content = stream.text
        Template template = TemplateParser.parse(content, name)
        template.source = "resources"
        return template
    }

    /**
     * Load template from local file
     */
    static Template loadFromFile(String path) {
        File file = new File(path)
        if (!file.exists()) {
            throw new FileNotFoundException("Template file not found: ${path}")
        }
        
        String name = file.name.replaceAll(/\.template$/, '')
        Template template = TemplateParser.parse(file.text, name)
        template.source = "file:${path}"
        return template
    }

    /**
     * Load template from remote URL
     */
    static Template loadFromUrl(String url) {
        try {
            String content = new URL(url).text
            String name = url.split('/').last().replaceAll(/\.template$/, '')
            Template template = TemplateParser.parse(content, name)
            template.source = "url:${url}"
            return template
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to load template from URL: ${url}. Error: ${e.message}", e)
        }
    }

    /**
     * List available templates in resources
     */
    static List<String> listAvailableTemplates() {
        // List of bundled templates
        return ['copilot', 'base', 'production', 'development']
    }

    /**
     * Prompt user to select a template interactively
     */
    static Template promptUserSelection(Project project) {
        println "\nSelect one of the available templates:"
        
        List<String> templates = listAvailableTemplates()
        templates.eachWithIndex { name, index ->
            println "${index + 1}- ${name}"
        }
        
        println "\nYou can also use:"
        println "  --template=<templateName> (template)"
        println "  --file=/path/to/template  (local file)"
        println "  --url=https://...         (remote URL)"
        println ""
        
        // Read user input
        print "Enter your selection (1-${templates.size()}): "
        System.out.flush()
        
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))
        String input = reader.readLine()?.trim()
        
        if (!input) {
            throw new IllegalArgumentException("No selection made. Aborting.")
        }
        
        int selection
        try {
            selection = Integer.parseInt(input)
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid selection: '${input}'. Please enter a number between 1 and ${templates.size()}")
        }
        
        if (selection < 1 || selection > templates.size()) {
            throw new IllegalArgumentException("Invalid selection: ${selection}. Please enter a number between 1 and ${templates.size()}")
        }
        
        String selectedTemplate = templates[selection - 1]
        return loadFromResources(selectedTemplate)
    }
}
