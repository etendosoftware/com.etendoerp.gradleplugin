package com.etendoerp.gradle.jars.resolution.coreconflicts

import com.etendoerp.gradle.jars.resolution.EtendoCoreResolutionSpecificationTest
import com.etendoerp.legacy.dependencies.ResolutionUtils
import org.gradle.testkit.runner.TaskOutcome
import org.gradle.testkit.runner.UnexpectedBuildFailure
import spock.lang.Issue
import spock.lang.Narrative
import spock.lang.TempDir
import spock.lang.Title

@Title("Module with Core conflicts")
@Narrative("""
Given a Core version, adding a module with the core dependency not matching the
actual core version, a conflict error will be throw.
If the user specifies the 'forceResolution' flag to true,
the Exception should be omitted.
    
module:
    coreDependsOnA:[1.0.0, 1.0.1]
    CORE:[21.4]

where:
    -coreDependsOnA:1.0.0
        |----CORE:[21.4.0, 22.1.0)
    
    -coreDependsOnA: 1.0.1
        |----CORE:22.1.0

 Gradle will evaluate the dependencies and found that A has not conflicts because there is not other A dependency found in the graph, 
 then will select the major version ‘1.0.1’. A:1.0.1 depends on CORE:22, but the users specifies the CORE:21.4. 
 A conflict between the CORE dependencies will occur, then, a error should be throw to alert the user.
""")
@Issue("EPL-104")
class CoreConflictsTest extends EtendoCoreResolutionSpecificationTest{

    @TempDir File testProjectDir

    @Override
    File getProjectDir() {
        testProjectDir
    }

    @Override
    String getCoreVersion() {
        return "[21.4.0]"
    }

    def "Module with Core conflicts"() {
        given: "A Etendo core '#coreType'"
        addRepositoryToBuildFile(getCoreRepo())

        Map pluginVariables = ["coreVersion" : "'${getCoreVersion()}'", "forceResolution": forceResolution]
        loadCore([coreType : "${coreType}", pluginVariables: pluginVariables])

        and: "The user resolves the core"
        resolveCore([coreType : "${coreType}", testProjectDir: testProjectDir])

        and: "The users adds a module dependency in JARs dependsOnCoreA:1.0.0"
        buildFile << """
        dependencies {
            implementation('com.test:dependsOnCoreA:${moduleVersion}')  
        }
        """

        when: "The user triggers the configuration phase"

        boolean success = true
        Exception exception  = null
        def dependenciesTaskResult = null
        try {
            dependenciesTaskResult = runTask(":dependencies","--refresh-dependencies", "-DnexusUser=${args.get("nexusUser")}", "-DnexusPassword=${args.get("nexusPassword")}")
        } catch (UnexpectedBuildFailure ignored) {
            exception = ignored
            success = false
        }

        then: "The resolution of conflicts will be trigger"

        if (!forceResolution) {
            and: "The tasks will fail"
            assert !success

            and: "The message should contain the Core conflicts"
            assert exception?.message?.contains("${ResolutionUtils.CORE_CONFLICTS_ERROR_MESSAGE}")
        } else {
            and: "The tasks will NOT fail"
            dependenciesTaskResult?.task(":dependencies")?.outcome == TaskOutcome.SUCCESS

            and: "The message should contain a WARNING with the Core conflicts"
            assert dependenciesTaskResult.output.contains("${ResolutionUtils.CONFLICT_WARNING_MESSAGE} ${getCoreGroup()}:${getCoreName()}")
        }

        where:
        coreType  | supportJars | forceResolution | moduleVersion    | _
        "sources" | true        | false           | "[1.0.0, 1.0.1]" | _
        "jar"     | true        | false           | "[1.0.0, 1.0.1]" | _
        "sources" | true        | true            | "[1.0.0, 1.0.1]" | _
        "jar"     | true        | true            | "[1.0.0, 1.0.1]" | _

    }

    def "Different module versions. Should fail: 'shouldFail'"() {
        given: "A Etendo core '#coreType'"
        addRepositoryToBuildFile(getCoreRepo())

        Map pluginVariables = ["coreVersion" : "'${getCoreVersion()}'"]
        loadCore([coreType : "${coreType}", pluginVariables: pluginVariables])

        and: "The user resolves the core"
        resolveCore([coreType : "${coreType}", testProjectDir: testProjectDir])

        and: "The users adds a module dependency in JARs dependsOnCoreA:1.0.0"
        buildFile << """
        dependencies {
            implementation('com.test:dependsOnCoreA:${moduleVersion}')  
        }
        """

        when: "The user triggers the configuration phase"

        boolean success = true
        Exception exception  = null
        def dependenciesTaskResult = null
        try {
            dependenciesTaskResult = runTask(":dependencies","--refresh-dependencies", "-DnexusUser=${args.get("nexusUser")}", "-DnexusPassword=${args.get("nexusPassword")}")
        } catch (UnexpectedBuildFailure ignored) {
            exception = ignored
            success = false
        }

        then: "The resolution of conflicts will be trigger"

        if (shouldFail) {
            and: "The tasks will fail"
            assert !success

            and: "The message should contain the Core conflicts"
            assert exception?.message?.contains("${ResolutionUtils.CORE_CONFLICTS_ERROR_MESSAGE}")
        } else {
            and: "The tasks will NOT fail"
            dependenciesTaskResult?.task(":dependencies")?.outcome == TaskOutcome.SUCCESS

            and: "The message should NOT contain a WARNING with the Core conflicts"
            assert !dependenciesTaskResult.output.contains("${ResolutionUtils.CONFLICT_WARNING_MESSAGE} ${getCoreGroup()}:${getCoreName()}")
        }

        where:
        coreType  | supportJars | shouldFail      | moduleVersion    | _
        "sources" | true        | false           | "1.0.0"          | _
        "jar"     | true        | false           | "1.0.0"          | _
        "sources" | true        | true            | "1.0.1"          | _
        "jar"     | true        | true            | "1.0.1"          | _

    }


}
