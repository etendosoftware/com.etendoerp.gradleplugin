package com.etendoerp.setup.applicator

import org.gradle.api.Project

/**
 * Applies dependencies to build.gradle file
 */
class DependencyApplicator {

    /**
     * Apply dependencies to build.gradle
     * @param project The Gradle project
     * @param dependencies List of dependencies to add
     */
    static void apply(Project project, List<String> dependencies) {
        if (!dependencies) {
            return
        }

        File buildFile = project.file('build.gradle')
        
        if (!buildFile.exists()) {
            throw new FileNotFoundException("build.gradle not found in project root")
        }

        String content = buildFile.text
        
        // Separate new dependencies to add
        List<String> newDeps = []
        
        dependencies.each { dep ->
            // Check if dependency already exists (considering variations in quotes)
            String depPattern = dep.replaceAll(/["']/, '.')
            if (content.contains(dep) || content.matches(/(?s).*${depPattern}.*/)) {
                println "*** ${dep} (already exists)"
            } else {
                newDeps.add(dep)
                println "*** ${dep}"
            }
        }
        
        // Add all new dependencies at once with Template Dependencies section
        if (newDeps) {
            content = addDependenciesToBlock(content, newDeps)
            buildFile.text = content
        }
    }

    /**
     * Add dependencies to the dependencies block with Template Dependencies section
     */
    private static String addDependenciesToBlock(String content, List<String> dependencies) {
        // Find the dependencies block
        def dependenciesPattern = /(?s)(dependencies\s*\{)(.*?)(\n\})/
        def matcher = content =~ dependenciesPattern
        
        if (matcher.find()) {
            String before = matcher.group(1)
            String block = matcher.group(2)
            String after = matcher.group(3)
            
            // Determine indentation from existing dependencies
            String indentation = detectIndentation(block)
            
            // Check if Template Dependencies section already exists
            boolean hasTemplateSection = block.contains('// Template Dependencies')
            
            // Build the new dependencies block
            StringBuilder newBlock = new StringBuilder(block)
            
            if (!hasTemplateSection) {
                // Add Template Dependencies section header
                newBlock.append("\n${indentation}// Template Dependencies")
            }
            
            // Add each dependency
            dependencies.each { dep ->
                newBlock.append("\n${indentation}${dep}")
            }
            
            return content.replaceFirst(dependenciesPattern, "${before}${newBlock.toString()}${after}")
        } else {
            throw new IllegalStateException("Could not find dependencies block in build.gradle")
        }
    }

    /**
     * Add a dependency to the dependencies block (legacy method for backward compatibility)
     */
    private static String addDependencyToBlock(String content, String dependency) {
        return addDependenciesToBlock(content, [dependency])
    }

    /**
     * Detect indentation used in the dependencies block
     */
    private static String detectIndentation(String block) {
        // Look for existing dependencies to match indentation
        def lines = block.split('\n')
        for (line in lines) {
            if (line.trim() && !line.trim().startsWith('//') && !line.trim().startsWith('/*')) {
                def leadingSpaces = line.replaceFirst(/^(\s*).*$/, '$1')
                if (leadingSpaces) {
                    return leadingSpaces
                }
            }
        }
        // Default to 4 spaces
        return '    '
    }
}
