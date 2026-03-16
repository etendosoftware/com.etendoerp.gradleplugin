package com.etendoerp.setup.template

/**
 * Detects and resolves placeholders in template property values.
 * Placeholders use the format {placeholder.name}.
 */
class PlaceholderResolver {

    private static final String PLACEHOLDER_PATTERN = /\{([a-zA-Z0-9_.]+)\}/

    /** Keys whose values should be masked during interactive input. */
    private static final Set<String> SENSITIVE_KEYS = ['openai.api.key'] as Set

    /**
     * Prompt definitions for the local template.
     * Each entry maps a placeholder key to its user-facing prompt message.
     */
    static final Map<String, String> LOCAL_PROMPTS = [
        'openai.api.key': 'OpenAI API Key'
    ]

    /**
     * Prompt definitions for the server template.
     * Each entry maps a placeholder key to its user-facing prompt message.
     */
    static final Map<String, String> SERVER_PROMPTS = [
        'context.url'   : 'Etendo Host (e.g., https://clienthost)',
        'openai.api.key': 'OpenAI API Key'
    ]

    /**
     * Detect all unique placeholders present in the template property values.
     * @param properties The template properties map
     * @return Set of placeholder names found
     */
    static Set<String> detectPlaceholders(Map<String, String> properties) {
        Set<String> placeholders = [] as LinkedHashSet
        properties.each { key, value ->
            def matcher = value =~ PLACEHOLDER_PATTERN
            while (matcher.find()) {
                placeholders.add(matcher.group(1))
            }
        }
        return placeholders
    }

    /**
     * Check whether the template has any placeholders that need user input.
     * @param template The template to check
     * @return true if placeholders are found
     */
    static boolean hasPlaceholders(Template template) {
        if (!template.properties) {
            return false
        }
        return !detectPlaceholders(template.properties).isEmpty()
    }

    /**
     * Collect user input for all required placeholders via console prompts.
     * For the 'context.url' placeholder:
     *   - If preResolved contains 'context.name', the user is expected to enter only the host
     *     (e.g. http://clienthost) and context.url / context.host are derived from host + context.name.
     *   - Otherwise the user enters the full URL and context.name / context.host are derived from it.
     *
     * @param placeholders The set of placeholder names that need values
     * @param prompts Map of placeholder name to prompt message
     * @param preResolved Values already resolved before prompting (e.g. context.name from gradle.properties)
     * @return Map of placeholder name to user-provided value (including derived values)
     */
    static Map<String, String> collectUserInput(Set<String> placeholders, Map<String, String> prompts,
                                                Map<String, String> preResolved = [:]) {
        Map<String, String> values = [:]
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))
        int current = 0
        int total = prompts.size()

        // Collect inputs that have prompts defined (in prompt order)
        prompts.each { key, prompt ->
            current++
            // Extra blank lines push Gradle's progress bar away from the prompt
            println ""
            println ""
            println ""
            println "  [${current}/${total}] ${prompt}"
            println ""
            print "  >> "
            System.out.flush()

            String input
            if (SENSITIVE_KEYS.contains(key)) {
                // Use console readPassword to mask sensitive input when available
                Console console = System.console()
                if (console != null) {
                    char[] chars = console.readPassword()
                    input = chars != null ? new String(chars).trim() : null
                } else {
                    // Fallback for environments without a console (e.g., IDE)
                    input = reader.readLine()?.trim()
                }
            } else {
                input = reader.readLine()?.trim()
            }

            if (!input) {
                throw new IllegalArgumentException("Value for '${key}' cannot be empty.")
            }

            values[key] = input

            // Derive context.url and context.host from the host input + pre-resolved context.name,
            // or fall back to deriving all three from a full URL when context.name is not pre-known.
            if (key == 'context.url') {
                String contextName = preResolved['context.name']
                if (contextName) {
                    // User entered only the host; build the full URL from host + context.name
                    String host = input.endsWith('/') ? input[0..-2] : input
                    values['context.url']  = "${host}/${contextName}"
                    values['context.host'] = "${host}/"
                } else {
                    deriveContextValues(input, values)
                }
            }
        }

        return values
    }

    /**
     * Derive context.name and context.host from a full context URL.
     * Example: "http://clienthost/mycompanyname"
     *   -> context.name = "mycompanyname"
     *   -> context.host = "http://clienthost/"
     *
     * @param contextUrl The full URL entered by the user
     * @param values The values map to populate with derived values
     */
    static void deriveContextValues(String contextUrl, Map<String, String> values) {
        try {
            URI uri = new URI(contextUrl)
            String path = uri.path

            if (path && path != '/') {
                // Remove leading slash and get segments
                String cleanPath = path.startsWith('/') ? path.substring(1) : path
                String[] segments = cleanPath.split('/')

                if (segments.length > 0 && segments[-1]) {
                    values['context.name'] = segments[-1]

                    // Build context.host: everything before the last segment
                    String fullUrl = contextUrl
                    int lastSegmentStart = fullUrl.lastIndexOf('/' + segments[-1])
                    if (lastSegmentStart >= 0) {
                        values['context.host'] = fullUrl.substring(0, lastSegmentStart + 1)
                    } else {
                        values['context.host'] = contextUrl
                    }
                } else {
                    values['context.name'] = 'etendo'
                    values['context.host'] = contextUrl
                }
            } else {
                values['context.name'] = 'etendo'
                values['context.host'] = contextUrl.endsWith('/') ? contextUrl : contextUrl + '/'
            }
        } catch (Exception e) {
            // Fallback: simple string parsing
            String url = contextUrl.endsWith('/') ? contextUrl[0..-2] : contextUrl
            int lastSlash = url.lastIndexOf('/')
            if (lastSlash > 0 && lastSlash > url.indexOf('://') + 2) {
                values['context.name'] = url.substring(lastSlash + 1)
                values['context.host'] = url.substring(0, lastSlash + 1)
            } else {
                values['context.name'] = 'etendo'
                values['context.host'] = contextUrl
            }
        }
    }

    /**
     * Substitute all placeholders in the template properties with the provided values.
     * @param properties The template properties map (modified in place)
     * @param values Map of placeholder name to resolved value
     */
    static void substitutePlaceholders(Map<String, String> properties, Map<String, String> values) {
        properties.each { key, value ->
            String resolved = value
            values.each { placeholder, replacement ->
                resolved = resolved.replace("{${placeholder}}", replacement)
            }
            properties[key] = resolved
        }
    }

    /**
     * Full resolution flow: detect placeholders, collect user input, and substitute.
     * @param template The template to resolve
     * @param preResolved Values already resolved before prompting (e.g. context.name from gradle.properties)
     * @return Map of user-provided values (including derived ones)
     */
    static Map<String, String> resolveInteractive(Template template, Map<String, String> preResolved = [:]) {
        Set<String> placeholders = detectPlaceholders(template.properties)
        Map<String, String> prompts = template.name == 'local' ? LOCAL_PROMPTS : SERVER_PROMPTS
        Map<String, String> values = collectUserInput(placeholders, prompts, preResolved)
        substitutePlaceholders(template.properties, values)
        return values
    }
}
