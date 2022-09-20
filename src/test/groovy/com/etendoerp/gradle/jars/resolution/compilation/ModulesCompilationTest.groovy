package com.etendoerp.gradle.jars.resolution.compilation

import com.etendoerp.gradle.jars.resolution.EtendoCoreResolutionSpecificationTest
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome
import org.gradle.testkit.runner.UnexpectedBuildFailure
import spock.lang.Issue
import spock.lang.Narrative
import spock.lang.TempDir
import spock.lang.Title

@Title("Compiling a module with dependency on other module in Sources")
@Narrative("""
Compiling a module B depending on A already in sources, if a new version of A is added
(transitive), then B should NOT compile with the classpath of the new JAR module of A.

B:1.0.0
    |---- A:[1.0.0, 1.0.1]
    
C:1.0.0
    |---- A:[1.0.1]

""")
@Issue("EPL-104")
class ModulesCompilationTest extends EtendoCoreResolutionSpecificationTest{

    @TempDir File testProjectDir

    @Override
    File getProjectDir() {
        testProjectDir
    }

    // TODO: Use latest snapshot
    @Override
    String getCoreVersion() {
        return ETENDO_LATEST_SNAPSHOT
    }

    @Override
    String getDB() {
        return this.getClass().getSimpleName().toLowerCase()
    }

    def "Compiling a module does not include the JAR module classpath if is already in Sources"() {
        given: "A Etendo core '#coreType'"
        addRepositoryToBuildFile(SNAPSHOT_REPOSITORY_URL)
        addRepositoryToBuildFile(getCoreRepo())

        Map pluginVariables = ["coreVersion" : "'${getCoreVersion()}'", forceResolution: true, ignoreDisplayMenu : true]
        loadCore([coreType : "${coreType}", pluginVariables: pluginVariables])

        and: "The user resolves the core"
        resolveCore([coreType : "${coreType}", testProjectDir: testProjectDir])

        if (coreType == "sources") {
            def setupResult = runTask("setup")
            def installResult = runTask("install")

            setupResult.task(":setup").outcome == TaskOutcome.SUCCESS || TaskOutcome.UP_TO_DATE
            installResult.task(":install").outcome == TaskOutcome.SUCCESS
        }

        and: "The user adds two dependencies (A and B) where B depends on A to compile "
        def moduleA = "com.test.compilationA"
        def moduleB = "com.test.compilationB"

        buildFile << """
            dependencies {
              moduleDeps('com.test:compilationA:1.0.0@zip')
              moduleDeps('com.test:compilationB:1.0.0@zip')
            }
        """

        // TODO: Add flag menu RDY

        and: "The user runs the 'expandModules' task"
        def expandTaskResult = runTask(":expandModules", "-DnexusUser=${args.get("nexusUser")}", "-DnexusPassword=${args.get("nexusPassword")}")
        expandTaskResult.task(":expandModules").outcome == TaskOutcome.SUCCESS

        and: "The modules A and B should be expanded in the 'modules' dir"
        def modulesLocation = new File(testProjectDir, "modules")

        def moduleALocation = new File(modulesLocation, "${moduleA}")
        assert moduleALocation.exists()

        def moduleBLocation = new File(modulesLocation, "${moduleB}")
        assert moduleBLocation.exists()

        and: "The users compile the module B depending on A"
        def jarResult = runTask(":modules:${moduleB}:jar", "-Ppkg=${moduleB}") as BuildResult

        and: "The task will complete successfully."
        jarResult.task(":modules:${moduleB}:jar").outcome == TaskOutcome.SUCCESS

        and: "The jar file will be created in the /build/libs of the module folder."
        def jarFile = new File("${testProjectDir.absolutePath}/modules/${moduleB}/build/libs/${moduleB}-1.0.0.jar")
        assert jarFile.exists()

        when: "The users adds a new dependency in the build.gradle C depending on A:1.0.1 (contains new classes)"

        buildFile << """
            dependencies {
              implementation('com.test:compilationC:1.0.0') 
            }
        """

        def dependenciesTaskResult = runTask(":dependencies","--refresh-dependencies", "-DnexusUser=${args.get("nexusUser")}", "-DnexusPassword=${args.get("nexusPassword")}")
        dependenciesTaskResult.task(":dependencies").outcome == TaskOutcome.SUCCESS

        and: "The user tries to use the new classes from A in the module B"
        def classBLocation = new File(moduleBLocation, "src/com/test/compilationB/CompilationB.java")
        assert classBLocation.exists()

        def classText = classBLocation.text
        def lines = classText.readLines()
        lines = lines.plus(1, "import com.test.compilationA.CompilationAExtra;")
        classBLocation.text = lines.join("\n")

        and: "The users compiles the module B depending on A using the NEW class"
        boolean success = true
        Exception exception  = null
        def newJarResult = null
        try {
            newJarResult = runTask(":modules:${moduleB}:jar", "-Ppkg=${moduleB}") as BuildResult
        } catch (UnexpectedBuildFailure ignored) {
            exception = ignored
            success = false
        }

        then: "The task will fail because the class is not found."
        assert !success
        assert exception.message.contains("cannot find symbol")
        assert exception.message.contains("symbol:   class CompilationAExtra")

        where:
        coreType  | _
        "sources" | _
        "jar"     | _

    }

}
