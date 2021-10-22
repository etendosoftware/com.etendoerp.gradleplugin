package com.etendoerp.gradle.jars.modules.buildfile

import com.etendoerp.gradle.jars.modules.ModuleToJarSpecificationTest
import com.etendoerp.modules.ModulesConfigurationLoader
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.TempDir

class BuildFileTest extends ModuleToJarSpecificationTest {
    @TempDir File testProjectDir

    @Override
    File getProjectDir() {
        testProjectDir
    }

    def "Creation of a 'build gradle' file in a module without the java plugin fails"() {
        given: "A module where the build gradle file will be created"
        def module = "com.test.module0"

        when: "The users creates a empty build.gradle file"
        def moduleLocation = new File("${getProjectDir().absolutePath}/${BASE_MODULE}/${module}/build.gradle")
        if (!moduleLocation.getParentFile().exists()) {
            moduleLocation.getParentFile().mkdirs()
        }
        moduleLocation.createNewFile()
        assert moduleLocation.exists()

        and: "The users runs the javaCompile task"
        def compilationResult = runTaskAndFail(":${BASE_MODULE}:${module}:javaCompile")

        then: "The task will fail and the output will show the error of missing the java plugin"
        compilationResult.output.contains("java.lang.IllegalArgumentException")
        compilationResult.output.contains(ModulesConfigurationLoader.ERROR_MISSING_PLUGIN)

    }

}
