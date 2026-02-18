package com.etendoerp.ui

import com.github.gradle.node.NodeExtension
import com.github.gradle.node.task.NodeTask
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification
import spock.lang.Unroll

class NodeTasksLoaderTest extends Specification {

    Project project
    NodeExtension nodeExtension

    def setup() {
        project = ProjectBuilder.builder().build()
        project.pluginManager.apply('com.github.node-gradle.node')
        nodeExtension = project.extensions.getByType(NodeExtension)
    }

    def cleanup() {
        def buildDir = project.buildDir
        if (buildDir?.exists()) {
            def uiDir = new File(buildDir, 'ui')
            if (uiDir?.exists()) {
                uiDir.deleteDir()
            }
        }
    }

    // ==================== Configuration Tests ====================

    def "configureNodePlugin sets correct default values"() {
        when:
        NodeTasksLoader.configureNodePlugin(project)

        then:
        nodeExtension.download.get() == true
        nodeExtension.version.get() == "20.11.0"
        nodeExtension.workDir.get().asFile.path.endsWith(".nodejs")
        nodeExtension.npmWorkDir.get().asFile.path.endsWith(".npm")
        nodeExtension.nodeProjectDir.get().asFile.path.endsWith("ui${File.separator}package")
    }

    def "configureNodePlugin can be called multiple times"() {
        when:
        NodeTasksLoader.configureNodePlugin(project)
        NodeTasksLoader.configureNodePlugin(project)

        then:
        noExceptionThrown()
        nodeExtension.version.get() == "20.11.0"
    }

    // ==================== URL Resolution Tests ====================

    @Unroll
    def "resolveEtendoUrls with url=#url and host=#host"() {
        given:
        if (url) {
            project.ext.set('etendo.classic.url', url)
        }
        if (host) {
            project.ext.set('etendo.classic.host', host)
        }

        when:
        def result = NodeTasksLoader.resolveEtendoUrls(project)

        then:
        result.url == expectedUrl
        result.host == expectedHost

        where:
        url                           | host                          | expectedUrl                   | expectedHost
        'http://localhost:8080/app'   | 'http://localhost:8080/app'   | 'http://localhost:8080/app'   | 'http://localhost:8080/app'
        'http://localhost:8080/app'   | null                          | 'http://localhost:8080/app'   | 'http://localhost:8080/app'
        null                          | 'http://localhost:9090/host'  | 'http://localhost:8080/etendo'| 'http://localhost:9090/host'
    }

    def "resolveEtendoUrls infers from context name"() {
        given:
        project.ext.set('context.name', 'myapp')

        when:
        def result = NodeTasksLoader.resolveEtendoUrls(project)

        then:
        result.url == 'http://localhost:8080/myapp'
        result.host == 'http://localhost:8080/myapp'
    }

    def "resolveEtendoUrls uses default context"() {
        when:
        def result = NodeTasksLoader.resolveEtendoUrls(project)

        then:
        result.url == 'http://localhost:8080/etendo'
        result.host == 'http://localhost:8080/etendo'
    }

    def "resolveEtendoUrls prioritizes gradle properties"() {
        given:
        project.ext.set('etendo.classic.url', 'http://gradle:8080/app')

        when:
        def result = NodeTasksLoader.resolveEtendoUrls(project)

        then:
        result.url == 'http://gradle:8080/app'
    }

    @Unroll
    def "resolveEtendoUrls with context name #contextName"() {
        given:
        project.ext.set('context.name', contextName)

        when:
        def result = NodeTasksLoader.resolveEtendoUrls(project)

        then:
        result.url == expectedUrl

        where:
        contextName | expectedUrl
        'test'      | 'http://localhost:8080/test'
        'prod'      | 'http://localhost:8080/prod'
        'dev'       | 'http://localhost:8080/dev'
    }

    // ==================== Task Registration Tests ====================

    def "load registers all required tasks"() {
        when:
        NodeTasksLoader.load(project)

        then:
        project.tasks.findByName('ui') != null
        project.tasks.findByName('startUINodeTask') != null
        project.tasks.findByName('ui.start') != null
        project.tasks.findByName('ui.stop') != null
        project.tasks.findByName('downloadAndExtractUI') != null
    }

    def "load configures NodeExtension"() {
        when:
        NodeTasksLoader.load(project)

        then:
        nodeExtension.download.get() == true
        nodeExtension.version.get() == "20.11.0"
    }

    def "all registered tasks are of correct type"() {
        when:
        NodeTasksLoader.load(project)

        then:
        project.tasks.findByName('startUINodeTask') instanceof NodeTask
    }

