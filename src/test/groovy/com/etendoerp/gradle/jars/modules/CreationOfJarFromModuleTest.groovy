package com.etendoerp.gradle.jars.modules

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Issue
import spock.lang.Shared
import spock.lang.TempDir

class CreationOfJarFromModuleTest extends ModuleToJarSpecificationTest{

    @TempDir @Shared File testProjectDir

    @Override
    File getProjectDir() {
        testProjectDir
    }

    @Issue("ERP-588")
    def "Creation of a JAR from a module"() {
        given: "A Etendo project with the generated entities"
        def entitiesResult = runTask(":generate.entities") as BuildResult
        entitiesResult.task(":generate.entities").outcome == TaskOutcome.SUCCESS

        and: "A module to create its JAR version"
        def module = "com.test.dummy0"

        when: "The jar task is ran in the module subproject"
        def jarResult = runTask(":modules:$module:jar", "-Ppkg=$module") as BuildResult

        then: "The task will complete successfully."
        jarResult.task(":modules:$module:jar").outcome == TaskOutcome.SUCCESS

        and: "The jar file will be created in the /build/libs of the module folder."
        def jarFile = new File("${testProjectDir.absolutePath}/modules/$module/build/libs/$module-1.0.0.jar")
        assert jarFile.exists()

        and: "The jar file will contain all the files located in the 'src' directory of the module, excluding the java files"
        hasAllSrcFiles(module, jarFile)

        and: "The jar file will contain all the files of the module in the 'META-INF/etendo/modules/#module' directory, excluding 'src'"
        hasAllFilesInMetaInf(module, jarFile)

    }

    void hasAllSrcFiles(String moduleName, File jarFile) {
        def filesFromModule = getFilesFromModule([module: moduleName,locationDir: "src"])
        def filesFromJar = getFilesFromJar([jarFile: jarFile, pathToIgnore:  "META-INF"])

        filesFromModule.each {
            def file = it

            // The '.java' files of a module will be mapped with the '.class' files of the Jar
            if (file.endsWith(".java")) {
                file = file.replace(".java",".class")
            }

            assert filesFromJar.contains(file)
        }
        assert true
    }

    void hasAllFilesInMetaInf(String moduleName, File jarFile) {
        def pathToIgnore = "${testProjectDir.absolutePath}/modules/$moduleName/src/"
        def filesFromModule = getFilesFromModule([module: moduleName, pathToIgnore: pathToIgnore])

        def pathToSearch = "META-INF/etendo/modules/$moduleName/"
        def filesFromJar   = getFilesFromJar([jarFile: jarFile, pathToSearch: pathToSearch])

        filesFromModule.each {
            def moduleFile = "${pathToSearch}$it"
            assert filesFromJar.contains(moduleFile)
        }

        assert true
    }

}
