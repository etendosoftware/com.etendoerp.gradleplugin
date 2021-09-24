package com.etendoerp.gradle.jars.modules

import com.etendoerp.gradle.tests.EtendoSpecification
import org.gradle.api.tasks.bundling.Jar
import org.gradle.internal.impldep.org.apache.commons.io.FileUtils
import org.gradle.testkit.runner.BuildResult
import spock.lang.Issue
import spock.lang.Shared
import spock.lang.TempDir
import org.gradle.testkit.runner.TaskOutcome

import java.util.zip.ZipFile

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

    @Issue("ERP-588")
    def "Creation of a JAR from a module"() {
        given: "A module to create its JAR version"
        def module = "com.test.nontransactional"

        when: "The jar task is runned in the module subproject"
        def result = runTask(":modules:$module:jar", "-Ppkg=$module") as BuildResult

        then: "The task will complete successfully."
        result.task(":modules:$module:jar").outcome == TaskOutcome.SUCCESS

        and: "The jar file will be created in the /build/libs of the module folder."
        def jarFile = new File("${testProjectDir.absolutePath}/modules/$module/build/libs/$module-1.0.0.jar")
        assert jarFile.exists()

        and: "The jar file will contain all the class files."
        hasAllClass(module, jarFile)
    }

    String[] getJavaFilesFromModule(String module) {
        def javaFiles = []
        def moduleSrcLocation = new File("${testProjectDir.absolutePath}/modules/$module/src")
        moduleSrcLocation.eachFileRecurse {
            if (it.name.endsWith(".java")) {
                javaFiles.add(it.absolutePath.replace("${testProjectDir.absolutePath}/modules/$module/src/",""))
            }
        }
        return javaFiles
    }

    String[] getClassFilesFromJar(File jarFile) {
        def classFiles = []
        new ZipFile(jarFile).entries().each {
            if (it.name.endsWith(".class")) {
                classFiles.add(it.name)
            }
        }
        return classFiles
    }

    void hasAllClass(String moduleName, File jarFile) {
        def javaFiles = getJavaFilesFromModule(moduleName)
        def classFiles = getClassFilesFromJar(jarFile)

        javaFiles.each {
            def file = it.replace(".java",".class")
            assert classFiles.contains(file)
        }
        assert true
    }

}
