package com.etendoerp.autoconfig

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification
import java.io.ByteArrayOutputStream
import java.io.PrintStream

class AutoConfigTaskTest extends Specification {

    Project project
    AutoConfigTask task
    ServerSocket serverSocket
    ByteArrayOutputStream outputBuffer
    PrintStream originalOut

    def setup() {
        project = ProjectBuilder.builder().build()
        task = project.tasks.create("autoConfig", AutoConfigTask)
        // Clear configurators before each test to ensure isolation
        AutoConfigTask.configurators.clear()
        outputBuffer = new ByteArrayOutputStream()
        originalOut = System.out
        System.setOut(new PrintStream(outputBuffer))
    }

    def cleanup() {
        if (serverSocket && !serverSocket.closed) {
            serverSocket.close()
        }
        if (originalOut) {
            System.setOut(originalOut)
        }
    }

    private String getOutput() {
        return outputBuffer.toString()
    }

    def "execute with listMode should list configurators"() {
        given:
        task.listMode = true
        project.extensions.extraProperties.set("tomcat.port", "54321")
        // Register a dummy configurator
        AutoConfigTask.configurators.put("testConfig", new AutoConfigurator() {
            String getName() { "testConfig" }
            String getDescription() { "Test Configurator" }
            void configure(Project p) { }
        })

        when:
        task.execute()

        then:
        noExceptionThrown()
        output.contains("Available Auto-Configurators")
        output.contains("testConfig")
        output.contains("Test Configurator")
    }

    def "execute with listMode and no configurators should show empty message"() {
        given:
        task.listMode = true

        when:
        task.execute()

        then:
        noExceptionThrown()
        output.contains("No configurators registered")
    }

    def "execute without configurator name should print help and return"() {
        given:
        task.configuratorName = null

        when:
        task.execute()

        then:
        noExceptionThrown()
        output.contains("No configurator specified")
        output.contains("--configurator=all")
        output.contains("--list")
    }

    def "execute should fail if Tomcat is not running"() {
        given:
        task.configuratorName = "all"
        // Use a port that is likely not in use
        project.extensions.extraProperties.set("tomcat.port", "54321") 

        when:
        task.execute()

        then:
        GradleException e = thrown()
        e.message.contains("Tomcat is not running")
    }

    def "execute should run specific configurator when Tomcat is running"() {
        given:
        // Start a fake server to simulate Tomcat
        serverSocket = new ServerSocket(0)
        int port = serverSocket.getLocalPort()
        project.extensions.extraProperties.set("tomcat.port", port.toString())

        task.configuratorName = "mock"
        boolean executed = false

        AutoConfigTask.configurators.put("mock", new AutoConfigurator() {
            String getName() { "mock" }
            String getDescription() { "Mock" }
            void configure(Project p) { executed = true }
        })

        when:
        task.execute()

        then:
        executed
        output.contains("Tomcat is running")
    }

     def "execute all should run all configurators"() {
        given:
        serverSocket = new ServerSocket(0)
        int port = serverSocket.getLocalPort()
        project.extensions.extraProperties.set("tomcat.port", port.toString())

        task.configuratorName = "all"
        int counter = 0

        AutoConfigTask.configurators.put("c1", new AutoConfigurator() {
            String getName() { "c1" }
            String getDescription() { "" }
            void configure(Project p) { counter++ }
        })
        AutoConfigTask.configurators.put("c2", new AutoConfigurator() {
            String getName() { "c2" }
            String getDescription() { "" }
            void configure(Project p) { counter++ }
        })

        when:
        task.execute()

        then:
        counter == 2
    }

    def "execute all should print message when no configurators registered"() {
        given:
        serverSocket = new ServerSocket(0)
        int port = serverSocket.getLocalPort()
        project.extensions.extraProperties.set("tomcat.port", port.toString())

        task.configuratorName = "all"

        when:
        task.execute()

        then:
        noExceptionThrown()
        output.contains("No configurators registered to run")
    }
    
    def "execute should fail if specified configurator does not exist"() {
         given:
        serverSocket = new ServerSocket(0)
        int port = serverSocket.getLocalPort()
        project.extensions.extraProperties.set("tomcat.port", port.toString())

        task.configuratorName = "nonExistent"

        when:
        task.execute()

        then:
         GradleException e = thrown()
         e.message.contains("Configurator 'nonExistent' not found")
    }
}