    def "load configures task dependencies"() {
        when:
        NodeTasksLoader.load(project)

        then:
        def uiTask = project.tasks.findByName('ui')
        def startUINodeTask = project.tasks.findByName('startUINodeTask')
        def uiStartTask = project.tasks.findByName('ui.start')
        def downloadTask = project.tasks.findByName('downloadAndExtractUI')

        uiTask.dependsOn.find { it == downloadTask || it.toString().contains('downloadAndExtractUI') }
        uiTask.dependsOn.find { it == startUINodeTask || it.toString().contains('startUINodeTask') }
        startUINodeTask.dependsOn.find { it == downloadTask || it.toString().contains('downloadAndExtractUI') }
        uiStartTask.dependsOn.find { it == downloadTask || it.toString().contains('downloadAndExtractUI') }
    }

    def "load sets task groups and descriptions"() {
        when:
        NodeTasksLoader.load(project)

        then:
        def uiTask = project.tasks.findByName('ui')
        def startUINodeTask = project.tasks.findByName('startUINodeTask')
        def uiStartTask = project.tasks.findByName('ui.start')
        def uiStopTask = project.tasks.findByName('ui.stop')
        def downloadTask = project.tasks.findByName('downloadAndExtractUI')

        uiTask.group == 'ui'
        startUINodeTask.group == 'ui'
        uiStartTask.group == 'ui'
        uiStopTask.group == 'ui'
        downloadTask.group == 'ui'

        uiTask.description == 'Start the Etendo UI from GitHub Packages (foreground)'
        startUINodeTask.description == 'Execute the UI server using NodeTask'
        uiStartTask.description == 'Start the Etendo UI server in background'
        uiStopTask.description == 'Stop the Etendo UI server (foreground or background)'
        downloadTask.description == 'Download and extract the UI package'
    }

    def "startUINodeTask has correct script configuration"() {
        when:
        NodeTasksLoader.load(project)
        def startUINodeTask = project.tasks.findByName('startUINodeTask') as NodeTask

        then:
        startUINodeTask.script.isPresent()
        startUINodeTask.script.get().asFile.path.endsWith("cli.js")
    }

    def "startUINodeTask has correct working directory"() {
        when:
        NodeTasksLoader.load(project)
        def startUINodeTask = project.tasks.findByName('startUINodeTask') as NodeTask

        then:
        startUINodeTask.workingDir.isPresent()
        startUINodeTask.workingDir.get().asFile.path.endsWith("package")
    }

    def "startUINodeTask has correct environment variables"() {
        given:
        project.ext.set('etendo.classic.url', 'http://test:8080/app')
        project.ext.set('etendo.classic.host', 'http://test:8080/host')

        when:
        NodeTasksLoader.load(project)
        def startUINodeTask = project.tasks.findByName('startUINodeTask') as NodeTask

        then:
        def env = startUINodeTask.environment.get()
        env['PORT'] == '3000'
        env['NODE_ENV'] == 'production'
        env['HOSTNAME'] == '0.0.0.0'
        env['NEXT_TELEMETRY_DISABLED'] == '1'
        env['ETENDO_CLASSIC_URL'] == 'http://test:8080/app'
        env['ETENDO_CLASSIC_HOST'] == 'http://test:8080/host'
    }

    // ==================== Constants Tests ====================

    def "constants have correct values"() {
        expect:
        NodeTasksLoader.UI_INSTALL_PATH == 'build/ui'
        NodeTasksLoader.PACKAGE_NAME == '@etendosoftware/mainui-cli'
        NodeTasksLoader.PID_FILE == 'build/ui/.pid'
        NodeTasksLoader.LOG_FILE == 'build/ui/server.log'
    }

    def "constants are accessible and non-null"() {
        expect:
        NodeTasksLoader.UI_INSTALL_PATH != null
        NodeTasksLoader.PACKAGE_NAME != null
        NodeTasksLoader.PID_FILE != null
        NodeTasksLoader.LOG_FILE != null
    }

    // ==================== Process Management Tests ====================

    def "isProcessRunning returns false for invalid PID"() {
        expect:
        !NodeTasksLoader.isProcessRunning("999999999")
    }

    def "isProcessRunning returns false for empty PID"() {
        expect:
        !NodeTasksLoader.isProcessRunning("")
    }

    def "isProcessRunning handles null gracefully"() {
        when:
        def result = NodeTasksLoader.isProcessRunning(null)

        then:
        notThrown(NullPointerException)
        result == false
    }

    @Unroll
    def "isProcessRunning with invalid PID #pid"() {
        expect:
        !NodeTasksLoader.isProcessRunning(pid)

        where:
        pid << ["-1", "abc", "12345678901234567890"]
    }

