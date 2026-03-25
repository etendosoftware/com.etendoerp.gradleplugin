package com.etendoerp.setup.template

/**
 * Parser for template files
 * Handles the parsing of [properties], [dependencies], and [modules] sections
 */
class TemplateParser {

    /**
     * Parse template content and return a Template object
     * @param content The content of the template file
     * @param name The name of the template
     * @return Populated Template object
     */
    static Template parse(String content, String name) {
        Template template = new Template(name: name)
        TemplateSection currentSection = null

        content.eachLine { line ->
            String trimmedLine = line.trim()

            // Detect section headers
            if (trimmedLine.startsWith('[') && trimmedLine.endsWith(']')) {
                String sectionName = trimmedLine[1..-2]
                currentSection = TemplateSection.fromString(sectionName)

                if (!currentSection) {
                    throw new IllegalArgumentException("Unknown section: [${sectionName}] in template '${name}'")
                }
                return
            }

            // Skip empty lines outside sections
            if (trimmedLine.isEmpty()) {
                return
            }

            // Preserve comments inside [properties] section for section separators
            if (currentSection == TemplateSection.PROPERTIES && trimmedLine.startsWith('#')) {
                template.propertyOrder.add(trimmedLine)
                return
            }

            // Skip comments in other sections
            if (trimmedLine.startsWith('#')) {
                return
            }

            // Process content based on current section
            if (currentSection) {
                processLine(template, currentSection, trimmedLine)
            }
        }

        return template
    }

    /**
     * Process a line according to the current section
     */
    private static void processLine(Template template, TemplateSection section, String line) {
        switch (section) {
            case TemplateSection.PROPERTIES:
                parseProperty(template, line)
                break
            case TemplateSection.DEPENDENCIES:
                template.dependencies.add(line)
                break
            case TemplateSection.MODULES:
                template.modules.add(line)
                break
        }
    }

    /**
     * Parse a property line in format key=value
     */
    private static void parseProperty(Template template, String line) {
        int equalsIndex = line.indexOf('=')
        if (equalsIndex > 0) {
            String key = line.substring(0, equalsIndex).trim()
            String value = line.substring(equalsIndex + 1).trim()
            template.properties[key] = value
            template.propertyOrder.add(key)
        } else {
            throw new IllegalArgumentException("Invalid property format: '${line}'. Expected format: key=value")
        }
    }
}
