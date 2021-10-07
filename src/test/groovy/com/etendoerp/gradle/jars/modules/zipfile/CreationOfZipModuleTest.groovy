package com.etendoerp.gradle.jars.modules.zipfile

import com.etendoerp.gradle.jars.modules.ModuleToJarSpecificationTest
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Shared
import spock.lang.TempDir
import spock.lang.Title

@Title("Creation of the zip version of a module")
class CreationOfZipModuleTest extends ModuleToJarSpecificationTest{

    @TempDir @Shared File testProjectDir

    @Override
    File getProjectDir() {
        testProjectDir
    }

    def "Creation of zip file"() {
        given: "A Etendo project with a module to be converted to ZIP "
        def module = "com.test.dummy0"

        when: "The Zip task is ran with the command line parameter '#module'"
        def zipResult = runTask(":generateModuleZip", "-Ppkg=$module") as BuildResult

        then: "The task will complete successfully."
        zipResult.task(":generateModuleZip").outcome == TaskOutcome.SUCCESS

        and: "The ZIP file will be created in the 'build/lib' directory of the root project"
        def zipFile = new File("${testProjectDir.absolutePath}/build/libs/${module}.zip")
        assert zipFile.exists()

        and: "The zip file should contain the same files of the module"
        containsAllFiles(module, zipFile)
    }

    void containsAllFiles(String module, File zipFile) {
        def moduleLocation = "${testProjectDir}/modules/$module"
        def filesInModule = getFilesFromLocation([location: moduleLocation])

        // A zip file and jar file have the same format
        def filesInZip   = getFilesFromJar([jarFile: zipFile])

        Set filesInModuleSet = filesInModule.flatten() as Set
        Set filesInZipSet    = filesInZip.collect({it.replaceFirst(module,"")}).flatten() as Set

        assert filesInModuleSet == filesInZipSet
    }

}