    // ==================== Download and Extract Tests ====================

    def "downloadAndExtractUI skips if package exists"() {
        given:
        def installDir = new File(project.buildDir, 'ui')
        def packageDir = new File(installDir, 'package')
        def cliJs = new File(packageDir, 'cli.js')
        
        packageDir.mkdirs()
        cliJs.createNewFile()

        when:
        NodeTasksLoader.downloadAndExtractUI(project)

        then:
        cliJs.exists()
        
        cleanup:
        installDir.deleteDir()
    }

    def "downloadAndExtractUI creates necessary directories"() {
        given:
        def installDir = new File(project.buildDir, 'ui')
        def packageDir = new File(installDir, 'package')
        def cliJs = new File(packageDir, 'cli.js')
        
        packageDir.mkdirs()
        cliJs.createNewFile()

        when:
        NodeTasksLoader.downloadAndExtractUI(project)

        then:
        installDir.exists()
        packageDir.exists()
    }

    def "downloadAndExtractUI with existing package"() {
        given:
        def installDir = new File(project.buildDir, 'ui')
        def packageDir = new File(installDir, 'package')
        packageDir.mkdirs()
        
        def cliJs = new File(packageDir, 'cli.js')
        cliJs.text = "test content"

        when:
        NodeTasksLoader.downloadAndExtractUI(project)

        then:
        cliJs.exists()
        cliJs.text == "test content"
    }

    def "downloadAndExtractUI throws when githubToken missing"() {
        given:
        def installDir = new File(project.buildDir, 'ui')
        if (installDir.exists()) {
            installDir.deleteDir()
        }

        when:
        NodeTasksLoader.downloadAndExtractUI(project)

        then:
        def exception = thrown(RuntimeException)
        exception.message.contains("githubToken")
        
        cleanup:
        if (installDir.exists()) {
            installDir.deleteDir()
        }
    }

    def "downloadAndExtractUI throws when githubUser missing"() {
        given:
        project.ext.set('githubToken', 'test-token')
        def installDir = new File(project.buildDir, 'ui')
        if (installDir.exists()) {
            installDir.deleteDir()
        }

        when:
        NodeTasksLoader.downloadAndExtractUI(project)

        then:
        def exception = thrown(RuntimeException)
        exception.message.contains("githubUser")
        
        cleanup:
        if (installDir.exists()) {
            installDir.deleteDir()
        }
    }

    def "downloadAndExtractUI validates credentials order"() {
        given:
        def installDir = new File(project.buildDir, 'ui')
        if (installDir.exists()) {
            installDir.deleteDir()
        }

        when:
        NodeTasksLoader.downloadAndExtractUI(project)

        then:
        def exception = thrown(RuntimeException)
        exception.message.contains("githubToken")
        !exception.message.contains("githubUser")
    }

    // ==================== Stop Server Tests ====================

    def "stopUIServer handles missing PID file"() {
        given:
        def pidFile = new File(project.buildDir, 'ui/.pid')
        if (pidFile.exists()) {
            pidFile.delete()
        }

        when:
        NodeTasksLoader.stopUIServer(project)

        then:
        noExceptionThrown()
    }

    def "stopUIServer deletes PID file for non-running process"() {
        given:
        def pidFile = new File(project.buildDir, 'ui/.pid')
        pidFile.parentFile.mkdirs()
        pidFile.text = "999999999"

        when:
        NodeTasksLoader.stopUIServer(project)

        then:
        !pidFile.exists()
    }

    def "stopUIServer with valid PID file structure"() {
        given:
        def pidFile = new File(project.buildDir, 'ui/.pid')
        pidFile.parentFile.mkdirs()
        pidFile.text = "12345"

        expect:
        pidFile.exists()
        pidFile.text.trim() == "12345"

        cleanup:
        NodeTasksLoader.stopUIServer(project)
    }

    def "stopUIServer handles exceptions gracefully"() {
        given:
        def pidFile = new File(project.buildDir, 'ui/.pid')
        pidFile.parentFile.mkdirs()
        pidFile.text = "1"

        when:
        NodeTasksLoader.stopUIServer(project)

        then:
        noExceptionThrown()
    }

    // ==================== Start Server Tests ====================

    def "startUIServerBackgroundWithNode throws when package missing"() {
        given:
        def installDir = new File(project.buildDir, 'ui')
        if (installDir.exists()) {
            installDir.deleteDir()
        }

        when:
        NodeTasksLoader.startUIServerBackgroundWithNode(project)

        then:
        thrown(RuntimeException)
    }

