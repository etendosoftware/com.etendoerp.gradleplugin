package com.etendoerp.gradle.tests.ant

import com.etendoerp.gradle.tests.EtendoSpecification
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.TempDir
import java.nio.file.Files

class DepsTest extends EtendoSpecification {
    @TempDir File testProjectDir

    @Override
    File getProjectDir() {
        return testProjectDir
    }

    def "gradle dependency is added to the ant classpath"() {
        given: "an installed project with an additional dependency"
        addRepositoryToBuildFileFirst(SNAPSHOT_REPOSITORY_URL)

        buildFile << """
        dependencies {
          compile 'com.google.code.gson:gson:2.8.7'
        }
        """

        def expandResult = runTask("expandCore")
        expandResult.task(":expandCore").outcome == TaskOutcome.SUCCESS
        def setupResult = runTask("setup")
        setupResult.task(":setup").outcome == TaskOutcome.SUCCESS
        def installResult = runTask("install")
        installResult.task(":install").outcome == TaskOutcome.SUCCESS

        when: "adding a class that uses the dependency"
        def newClassDirectory = Files.createDirectories(testProjectDir.toPath().resolve("src/com/etendoerp/dependency/test/"))
        def newClassFile = Files.createFile(newClassDirectory.resolve("DependencyTest.java"))
        newClassFile << """
        package com.etendoerp.dependency.test;
        
        import com.google.gson.Gson;
        
        public class DependencyTest {
            public DependencyTest() {
                // taken from: https://github.com/google/gson/blob/master/UserGuide.md#TOC-Overview
                Gson gson = new Gson();
                gson.toJson(1);            // ==> 1
                gson.toJson("abcd");       // ==> "abcd"
                gson.toJson(new Long(10)); // ==> 10
                int[] values = { 1 };
                gson.toJson(values);       // ==> [1]
            }
        }
        """

        then: "compilation with #task succeeds"
        def compilationSmartbuildResult = runTask(":smartbuild")
        compilationSmartbuildResult.task(":smartbuild").outcome == TaskOutcome.SUCCESS

        def compileCompleteResult = runTask(":compile.complete.deploy")
        compileCompleteResult.task(":compile.complete.deploy").outcome == TaskOutcome.SUCCESS

    }

    def "compilation fails when dependency is not added to gradle"() {
        given: "an installed project with a missing dependency"
        addRepositoryToBuildFileFirst(SNAPSHOT_REPOSITORY_URL)

        def expandResult = runTask("expandCore")
        expandResult.task(":expandCore").outcome == TaskOutcome.SUCCESS
        def setupResult = runTask("setup")
        setupResult.task(":setup").outcome == TaskOutcome.SUCCESS
        def installResult = runTask("install")
        installResult.task(":install").outcome == TaskOutcome.SUCCESS

        when: "adding a class that uses the dependency"
        def newClassDirectory = Files.createDirectories(testProjectDir.toPath().resolve("src/com/etendoerp/dependency/test/"))
        def newClassFile = Files.createFile(newClassDirectory.resolve("DependencyTest.java"))
        newClassFile << """
        package com.etendoerp.dependency.test;
        
        import com.google.gson.Gson;
        
        public class DependencyTest {
            public DependencyTest() {
                // taken from: https://github.com/google/gson/blob/master/UserGuide.md#TOC-Overview
                Gson gson = new Gson();
                gson.toJson(1);            // ==> 1
                gson.toJson("abcd");       // ==> "abcd"
                gson.toJson(new Long(10)); // ==> 10
                int[] values = { 1 };
                gson.toJson(values);       // ==> [1]
            }
        }
        """

        then: "compilation with #task fails"
        def compilationResult = runTaskAndFail(task)
        compilationResult.task(":${task}").outcome == TaskOutcome.FAILED

        where:
        task | _
        "smartbuild" | _
        "compile.complete.development" | _
    }
}
