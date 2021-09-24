package com.etendoerp.gradle.jars.modules

import com.etendoerp.gradle.tests.EtendoSpecification
import org.gradle.api.tasks.bundling.Jar
import org.gradle.internal.impldep.org.apache.commons.io.FileUtils
import org.gradle.testkit.runner.BuildResult
import spock.lang.Issue
import spock.lang.Shared
import spock.lang.TempDir
import org.gradle.testkit.runner.TaskOutcome

class ModuleToJarTest extends EtendoSpecification {

    final static String ENVIRONMENTS_LOCATION = "src/test/resources/jars/environments"

    @TempDir @Shared File testProjectDir

    @Override
    File getProjectDir() {
        testProjectDir
    }

    def setup() {
        def baseDir = new File("${ENVIRONMENTS_LOCATION}/moduleToJarEnvironment")
        FileUtils.copyDirectory(baseDir, testProjectDir)
    }

    @Issue("ERP-")
    def "Creation of a JAR"() {
        given: "a module to create its JAR version"
            def module = "com.test.nontransactional"
        when: "The jar task is runned in the module subproject"
            def result = runTask(":modules:$module:jar", "-Ppkg=$module") as BuildResult

        then: "The task will complete successfully"
            result.task(":modules:$module:jar").outcome == TaskOutcome.SUCCESS
            def jarFile = new File("${testProjectDir.absolutePath}/modules/$module/build/libs/$module-1.0.0.jar")

            assert jarFile.exists()

    }
}
