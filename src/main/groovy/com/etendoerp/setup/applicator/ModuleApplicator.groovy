package com.etendoerp.setup.applicator

import org.gradle.api.Project

/**
 * Applies modules (artifacts and git repositories) to the project
 */
class ModuleApplicator {

    /**
     * Apply modules to the project
     * @param project The Gradle project
     * @param modules List of modules to apply
     */
    static void apply(Project project, List<String> modules) {
        if (!modules) {
            return
        }

        modules.each { module ->
            if (module.startsWith('git::')) {
                applyGitModule(project, module)
            } else {
                applyArtifactModule(project, module)
            }
        }
    }

    /**
     * Apply a git-based module
     * Format: git::<url>::branch=<branch>
     */
    private static void applyGitModule(Project project, String gitModule) {
        try {
            def parts = gitModule.split('::')
            if (parts.length < 2) {
                throw new IllegalArgumentException("Invalid git module format: ${gitModule}. Expected: git::<url>::branch=<branch>")
            }
            
            String url = parts[1]
            String branch = null
            
            // Parse branch if provided
            if (parts.length >= 3) {
                def branchPart = parts[2]
                if (branchPart.startsWith('branch=')) {
                    branch = branchPart.substring(7)
                }
            }
            
            // Extract module name from URL
            String moduleName = extractModuleName(url)
            
            // Create modules directory if it doesn't exist
            def modulesDir = project.file('modules')
            if (!modulesDir.exists()) {
                modulesDir.mkdirs()
                project.logger.info("Created modules directory: ${modulesDir.absolutePath}")
            }
            
            // Target directory for the cloned module
            def targetDir = new File(modulesDir, moduleName)
            
            // Check if module already exists
            if (targetDir.exists()) {
                project.logger.warn("Module directory already exists: ${targetDir.absolutePath}. Skipping clone.")
                println "*** git: ${url} (already cloned in modules/${moduleName})"
                return
            }
            
            // Clone the git repository with branch detection
            boolean cloneSuccessful = false
            String usedBranch = branch
            
            if (branch != null) {
                // Branch specified, try to clone it
                try {
                    println "*** git: ${url} (branch: ${branch})"
                    project.exec {
                        workingDir modulesDir
                        commandLine 'git', 'clone', '-b', branch, url, moduleName
                    }
                    cloneSuccessful = true
                } catch (Exception cloneEx) {
                    project.logger.error("Failed to clone git module with branch ${branch}: ${url}", cloneEx)
                    throw new RuntimeException("Failed to clone git repository: ${url} on branch ${branch}. Make sure git is installed, the URL is accessible, and the branch exists.", cloneEx)
                }
            } else {
                // No branch specified, try main first, then master
                try {
                    println "*** git: ${url} (trying branch: main)"
                    project.exec {
                        workingDir modulesDir
                        commandLine 'git', 'clone', '-b', 'main', url, moduleName
                    }
                    usedBranch = 'main'
                    cloneSuccessful = true
                } catch (Exception mainEx) {
                    project.logger.info("Branch 'main' not found, trying 'master'...")
                    try {
                        println "*** git: ${url} (trying branch: master)"
                        project.exec {
                            workingDir modulesDir
                            commandLine 'git', 'clone', '-b', 'master', url, moduleName
                        }
                        usedBranch = 'master'
                        cloneSuccessful = true
                    } catch (Exception masterEx) {
                        // Try without specifying branch (let git choose default)
                        project.logger.info("Branch 'master' not found, trying default branch...")
                        try {
                            println "*** git: ${url} (using default branch)"
                            project.exec {
                                workingDir modulesDir
                                commandLine 'git', 'clone', url, moduleName
                            }
                            usedBranch = 'default'
                            cloneSuccessful = true
                        } catch (Exception defaultEx) {
                            project.logger.error("Failed to clone git module: ${url}", defaultEx)
                            throw new RuntimeException("Failed to clone git repository: ${url}. Make sure git is installed and the URL is accessible.", defaultEx)
                        }
                    }
                }
            }
            
            if (cloneSuccessful) {
                println "*** cloned to modules/${moduleName} (branch: ${usedBranch})"
                project.logger.info("Git module cloned successfully: ${url} -> modules/${moduleName}")
            }
            
        } catch (Exception e) {
            project.logger.error("Failed to apply git module: ${gitModule}", e)
            throw e
        }
    }
    
    /**
     * Extract module name from git URL
     * Examples:
     *   https://github.com/user/repo.git -> repo
     *   git@github.com:user/repo.git -> repo
     *   https://github.com/user/my-module -> my-module
     */
    private static String extractModuleName(String url) {
        // Remove trailing .git if present
        def cleanUrl = url.endsWith('.git') ? url.substring(0, url.length() - 4) : url
        
        // Extract last part of the path
        def lastSlash = cleanUrl.lastIndexOf('/')
        if (lastSlash == -1) {
            lastSlash = cleanUrl.lastIndexOf(':')
        }
        
        if (lastSlash != -1 && lastSlash < cleanUrl.length() - 1) {
            return cleanUrl.substring(lastSlash + 1)
        }
        
        // Fallback: use the whole URL (sanitized)
        return cleanUrl.replaceAll('[^a-zA-Z0-9_-]', '_')
    }

    /**
     * Apply an artifact-based module
     * Format: group:artifact:version
     */
    private static void applyArtifactModule(Project project, String artifact) {
        println "*** artifact: ${artifact}"
        
        // Add to artifacts.list.COMPILATION.gradle file
        def artifactsFile = project.file('artifacts.list.COMPILATION.gradle')
        if (!artifactsFile.exists()) {
            project.logger.warn("artifacts.list.COMPILATION.gradle not found")
            return
        }
        
        def content = artifactsFile.text
        
        // Check if artifact already exists
        if (content.contains("'${artifact}'")) {
            project.logger.info("Artifact ${artifact} already exists in artifacts.list.COMPILATION.gradle")
            println "*** (already in artifacts list)"
            return
        }
        
        // Find the closing bracket of the list
        def lastBracket = content.lastIndexOf(']')
        if (lastBracket == -1) {
            project.logger.error("Invalid format in artifacts.list.COMPILATION.gradle")
            return
        }
        
        // Check if Template Dependencies section exists
        boolean hasTemplateSection = content.contains('// Template Dependencies')
        
        // Get content before and after the bracket
        def beforeBracket = content.substring(0, lastBracket).trim()
        def afterBracket = content.substring(lastBracket)
        
        // Remove trailing comma if exists (to re-add it properly)
        if (beforeBracket.endsWith(',')) {
            beforeBracket = beforeBracket.substring(0, beforeBracket.length() - 1).trim()
        }
        
        StringBuilder newContent = new StringBuilder(beforeBracket)
        
        // Always add comma after the last entry
        newContent.append(',')
        
        if (!hasTemplateSection) {
            // Add Template Dependencies section header before the artifact
            newContent.append("\n\n  // Template Dependencies")
        }
        
        // Add the artifact
        newContent.append("\n  '${artifact}'")
        newContent.append('\n')
        newContent.append(afterBracket)
        
        artifactsFile.text = newContent.toString()
        
        project.logger.info("Added artifact ${artifact} to artifacts.list.COMPILATION.gradle")
    }
}
