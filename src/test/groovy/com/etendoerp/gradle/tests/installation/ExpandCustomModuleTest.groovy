package com.etendoerp.gradle.tests.installation

import com.etendoerp.gradle.tests.EtendoSpecification
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.TempDir
import spock.lang.Title

@Title("expandCustomModule task")
class ExpandCustomModuleTest extends EtendoSpecification {
    @TempDir File testProjectDir

    @Override
    File getProjectDir() {
        return testProjectDir
    }

    public final static String SOURCE_MODULE_GROUP = "com.test"
    public final static String SOURCE_MODULE_NAME  = "moduletoexpand"

    def "Expanding a custom module"() {
        given : "A custom module to expand"
        def moduleSourceGroup = SOURCE_MODULE_GROUP
        def moduleSourceName = SOURCE_MODULE_NAME
        def repoEtendoTest = TEST_REPO
        buildFile << """
        dependencies {
          moduleDeps('${moduleSourceGroup}:${moduleSourceName}:[1.0.0,)@zip') { transitive = true }
        }

        repositories {
          maven {
            url '${repoEtendoTest}'
          }
        }
        """
        when: "The users runs the expandCustomModule task passing by command line the module to expand"
        def expandCustomModuleTaskResult = runTask(":expandCustomModule","-Ppkg=${moduleSourceGroup}.${moduleSourceName}","-DnexusUser=${args.get("nexusUser")}", "-DnexusPassword=${args.get("nexusPassword")}", "-DgithubUser=${args.get("githubUser")}", "-DgithubToken=${args.get("githubToken")}")

        then: "The task will finish successfully"
        expandCustomModuleTaskResult.task(":expandCustomModule").outcome == TaskOutcome.SUCCESS

        and: "The module will be expanded in the 'modules' dir "
        def moduleLocation = new File("${testProjectDir.getAbsolutePath()}/modules/${moduleSourceGroup}.${moduleSourceName}/src-db")
        assert moduleLocation.exists()
    }
}
