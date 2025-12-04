package com.etendoerp.ui

import org.gradle.api.Project

class NodeTasksLoader {

    static final String UI_MODULE_PATH = 'modules/com.etendorx.workspace-ui'
    static final String UI_BUILD_PATH = 'build/ui'
    static final String UI_REPO_URL = 'https://github.com/etendosoftware/com.etendorx.workspace-ui.git'

    static void load(Project project) {
        // Determine the UI project directory
        File uiProjectDir = resolveUIProjectDirectory(project)

        // Configure node extension (plugin is applied in main build.gradle)
        project.afterEvaluate {
            if (project.extensions.findByName('node')) {
                project.extensions.configure("node") { node ->
                    node.version.set('20.10.0')
                    node.download.set(true)
                    node.nodeProjectDir.set(uiProjectDir)
                }
            }
        }

        // Register uiInstall task
        project.tasks.register('uiInstall') {
            group = 'ui'
            description = 'Install UI dependencies using pnpm'
            doLast {
                project.exec {
                    workingDir uiProjectDir
                    commandLine 'pnpm', 'install'
                }
            }
        }

        // Register uiBuild task
        project.tasks.register('uiBuild') {
            group = 'ui'
            description = 'Build the UI project'
            dependsOn 'uiInstall'
            doLast {
                project.exec {
                    workingDir uiProjectDir
                    commandLine 'pnpm', 'build'
                }
            }
        }

        // Register ui task
        project.tasks.register('ui') {
            group = 'ui'
            description = 'Start the UI development server'
            dependsOn 'uiBuild'
            doLast {
                startUIServer(project, uiProjectDir)
            }
        }
    }

    /**
     * Resolves the UI project directory with the following logic:
     * 1. If modules/com.etendorx.workspace-ui exists, use it
     * 2. Otherwise, check build/ui:
     *    - If it exists and is a git repo, pull latest changes
     *    - If it doesn't exist, clone the repository
     * @param project The Gradle project
     * @return The resolved UI project directory
     */
    static File resolveUIProjectDirectory(Project project) {
        File moduleDir = project.file(UI_MODULE_PATH)
        File buildDir = project.file(UI_BUILD_PATH)

        // Check if the module directory exists
        if (moduleDir.exists() && moduleDir.isDirectory()) {
            project.logger.info("Using UI module from: ${UI_MODULE_PATH}")
            return moduleDir
        }

        // Module doesn't exist, use build directory
        project.logger.info("UI module not found in ${UI_MODULE_PATH}, using build directory")

        if (buildDir.exists() && buildDir.isDirectory()) {
            // Check if it's a git repository
            File gitDir = new File(buildDir, '.git')
            if (gitDir.exists()) {
                project.logger.info("Updating UI repository in ${UI_BUILD_PATH}")
                updateGitRepository(project, buildDir)
            } else {
                project.logger.warn("Directory ${UI_BUILD_PATH} exists but is not a git repository. Removing and cloning fresh.")
                buildDir.deleteDir()
                cloneRepository(project, buildDir)
            }
        } else {
            // Clone the repository
            project.logger.info("Cloning UI repository to ${UI_BUILD_PATH}")
            cloneRepository(project, buildDir)
        }

        return buildDir
    }

    /**
     * Clones the UI repository to the specified directory
     */
    static void cloneRepository(Project project, File targetDir) {
        try {
            def result = project.exec {
                commandLine 'git', 'clone', UI_REPO_URL, targetDir.absolutePath
                ignoreExitValue = true
            }
            if (result.exitValue != 0) {
                throw new RuntimeException("Failed to clone UI repository from ${UI_REPO_URL}")
            }
            project.logger.lifecycle("Successfully cloned UI repository to ${targetDir.absolutePath}")
        } catch (Exception e) {
            project.logger.error("Error cloning repository: ${e.message}")
            throw e
        }
    }

    /**
     * Updates the git repository by pulling the latest changes
     */
    static void updateGitRepository(Project project, File repoDir) {
        try {
            // Fetch latest changes
            def fetchResult = project.exec {
                workingDir repoDir
                commandLine 'git', 'fetch', 'origin'
                ignoreExitValue = true
            }

            if (fetchResult.exitValue != 0) {
                project.logger.warn("Failed to fetch updates from origin. Using existing version.")
                return
            }

            // Check if there are updates available
            def revParseLocal = new ByteArrayOutputStream()
            project.exec {
                workingDir repoDir
                commandLine 'git', 'rev-parse', 'HEAD'
                standardOutput = revParseLocal
            }

            def revParseRemote = new ByteArrayOutputStream()
            project.exec {
                workingDir repoDir
                commandLine 'git', 'rev-parse', 'origin/main'
                standardOutput = revParseRemote
                ignoreExitValue = true
            }

            def localCommit = revParseLocal.toString().trim()
            def remoteCommit = revParseRemote.toString().trim()

            if (localCommit != remoteCommit && !remoteCommit.isEmpty()) {
                project.logger.lifecycle("Updates available. Pulling latest changes...")
                def pullResult = project.exec {
                    workingDir repoDir
                    commandLine 'git', 'pull', 'origin', 'main'
                    ignoreExitValue = true
                }

                if (pullResult.exitValue == 0) {
                    project.logger.lifecycle("Successfully updated UI repository")
                } else {
                    project.logger.warn("Failed to pull updates. Using existing version.")
                }
            } else {
                project.logger.info("UI repository is already up to date")
            }
        } catch (Exception e) {
            project.logger.warn("Error updating repository: ${e.message}. Using existing version.")
        }
    }

