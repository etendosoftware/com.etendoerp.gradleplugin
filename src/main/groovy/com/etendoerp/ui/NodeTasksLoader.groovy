package com.etendoerp.ui

import org.gradle.api.Project
import groovy.json.JsonSlurper

class NodeTasksLoader {

    static final String UI_INSTALL_PATH = 'build/ui'
    static final String PACKAGE_NAME = '@etendosoftware/mainui-cli'
    static final String PID_FILE = 'build/ui/.pid'
    static final String LOG_FILE = 'build/ui/server.log'

    static void load(Project project) {
        // Register ui task (foreground)
        project.tasks.register('ui') {
            group = 'ui'
            description = 'Start the Etendo UI from GitHub Packages (foreground)'
            doLast {
                downloadAndExtractUI(project)
                startUIServer(project, false)
            }
        }

        // Register ui.start task (background)
        project.tasks.register('ui.start') {
            group = 'ui'
            description = 'Start the Etendo UI server in background'
            doLast {
                downloadAndExtractUI(project)
                startUIServerBackground(project)
            }
        }

        // Register ui.stop task
        project.tasks.register('ui.stop') {
            group = 'ui'
            description = 'Stop the Etendo UI server (foreground or background)'
            doLast {
                stopUIServer(project)
            }
        }
    }

    /**
     * Download and extract the UI package as a tarball
     * This avoids npm/pnpm dependency resolution issues
     */
    static void downloadAndExtractUI(Project project) {
        File installDir = project.file(UI_INSTALL_PATH)
        File packageDir = new File(installDir, 'package')

        // Check if already extracted
        if (packageDir.exists() && new File(packageDir, 'cli.js').exists()) {
            project.logger.lifecycle("UI package already installed in ${UI_INSTALL_PATH}")
            return
        }

        project.logger.lifecycle("Downloading UI package from GitHub Packages...")

        // Get GitHub token
        String githubToken = project.findProperty('githubToken') ?: ""
        if (githubToken.isEmpty()) {
            throw new RuntimeException("Property 'githubToken' not found in gradle.properties")
        }

        // Create temp directory for npm operations
        File tempDir = new File(installDir, '.temp')
        tempDir.mkdirs()

        // Create .npmrc for authentication
        File npmrc = new File(tempDir, '.npmrc')
        npmrc.text = """@etendosoftware:registry=https://npm.pkg.github.com
//npm.pkg.github.com/:_authToken=${githubToken}
"""

        // Download package using npm pack
        project.logger.lifecycle("Fetching package tarball...")
        def packResult = project.exec {
            workingDir tempDir
            commandLine 'npm', 'pack', PACKAGE_NAME, '--registry=https://npm.pkg.github.com'
            ignoreExitValue true
        }

        if (packResult.exitValue != 0) {
            throw new RuntimeException("Failed to download UI package. Check your githubToken permissions.")
        }

        // Find the downloaded tarball
        File tarball = tempDir.listFiles().find { it.name.endsWith('.tgz') }
        if (!tarball) {
            throw new RuntimeException("Tarball not found after npm pack")
        }

        project.logger.lifecycle("Extracting package...")
        
        // Clean package directory if exists
        if (packageDir.exists()) {
            packageDir.deleteDir()
        }
        packageDir.mkdirs()

        // Extract tarball using tar command
        project.exec {
            workingDir installDir
            commandLine 'tar', '-xzf', tarball.absolutePath, '-C', packageDir.absolutePath, '--strip-components=1'
        }

        // Cleanup temp directory
        tempDir.deleteDir()

        project.logger.lifecycle("UI package ready at ${packageDir.absolutePath}")
    }

    /**
     * Start the UI server in background
     */
    static void startUIServerBackground(Project project) {
        File pidFile = project.file(PID_FILE)
        
        // Check if already running
        if (pidFile.exists()) {
            def existingPid = pidFile.text.trim()
            if (isProcessRunning(existingPid)) {
                project.logger.lifecycle("UI server is already running with PID ${existingPid}")
                project.logger.lifecycle("Use './gradlew ui.stop' to stop it first")
                return
            } else {
                // Stale PID file, clean it up
                pidFile.delete()
            }
        }

        File packageDir = new File(project.file(UI_INSTALL_PATH), 'package')
        File cliJs = new File(packageDir, 'cli.js')
        File logFile = project.file(LOG_FILE)

        if (!cliJs.exists()) {
            throw new RuntimeException("UI package not found. Run the task again to download.")
        }

        project.logger.lifecycle("Starting UI server in background...")
        project.logger.lifecycle("Logs will be written to: ${logFile.absolutePath}")

        // Prepare log file
        logFile.parentFile.mkdirs()
        if (logFile.exists()) {
            logFile.delete()
        }
        logFile.createNewFile()

        // Start process in background
        ProcessBuilder processBuilder = new ProcessBuilder('node', cliJs.absolutePath)
        processBuilder.directory(packageDir)
        
        Map<String, String> env = processBuilder.environment()
        env.put("PORT", "3000")
        env.put("NODE_ENV", "production")
        env.put("HOSTNAME", "0.0.0.0")
        env.put("NEXT_TELEMETRY_DISABLED", "1")

        // Redirect output to log file
        processBuilder.redirectOutput(ProcessBuilder.Redirect.appendTo(logFile))
        processBuilder.redirectError(ProcessBuilder.Redirect.appendTo(logFile))

        Process process = processBuilder.start()
        def pid = process.pid()

        // Save PID to file
        pidFile.text = pid.toString()

        // Give it a moment to start
        Thread.sleep(1000)

        if (process.isAlive()) {
            project.logger.lifecycle("✅ UI server started successfully")
            project.logger.lifecycle("   PID: ${pid}")
            project.logger.lifecycle("   URL: http://localhost:3000")
            project.logger.lifecycle("   Logs: ${logFile.absolutePath}")
            project.logger.lifecycle("")
            project.logger.lifecycle("To stop the server, run: ./gradlew ui.stop")
        } else {
            pidFile.delete()
            throw new RuntimeException("Failed to start UI server. Check logs at ${logFile.absolutePath}")
        }
    }

