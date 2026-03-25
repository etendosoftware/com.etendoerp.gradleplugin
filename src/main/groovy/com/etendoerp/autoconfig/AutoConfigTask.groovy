package com.etendoerp.autoconfig

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option

class AutoConfigTask extends DefaultTask {

    // Registry of configurators (modules will register themselves here)
    static Map<String, AutoConfigurator> configurators = [:]

    private boolean listMode = false
    private String configuratorName

    @Option(option = "list", description = "List available configurators")
    void setListMode(boolean listMode) {
        this.listMode = listMode
    }

    @Option(option = "configurator", description = "Specific configurator to run")
    void setConfiguratorName(String configuratorName) {
        this.configuratorName = configuratorName
    }

    @TaskAction
    void execute() {
        if (listMode) {
            listConfigurators()
            return
        }

        if (!configuratorName) {
            println "\n[INFO] No configurator specified."
            println "       * Use --configurator=all to run all."
            println "       * Use --configurator=<name> to run a specific one (e.g., --configurator=copilot)."
            println "       * Use --list to see all available options.\n"
            return
        }

        // 1. ALWAYS validate Tomcat first for configuration actions
        validateTomcatRunning()

        // 2. Execute configurator(s)
        if (configuratorName.equalsIgnoreCase("all")) {
            runAllConfigurators()
        } else {
            runConfigurator(configuratorName)
        }
    }

    private void validateTomcatRunning() {
        def tomcatPort = project.findProperty("tomcat.port") ?: "8080"
        int port = 8080
        try {
            port = Integer.parseInt(tomcatPort.toString())
        } catch (NumberFormatException e) {
            project.logger.warn("Invalid tomcat.port '${tomcatPort}', defaulting to 8080")
        }

        try {
            new Socket("localhost", port).close()
            println "[OK] Tomcat is running on port ${port}"
        } catch (Exception e) {
            throw new GradleException("""
                |===========================================
                | ERROR: Tomcat is not running
                |===========================================
                | auto-config requires Etendo to be running on port ${port}.
                |
                | Start Tomcat first, or ensure the correct port is set with -Ptomcat.port=<port>.
                |
                | Then retry:
                |   ./gradlew setup.autoConfig
                |===========================================""".stripMargin())
        }
    }

    private void listConfigurators() {
        println """
        |===========================================
        | Available Auto-Configurators
        |===========================================
        """.stripMargin()

        if (configurators.isEmpty()) {
            println "  (No configurators registered)"
        } else {
            configurators.each { name, config ->
                println "  * ${name.padRight(20)} : ${config.getDescription()}"
            }
        }
        println ""
    }

    private void runConfigurator(String name) {
        def config = configurators[name]
        if (!config) {
            throw new GradleException("Configurator '${name}' not found. Use --list to see available options.")
        }
        println "\n>>> Running configurator: ${name}"
        config.configure(project)
        println "<<< Finished: ${name}\n"
    }

    private void runAllConfigurators() {
        if (configurators.isEmpty()) {
            println "No configurators registered to run."
            return
        }
        println "Running all registered configurators..."
        for (entry in configurators.entrySet()) {
            runConfigurator(entry.key)
        }
    }
}
