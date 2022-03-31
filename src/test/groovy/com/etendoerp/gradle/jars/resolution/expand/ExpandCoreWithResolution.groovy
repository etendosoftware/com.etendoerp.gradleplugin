package com.etendoerp.gradle.jars.resolution.expand

import com.etendoerp.gradle.jars.resolution.EtendoCoreResolutionSpecificationTest
import com.etendoerp.legacy.dependencies.EtendoArtifactMetadata
import com.etendoerp.legacy.dependencies.ResolutionUtils
import spock.lang.Issue
import spock.lang.Narrative
import spock.lang.TempDir
import spock.lang.Title

@Title("Expanding core between ranges choose the correct version")
@Narrative("""
Expanding the core having a dependency A:1.0.0 which depends on the core, resolves the correct Core version

-CORE:[21.4.0, 22.1.1]
|
-A:1.0.0
    |---- CORE:[21.4.0, 22.1.0)

The core version resolved should be: 21.4.0

""")
@Issue("EPL-149")
class ExpandCoreWithResolution extends EtendoCoreResolutionSpecificationTest {

    @TempDir File testProjectDir

    @Override
    File getProjectDir() {
        testProjectDir
    }

    @Override
    String getCoreVersion() {
        return "[21.4.0, 22.1.1]"
    }

    def "Cores chooses the matching version when expanding" () {
        given: "A Etendo core '#coreType'"
        addRepositoryToBuildFile(getCoreRepo())

        Map pluginVariables = [
                "coreVersion" : "'${getCoreVersion()}'",
        ]

        loadCore([coreType : "${coreType}", pluginVariables: pluginVariables])

        and: "The users adds a dependency before expanding the core"
        buildFile << """
            dependencies {
              implementation('com.test:dependsOnCoreA:1.0.0')
            }
        """

        when: "The user expand the core"
        resolveCore([coreType : "${coreType}", testProjectDir: testProjectDir])

        then: "The Core version resolved will be the one matched with the module A"
        def artifactPropertiesLocation = new File(testProjectDir, "${EtendoArtifactMetadata.METADATA_FILE}")
        assert artifactPropertiesLocation.exists()
        assert artifactPropertiesLocation.text.contains("21.4.0")

        where:
        coreType  | _
        "sources" | _
    }

    def "Expanding core with version conflicts"() {
        given: "A Etendo core '#coreType'"
        addRepositoryToBuildFile(getCoreRepo())

        Map pluginVariables = [
                "coreVersion" : "'[21.4.0, 22.1.0)'",
                "forceResolution": forceResolution
        ]

        loadCore([coreType : "${coreType}", pluginVariables: pluginVariables])

        and: "The users adds a dependency before expanding the core"
        buildFile << """
            dependencies {
              implementation('com.test:dependsOnCoreA:1.0.1')
            }
        """

        when: "The user expand the core"
        def success = true
        def exception = null
        try {
            resolveCore([coreType : "${coreType}", testProjectDir: testProjectDir])
        } catch (Exception e) {
            exception = e
            success = false
        }

        then: "The core resolution will be performed"

        if (!forceResolution) {
            assert !success
            assert exception.message.contains("${ResolutionUtils.CORE_CONFLICTS_ERROR_MESSAGE}")
        } else {
            assert success
            def artifactPropertiesLocation = new File(testProjectDir, "${EtendoArtifactMetadata.METADATA_FILE}")
            assert artifactPropertiesLocation.exists()
            assert artifactPropertiesLocation.text.contains("21.4.0")
        }

        where:
        coreType  | forceResolution | _
        "sources" | false           | _
        "sources" | true            | _
    }


    def "Expand Core updating the version preserve user files"() {
        given: "A Etendo core '#coreType'"
        addRepositoryToBuildFile(getCoreRepo())

        Map pluginVariables = [
                "coreVersion" : "'21.4.0'",
        ]
        loadCore([coreType : "${coreType}", pluginVariables: pluginVariables])

        and: "The user expand the core"
        resolveCore([coreType : "${coreType}", testProjectDir: testProjectDir])

        and: "The version resolved will be the one specified by the user"
        def artifactPropertiesLocation = new File(testProjectDir, "${EtendoArtifactMetadata.METADATA_FILE}")
        assert artifactPropertiesLocation.exists()
        assert artifactPropertiesLocation.text.contains("21.4.0")

        and: "There is some changes in the core files"
        def coreFileLocation = new File(testProjectDir, "modules_core/com.test.coremodule")
        coreFileLocation.mkdirs()

        and: "The users make some work in the Environment"
        def moduleCreation = new File(testProjectDir, "modules/com.test.usermodule")
        moduleCreation.mkdirs()

        def customProp = "custom.prop=test"
        // Simulates setup
        def configOpenbravoProps = new File(testProjectDir, "config/Openbravo.properties")
        configOpenbravoProps.createNewFile()
        configOpenbravoProps.text += "\n${customProp}\n"

        when: "The users wants to update the version of the core in Sources"
        pluginVariables = [
                "coreVersion" : "'22.1.0'",
        ]
        loadCore([coreType : "${coreType}", pluginVariables: pluginVariables])

        and: "The users resolves the new core version"
        resolveCore([coreType : "${coreType}", testProjectDir: testProjectDir])

        then: "The resolved version will be the new one specified by the user"
        assert artifactPropertiesLocation.exists()
        assert artifactPropertiesLocation.text.contains("22.1.0")

        and: "The files the user changes or create will be conserved"
        assert moduleCreation.exists()

        assert configOpenbravoProps.exists()
        assert configOpenbravoProps.text.contains("${customProp}")

        and: "The files that belong to the core will be sync with the new version"
        assert !coreFileLocation.exists()

        where:
        coreType  | _
        "sources" | _
    }

}