    /**
     * Stop the UI server (foreground or background)
     */
    static void stopUIServer(Project project) {
        File pidFile = project.file(PID_FILE)
        
        if (!pidFile.exists()) {
            project.logger.lifecycle("No running UI server found (PID file not found)")
            project.logger.lifecycle("Checking for running node processes on port 3000...")
            
            // Try to find and kill process using port 3000
            try {
                def result = new ByteArrayOutputStream()
                project.exec {
                    commandLine 'lsof', '-ti', ':3000'
                    standardOutput = result
                    ignoreExitValue = true
                }
                
                def pids = result.toString().trim()
                if (pids) {
                    pids.split('\n').each { pid ->
                        project.logger.lifecycle("Killing process ${pid} on port 3000...")
                        project.exec {
                            commandLine 'kill', '-15', pid
                            ignoreExitValue = true
                        }
                    }
                    Thread.sleep(2000)
                    project.logger.lifecycle("✅ Stopped UI server")
                } else {
                    project.logger.lifecycle("No process found on port 3000")
                }
            } catch (Exception e) {
                project.logger.warn("Could not check for processes on port 3000: ${e.message}")
            }
            return
        }

        def pid = pidFile.text.trim()
        project.logger.lifecycle("Stopping UI server (PID: ${pid})...")

        if (!isProcessRunning(pid)) {
            project.logger.lifecycle("Process ${pid} is not running")
            pidFile.delete()
            return
        }

        // Try graceful shutdown first (SIGTERM)
        try {
            if (System.getProperty('os.name').toLowerCase().contains('windows')) {
                project.exec {
                    commandLine 'taskkill', '/F', '/PID', pid
                    ignoreExitValue = true
                }
            } else {
                // Kill process group on Unix
                project.exec {
                    commandLine 'kill', '-15', pid
                    ignoreExitValue = true
                }
                
                // Wait for graceful shutdown
                Thread.sleep(2000)
                
                // Force kill if still running
                if (isProcessRunning(pid)) {
                    project.exec {
                        commandLine 'kill', '-9', pid
                        ignoreExitValue = true
                    }
                }
            }
            
            project.logger.lifecycle("✅ UI server stopped")
        } catch (Exception e) {
            project.logger.warn("Error stopping process: ${e.message}")
        } finally {
            pidFile.delete()
        }
    }

    /**
     * Check if a process is running
     */
    static boolean isProcessRunning(String pid) {
        try {
            if (System.getProperty('os.name').toLowerCase().contains('windows')) {
                def process = "tasklist /FI \"PID eq ${pid}\" /NH".execute()
                def output = process.text
                return output.contains(pid)
            } else {
                def process = "kill -0 ${pid}".execute()
                process.waitFor()
                return process.exitValue() == 0
            }
        } catch (Exception e) {
            return false
        }
    }

    /**
     * Start the UI server by executing the CLI directly (foreground)
     */
    static void startUIServer(Project project, boolean foreground = true) {
        File packageDir = new File(project.file(UI_INSTALL_PATH), 'package')
        File cliJs = new File(packageDir, 'cli.js')

        if (!cliJs.exists()) {
            throw new RuntimeException("UI package not found. Run the task again to download.")
        }

        project.logger.lifecycle("Starting UI server...")
        project.logger.lifecycle("Press Ctrl+C to stop the server")
        project.logger.lifecycle("")

        Process process = null
        Thread shutdownHook = null
        Thread outputThread = null
        Thread errorThread = null

        try {
            ProcessBuilder processBuilder = new ProcessBuilder('node', cliJs.absolutePath)
            processBuilder.directory(packageDir)
            
            Map<String, String> env = processBuilder.environment()
            env.put("PORT", "3000")
            env.put("NODE_ENV", "production")
            env.put("HOSTNAME", "0.0.0.0")
            env.put("NEXT_TELEMETRY_DISABLED", "1")

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