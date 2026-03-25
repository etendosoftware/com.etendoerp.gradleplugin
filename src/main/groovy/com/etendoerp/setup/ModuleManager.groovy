package com.etendoerp.setup

import org.gradle.api.Project

/**
 * Central manager for module operations (artifacts and git repositories)
 * 
 * This class provides utilities to:
 * - List installed artifacts from artifacts.list.COMPILATION.gradle
 * - List installed git modules from modules/ directory
 * - Check if modules are installed
 * - Extract information from git repositories
 */
class ModuleManager {
    
    // ====== ARTIFACT MANAGEMENT ======
    
    /**
     * List all installed artifacts from artifacts.list.COMPILATION.gradle
     * @param project The Gradle project
     * @return List of artifact coordinates (group:artifact:version)
     */
    static List<String> listInstalledArtifacts(Project project) {
        def artifactsFile = project.file('artifacts.list.COMPILATION.gradle')
        
        if (!artifactsFile.exists()) {
            project.logger.warn("artifacts.list.COMPILATION.gradle not found")
            return []
        }
        
        def artifacts = []
        def content = artifactsFile.text
        
        // Parse artifact lines: '  'group:artifact:version''
        content.eachLine { line ->
            def trimmed = line.trim()
            if (trimmed.startsWith("'") && trimmed.contains(':')) {
                // Extract artifact coordinate from '  'group:artifact:version','
                def coordinate = trimmed.replaceAll("^'", '').replaceAll("'[,]*\$", '')
                artifacts << coordinate
            }
        }
        
        return artifacts.sort()
    }
    
    /**
     * Check if an artifact is installed
     * @param project The Gradle project
     * @param artifact Artifact coordinate in format group:artifact:version
     * @return true if the artifact exists in artifacts.list.COMPILATION.gradle
     */
    static boolean isArtifactInstalled(Project project, String artifact) {
        return listInstalledArtifacts(project).contains(artifact)
    }
    
    // ====== GIT MODULE MANAGEMENT ======
    
    /**
     * List all installed git modules from modules/ directory
     * @param project The Gradle project
     * @return List of GitModuleInfo objects
     */
    static List<GitModuleInfo> listInstalledGitModules(Project project) {
        def modulesDir = project.file('modules')
        
        if (!modulesDir.exists() || !modulesDir.isDirectory()) {
            return []
        }
        
        def gitModules = []
        
        modulesDir.listFiles().each { dir ->
            if (dir.isDirectory() && new File(dir, '.git').exists()) {
                def info = getGitModuleInfo(project, dir.name)
                if (info) {
                    gitModules << info
                }
            }
        }
        
        return gitModules.sort { it.name }
    }
    
    /**
     * Check if a git module is installed
     * @param project The Gradle project
     * @param moduleName Module name (directory name)
     * @return true if the directory modules/<moduleName> exists
     */
    static boolean isGitModuleInstalled(Project project, String moduleName) {
        def moduleDir = project.file("modules/${moduleName}")
        return moduleDir.exists() && moduleDir.isDirectory()
    }
    
    /**
     * Extract module name from git URL
     * Examples:
     *   https://github.com/user/repo.git -> repo
     *   git@github.com:user/repo.git -> repo
     *   https://github.com/user/my-module -> my-module
     * 
     * @param gitUrl Git repository URL
     * @return Module name
     */
    static String extractModuleName(String gitUrl) {
        // Remove trailing .git if present
        def cleanUrl = gitUrl.endsWith('.git') ? gitUrl.substring(0, gitUrl.length() - 4) : gitUrl
        
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
     * Get information about an installed git module
     * @param project The Gradle project
     * @param moduleName Module name (directory name)
     * @return GitModuleInfo object or null if not found
     */
    static GitModuleInfo getGitModuleInfo(Project project, String moduleName) {
        def moduleDir = project.file("modules/${moduleName}")
        
        if (!moduleDir.exists() || !new File(moduleDir, '.git').exists()) {
            return null
        }
        
        def info = new GitModuleInfo()
        info.name = moduleName
        info.path = "modules/${moduleName}"
        
        try {
            // Get current branch
            def branchResult = new ByteArrayOutputStream()
            project.exec {
                workingDir moduleDir
                commandLine 'git', 'rev-parse', '--abbrev-ref', 'HEAD'
                standardOutput = branchResult
                ignoreExitValue = true
            }
            info.currentBranch = branchResult.toString().trim()
            
            // Get remote URL
            def remoteResult = new ByteArrayOutputStream()
            project.exec {
                workingDir moduleDir
                commandLine 'git', 'remote', 'get-url', 'origin'
                standardOutput = remoteResult
                ignoreExitValue = true
            }
            info.remoteUrl = remoteResult.toString().trim()
            
        } catch (Exception e) {
            project.logger.warn("Failed to get git info for ${moduleName}: ${e.message}")
        }
        
        return info
    }
    
    // ====== INNER CLASS ======
    
    /**
     * Information about a git module
     */
    static class GitModuleInfo {
        String name           // Module name (directory name)
        String path           // Relative path (modules/xxx)
        String currentBranch  // Current branch (from git)
        String remoteUrl      // Remote URL (from git remote -v)
        
        @Override
        String toString() {
            return "${name} (${currentBranch}) - ${remoteUrl}"
        }
    }
}
