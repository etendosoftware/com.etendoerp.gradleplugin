package com.etendoerp.ui

import org.gradle.api.Project
import groovy.json.JsonSlurper
import com.github.gradle.node.npm.task.NpmTask
import com.github.gradle.node.NodeExtension
import com.github.gradle.node.task.NodeTask
import com.github.gradle.node.exec.NodeExecConfiguration
import com.github.gradle.node.variant.VariantComputer

class NodeTasksLoader {

    static final String UI_INSTALL_PATH = 'build/ui'
    static final String PACKAGE_NAME = '@etendosoftware/mainui-cli'
    static final String PID_FILE = 'build/ui/.pid'
    static final String LOG_FILE = 'build/ui/server.log'

    /**
     * Configure Node.js plugin with default settings
     */
    static void configureNodePlugin(Project project) {
        project.extensions.configure(NodeExtension) { NodeExtension node ->
            // Download Node.js automatically
            node.download.set(true)
            
            // Node.js version to use
            node.version.set("20.11.0")
            
            // Directory where Node.js will be installed
            node.workDir.set(project.layout.buildDirectory.dir(".nodejs"))
            
            // Directory for npm cache
            node.npmWorkDir.set(project.layout.buildDirectory.dir(".npm"))
            
            // Use the project's UI package directory
            node.nodeProjectDir.set(project.layout.buildDirectory.dir("ui/package"))
        }
    }

    /**
     * Resolve Etendo Classic URLs from gradle.properties or infer from context.name
     * Searches for properties in this order:
     * 1. etendo.classic.url / etendo.classic.host (gradle property)
     * 2. ETENDO_CLASSIC_URL / ETENDO_CLASSIC_HOST (gradle property)
     * 3. ETENDO_CLASSIC_URL / ETENDO_CLASSIC_HOST (environment variable)
     * 4. Infer from context.name
     */
    static Map<String, String> resolveEtendoUrls(Project project) {
        // Try to find etendo.classic.url (priority order)
        String etendoUrl = project.findProperty('etendo.classic.url') ?: 
                          project.findProperty('ETENDO_CLASSIC_URL') ?:
                          System.getenv('ETENDO_CLASSIC_URL')
        
        // Try to find etendo.classic.host (priority order)
        String etendoHost = project.findProperty('etendo.classic.host') ?: 
                           project.findProperty('ETENDO_CLASSIC_HOST') ?:
                           System.getenv('ETENDO_CLASSIC_HOST')
        
        // If etendo.classic.url is not explicitly defined, infer from context.name
        if (!etendoUrl) {
            String contextName = project.findProperty('context.name') ?: 'etendo'
            etendoUrl = "http://localhost:8080/${contextName}"
        }
        
        // If etendo.classic.host is not defined, use the same as etendoUrl
        if (!etendoHost) {
            etendoHost = etendoUrl
        }
        
        return [
            url: etendoUrl,
            host: etendoHost
        ]
    }

