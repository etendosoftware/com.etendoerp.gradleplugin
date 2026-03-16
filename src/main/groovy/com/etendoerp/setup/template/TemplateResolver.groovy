package com.etendoerp.setup.template

import org.gradle.api.Project

/**
 * Resolves templates from different sources: resources, files, or URLs.
 * When a template contains placeholders, prompts the user for input
 * and substitutes the values before returning.
 */
class TemplateResolver {

    /**
     * Resolve a template from the specified source.
     * After resolution, if the template contains placeholders the user
     * is prompted for values and the placeholders are substituted.
     *
     * @param project The Gradle project
     * @param templateName Template name from resources
     * @param filePath Local file path
     * @param url Remote URL
     * @return Resolved Template with all placeholders substituted
     */
    static Template resolve(Project project, String templateName, String filePath, String url) {
        Template template

        if (url) {
            template = loadFromUrl(url)
        } else if (filePath) {
            template = loadFromFile(filePath)
        } else if (templateName) {
            template = loadFromResources(templateName)
        } else {
            template = promptUserSelection(project)
        }

        // For local and server templates, pre-substitute context.name from gradle.properties (no user prompt)
        Map<String, String> preResolved = [:]
        if (template && template.name in ['local', 'server']) {
            String fromProps = readContextNameFromProperties(project)
            String contextName = fromProps ?: 'etendo'
            String source = fromProps ? 'gradle.properties' : 'default'
            project.logger.lifecycle("Using context.name='${contextName}' (from ${source}) for ${template.name} template URL resolution.")
            PlaceholderResolver.substitutePlaceholders(template.properties, ['context.name': contextName])
            preResolved['context.name'] = contextName
        }

        // Resolve placeholders if the template has any
        if (template && PlaceholderResolver.hasPlaceholders(template)) {
            println ""
            println ""
            println ""
            println "======================================================"
            println "  CONFIGURATION REQUIRED"
            println "  Template '${template.name}' needs the following input"
            println "  Please type each value and press ENTER"
            println "======================================================"
            PlaceholderResolver.resolveInteractive(template, preResolved)
        }

        return template
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
     * List available templates in resources by scanning the templates directory
     */
    static List<String> listAvailableTemplates() {
        List<String> templates = []
        def templatesUrl = TemplateResolver.class.getResource("/templates/")
        if (templatesUrl) {
            if (templatesUrl.protocol == 'file') {
                new File(templatesUrl.toURI()).listFiles()?.each { File f ->
                    if (f.name.endsWith('.template')) {
                        templates.add(f.name.replaceAll(/\.template$/, ''))
                    }
                }
            } else if (templatesUrl.protocol == 'jar') {
                def jarPath = templatesUrl.path.substring(5, templatesUrl.path.indexOf('!'))
                def jar = new java.util.jar.JarFile(jarPath)
                jar.entries().each { entry ->
                    if (entry.name.startsWith('templates/') && entry.name.endsWith('.template')) {
                        templates.add(entry.name.replace('templates/', '').replaceAll(/\.template$/, ''))
                    }
                }
                jar.close()
            }
        }
        return templates.sort()
    }

    /**
     * Prompt user to select a template interactively
     */
    static Template promptUserSelection(Project project) {
        println ""
        println ""
        println ""
        println "======================================================"
        println "  SELECT A TEMPLATE"
        println "======================================================"

        List<String> templates = listAvailableTemplates()
        templates.eachWithIndex { name, index ->
            println "  ${index + 1}) ${name}"
        }

        println ""
        println "  You can also use:"
        println "    --template=<name>  (by name)"
        println "    --file=<path>      (local file)"
        println "    --url=<url>        (remote URL)"
        println ""
        print "  >> Enter your selection (1-${templates.size()}): "
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

    /**
     * Read the value of the 'context.name' property from the project's gradle.properties file.
     *
     * @param project The Gradle project
     * @return The trimmed value of 'context.name', or null if the file does not exist or the property is not set
     */
    private static String readContextNameFromProperties(Project project) {
        File propsFile = project.file('gradle.properties')
        if (!propsFile.exists()) {
            return null
        }
        Properties props = new Properties()
        propsFile.withInputStream { props.load(it) }
        return props.getProperty('context.name')?.trim() ?: null
    }
}
