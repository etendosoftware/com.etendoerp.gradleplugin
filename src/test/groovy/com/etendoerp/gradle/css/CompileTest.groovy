package com.etendoerp.gradle.css

import com.etendoerp.gradle.tests.EtendoSpecification
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Issue
import spock.lang.TempDir
import spock.lang.Title

@Issue("EPL-218")
@Title("Running the compile sass task compiles the sass file to css file")
class CompileTest extends EtendoSpecification{

    @TempDir
    File testProjectDir

    @Override
    File getProjectDir() {
        return testProjectDir
    }

    def "Running the compile sass task"() {
        given: "The user with a custom module containing a sass file"
        File moduleWebLocation = new File(testProjectDir, "modules/com.test.custom/web")
        moduleWebLocation.mkdirs()

        File sassFile = new File(moduleWebLocation, "my-sass.scss")
        sassFile.createNewFile()

        sassFile <<
            """
            \$bgcolor: lightblue;

            body {
              background-color: \$bgcolor;
            }
            """

        when: "The users runs the cssCompile task"
        def cssCompileResult = runTask(":cssCompile")

        then: "The task will finish successfully"
        assert cssCompileResult.task(":cssCompile").outcome == TaskOutcome.SUCCESS

        and: "The css file will be created"
        File moduleWebLocationAfter = new File(testProjectDir, "modules/com.test.custom/web")
        File cssFile = new File(moduleWebLocationAfter, "my-sass.css")
        assert cssFile.exists()

        and: "The file content should be compiled"
        assert cssFile.text.contains("background-color: lightblue;")

    }

}
