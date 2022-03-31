package com.etendoerp.gradle.jars.modules.buildfile

import com.etendoerp.gradle.jars.modules.ModuleToJarSpecificationTest
import com.etendoerp.modules.ModulesConfigurationLoader
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.TempDir
import spock.lang.Title

@Title("Test to verify that the users contains the java plugin in a module with the 'build.gradle' file")
class BuildFileTest extends ModuleToJarSpecificationTest {
    @TempDir File testProjectDir

    @Override
    File getProjectDir() {
        testProjectDir
    }

    def "Creation of a 'build gradle' file "() {
        given: "A module where the build gradle file will be created"
        def module = "com.test.module0"

        when: "The users creates a empty build.gradle file"
        def moduleLocation = new File("${getProjectDir().absolutePath}/${BASE_MODULE}/${module}/build.gradle")
        if (!moduleLocation.getParentFile().exists()) {
            moduleLocation.getParentFile().mkdirs()
        }
        moduleLocation.createNewFile()
        assert moduleLocation.exists()

        and: "The users runs the compileJava task"
        def compilationResult = runTask(":${BASE_MODULE}:${module}:compileJava")

        then: "The task will NOT fail"
        compilationResult.task(":${BASE_MODULE}:${module}:compileJava").outcome == TaskOutcome.SUCCESS || TaskOutcome.NO_SOURCE
    }

}