    /**
     * Starts the UI development server with proper process management
     * Handles Ctrl+C gracefully by killing the child process
     */
    static void startUIServer(Project project, File workingDir) {
        project.logger.lifecycle("Starting UI development server...")
        project.logger.lifecycle("Press Ctrl+C to stop the server")
        project.logger.lifecycle("")

        Process process = null
        Thread shutdownHook = null
        Thread outputThread = null
        Thread errorThread = null

        try {
            // Build the process
            ProcessBuilder processBuilder = new ProcessBuilder('pnpm', 'start')
            processBuilder.directory(workingDir)

            // Start the process
            process = processBuilder.start()
            final Process finalProcess = process

            // Create threads to read stdout and stderr in real-time
            outputThread = new Thread({
                try {
                    process.inputStream.eachLine { line ->
                        println line
                    }
                } catch (IOException ignored) {
                    // Stream closed
                }
            })
            outputThread.setDaemon(true)
            outputThread.start()

            errorThread = new Thread({
                try {
                    process.errorStream.eachLine { line ->
                        System.err.println line
                    }
                } catch (IOException ignored) {
                    // Stream closed
                }
            })
            errorThread.setDaemon(true)
            errorThread.start()

            // Add shutdown hook to kill process on Ctrl+C
            shutdownHook = new Thread({
                project.logger.lifecycle("\nShutting down UI server...")
                try {
                    if (finalProcess?.isAlive()) {
                        // Kill the process tree (parent and children)
                        killProcessTree(finalProcess, project)
                        project.logger.lifecycle("UI server stopped successfully")
                    }
                } catch (Exception e) {
                    project.logger.error("Error stopping UI server: ${e.message}")
                }
            })
            Runtime.getRuntime().addShutdownHook(shutdownHook)

            // Wait for the process to complete
            int exitCode = process.waitFor()

            // Wait for output threads to finish reading
            outputThread?.join(1000)
            errorThread?.join(1000)

            // Remove shutdown hook if process completed normally
            try {
                Runtime.getRuntime().removeShutdownHook(shutdownHook)
            } catch (IllegalStateException ignored) {
                // Shutdown already in progress
            }

            if (exitCode != 0) {
                project.logger.error("")
                project.logger.error("UI server exited with code: ${exitCode}")
                project.logger.error("Check the output above for errors")
                project.logger.error("")
                throw new RuntimeException("UI server exited with code: ${exitCode}")
            }

        } catch (InterruptedException e) {
            project.logger.lifecycle("\nUI server interrupted")
            if (process?.isAlive()) {
                killProcessTree(process, project)
            }
            Thread.currentThread().interrupt()
        } catch (Exception e) {
            if (process?.isAlive()) {
                killProcessTree(process, project)
            }
            throw e
        }
    }

    /**
     * Kills a process and all its children
     */
    static void killProcessTree(Process process, Project project) {
        try {
            // Try to get the PID
            def pid = process.pid()

            // Kill process tree on Unix-like systems
            if (System.getProperty('os.name').toLowerCase().contains('windows')) {
                // Windows: taskkill /F /T /PID
                new ProcessBuilder('taskkill', '/F', '/T', '/PID', pid.toString())
                    .inheritIO()
                    .start()
                    .waitFor()
            } else {
                // Unix/Linux/Mac: kill process group
                new ProcessBuilder('pkill', '-TERM', '-P', pid.toString())
                    .inheritIO()
                    .start()
                    .waitFor()

                // Wait a bit and force kill if still alive
                Thread.sleep(1000)
                if (process.isAlive()) {
                    new ProcessBuilder('pkill', '-KILL', '-P', pid.toString())
                        .inheritIO()
                        .start()
                        .waitFor()
                }
            }

            // Finally, destroy the main process
            process.destroy()

            // Wait for termination with timeout
            if (!process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) {
                process.destroyForcibly()
            }

        } catch (Exception e) {
            project.logger.warn("Error killing process tree: ${e.message}")
            // Force kill as last resort
            process.destroyForcibly()
        }
    }
}