    def "startUIServerBackgroundWithNode detects running server"() {
        given:
        def pidFile = new File(project.buildDir, 'ui/.pid')
        pidFile.parentFile.mkdirs()
        pidFile.text = String.valueOf(ProcessHandle.current().pid())

        when:
        NodeTasksLoader.startUIServerBackgroundWithNode(project)

        then:
        noExceptionThrown()
        pidFile.exists()

        cleanup:
        pidFile.delete()
    }

    def "startUIServerBackgroundWithNode cleans stale PID"() {
        given:
        def pidFile = new File(project.buildDir, 'ui/.pid')
        pidFile.parentFile.mkdirs()
        pidFile.text = "999999999"

        when:
        try {
            NodeTasksLoader.startUIServerBackgroundWithNode(project)
        } catch (RuntimeException e) {
            // Expected - package not found
        }

        then:
        !pidFile.exists() || pidFile.text != "999999999"

        cleanup:
        if (pidFile.exists()) {
            pidFile.delete()
        }
    }

    // ==================== Integration Tests ====================

    def "full task registration and configuration flow"() {
        when:
        NodeTasksLoader.load(project)

        then:
        nodeExtension.download.get() == true
        nodeExtension.version.get() == "20.11.0"

        project.tasks.findByName('ui') != null
        project.tasks.findByName('startUINodeTask') != null
        project.tasks.findByName('ui.start') != null
        project.tasks.findByName('ui.stop') != null
        project.tasks.findByName('downloadAndExtractUI') != null

        def uiTask = project.tasks.findByName('ui')
        uiTask.dependsOn.find { it.toString().contains('downloadAndExtractUI') }
    }

    def "URL resolution priority order"() {
        given:
        project.ext.set('context.name', 'context')
        project.ext.set('ETENDO_CLASSIC_URL', 'http://uppercase:8080/app')
        project.ext.set('etendo.classic.url', 'http://lowercase:8080/app')

        when:
        def result = NodeTasksLoader.resolveEtendoUrls(project)

        then:
        result.url == 'http://lowercase:8080/app'
    }

    def "environment variables configuration format"() {
        given:
        project.ext.set('etendo.classic.url', 'http://localhost:9090/custom')

        when:
        NodeTasksLoader.load(project)
        def startUINodeTask = project.tasks.findByName('startUINodeTask') as NodeTask
        def env = startUINodeTask.environment.get()

        then:
        env.size() >= 6
        env.containsKey('PORT')
        env.containsKey('NODE_ENV')
        env.containsKey('HOSTNAME')
        env.containsKey('NEXT_TELEMETRY_DISABLED')
        env.containsKey('ETENDO_CLASSIC_URL')
        env.containsKey('ETENDO_CLASSIC_HOST')
    }

    def "PID and LOG file paths are consistent"() {
        expect:
        NodeTasksLoader.PID_FILE.startsWith(NodeTasksLoader.UI_INSTALL_PATH)
        NodeTasksLoader.LOG_FILE.startsWith(NodeTasksLoader.UI_INSTALL_PATH)
    }

    def "package name follows npm convention"() {
        expect:
        NodeTasksLoader.PACKAGE_NAME.startsWith('@')
        NodeTasksLoader.PACKAGE_NAME.contains('/')
    }

    // ==================== Edge Cases ====================

    def "resolveEtendoUrls with null properties"() {
        when:
        def result = NodeTasksLoader.resolveEtendoUrls(project)

        then:
        result != null
        result.url != null
        result.host != null
    }

    def "multiple load calls don't create duplicates"() {
        when:
        NodeTasksLoader.load(project)
        def tasksCount = project.tasks.size()

        then:
        def exception = null
        try {
            NodeTasksLoader.load(project)
        } catch (Exception e) {
            exception = e
        }
        
        // Should either maintain same task count or throw duplicate task exception
        exception == null ? project.tasks.size() == tasksCount : exception.message.contains("already exists")
    }

    def "task names follow naming convention"() {
        when:
        NodeTasksLoader.load(project)

        then:
        project.tasks.findByName('ui').name == 'ui'
        project.tasks.findByName('ui.start').name == 'ui.start'
        project.tasks.findByName('ui.stop').name == 'ui.stop'
    }

    def "all UI tasks are in ui group"() {
        when:
        NodeTasksLoader.load(project)

        then:
        project.tasks.findByName('ui').group == 'ui'
        project.tasks.findByName('startUINodeTask').group == 'ui'
        project.tasks.findByName('ui.start').group == 'ui'
        project.tasks.findByName('ui.stop').group == 'ui'
        project.tasks.findByName('downloadAndExtractUI').group == 'ui'
    }
}