    static void load(Project project) {
        // Configure Node.js plugin with defaults
        configureNodePlugin(project)
        
        // Register ui task (foreground) - uses NodeTask
        project.tasks.register('ui') {
            group = 'ui'
            description = 'Start the Etendo UI from GitHub Packages (foreground)'
            dependsOn 'downloadAndExtractUI', 'startUINodeTask'
        }
        
        // Register the actual NodeTask for starting UI
        project.tasks.register('startUINodeTask', NodeTask) {
            group = 'ui'
            description = 'Execute the UI server using NodeTask'
            dependsOn 'downloadAndExtractUI'
            
            // Configure the script path
            File packageDir = new File(project.file(UI_INSTALL_PATH), 'package')
            File cliJs = new File(packageDir, 'cli.js')
            script.set(cliJs)
            
            // Set working directory
            workingDir.set(packageDir)
            
            // Configure environment variables
            def etendoUrls = resolveEtendoUrls(project)
            environment.set([
                'PORT': '3000',
                'NODE_ENV': 'production',
                'HOSTNAME': '0.0.0.0',
                'NEXT_TELEMETRY_DISABLED': '1',
                'ETENDO_CLASSIC_URL': etendoUrls.url,
                'ETENDO_CLASSIC_HOST': etendoUrls.host,
            ])
            
            doFirst {
                if (!cliJs.exists()) {
                    throw new RuntimeException("UI package not found at ${cliJs.absolutePath}. Run downloadAndExtractUI first.")
                }
                
                project.logger.lifecycle("Starting UI server...")
                project.logger.lifecycle("Press Ctrl+C to stop the server")
                project.logger.lifecycle("")
            }
        }

        // Register ui.start task (background) - uses ProcessBuilder
        project.tasks.register('ui.start') {
            group = 'ui'
            description = 'Start the Etendo UI server in background'
            dependsOn 'downloadAndExtractUI'
            doLast {
                startUIServerBackgroundWithNode(project)
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
        
        // Register download task
        project.tasks.register('downloadAndExtractUI') {
            group = 'ui'
            description = 'Download and extract the UI package'
            doLast {
                downloadAndExtractUI(project)
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

        // Get GitHub credentials
        String githubToken = project.findProperty('githubToken') ?: ""
        String githubUser = project.findProperty('githubUser') ?: ""
        
        if (githubToken.isEmpty()) {
            throw new RuntimeException("Property 'githubToken' not found in gradle.properties")
        }
        
        if (githubUser.isEmpty()) {
            throw new RuntimeException("Property 'githubUser' not found in gradle.properties. Add your GitHub username in lowercase.")
        }

        // Create install directory
        installDir.mkdirs()

        // Create .npmrc in install directory for authentication
        File npmrc = new File(installDir, '.npmrc')
        npmrc.text = """@etendosoftware:registry=https://npm.pkg.github.com
//npm.pkg.github.com/:_authToken=${githubToken}
//npm.pkg.github.com/:username=${githubUser.toLowerCase()}
//npm.pkg.github.com/:always-auth=true
"""

        project.logger.lifecycle("Authenticating as: ${githubUser.toLowerCase()}")
        project.logger.lifecycle(".npmrc created at: ${npmrc.absolutePath}")
        project.logger.lifecycle("Fetching package tarball...")

        // Create temp directory for download
        File tempDir = new File(installDir, '.temp')
        tempDir.mkdirs()

        // Download package using npm pack with explicit userconfig
        def packResult = project.exec {
            workingDir installDir
            environment 'NPM_CONFIG_USERCONFIG', npmrc.absolutePath
            commandLine 'npm', 'pack', PACKAGE_NAME, '--pack-destination', tempDir.absolutePath, '--userconfig', npmrc.absolutePath
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
     * Start the UI server in background using Node.js plugin
     */
    static void startUIServerBackgroundWithNode(Project project) {
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

        // Get Node executable from plugin
        def nodeExtension = project.extensions.getByType(NodeExtension)
        def variantComputer = new VariantComputer()
        def nodeDirProvider = nodeExtension.resolvedNodeDir
        def nodeBinDirProvider = variantComputer.computeNodeBinDir(nodeDirProvider, nodeExtension.resolvedPlatform)
        def nodeBinDir = nodeBinDirProvider.get().asFile
        def nodeExec = new File(nodeBinDir, nodeExtension.resolvedPlatform.get().isWindows() ? "node.exe" : "node")

        // Ensure Node is downloaded
        if (!nodeExec.exists()) {
            project.logger.lifecycle("Downloading Node.js...")
            def setupTask = project.tasks.findByName('nodeSetup')
            if (setupTask) {
                setupTask.actions.each { it.execute(setupTask) }
            }
        }

        // Start process in background
        ProcessBuilder processBuilder = new ProcessBuilder(nodeExec.absolutePath, cliJs.absolutePath)
        processBuilder.directory(packageDir)
        
        Map<String, String> env = processBuilder.environment()
        env.put("PORT", "3000")
        env.put("NODE_ENV", "production")
        env.put("HOSTNAME", "0.0.0.0")
        env.put("NEXT_TELEMETRY_DISABLED", "1")
        
        // Etendo Classic backend URLs
        def etendoUrls = resolveEtendoUrls(project)
        env.put("ETENDO_CLASSIC_URL", etendoUrls.url)
        env.put("ETENDO_CLASSIC_HOST", etendoUrls.host)

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
}