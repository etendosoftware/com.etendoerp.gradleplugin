package com.etendoerp.gradle.jars.publication.publishall

import com.etendoerp.gradle.jars.publication.PublicationUtils
import com.etendoerp.gradle.jars.resolution.EtendoCoreResolutionSpecificationTest
import com.etendoerp.publication.buildfile.ModuleBuildTemplateLoader
import org.gradle.internal.impldep.org.apache.commons.io.FileUtils
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.TempDir
import spock.lang.Title
import spock.lang.Unroll

@Title("Running the 'publishAll' task with different version update scenarios")
class PublishAllVersionTest extends EtendoCoreResolutionSpecificationTest {

    static final String ENVIRONMENTS_LOCATION = "src/test/resources/jars/multiplepublication/updatepublication/modules"
    static final String REPOSITORY = "etendo-multiplepublish-test"
    static final String REPOSITORY_URL = "https://repo.futit.cloud/repository/"

    // Module definitions
    static final String MODULE_A = "com.test.moduleApublish"
    static final String MODULE_B = "com.test.moduleBpublish"
    static final String MODULE_C = "com.test.moduleCpublish"
    static final String MODULE_BUNDLE = "com.test.module.bundle"

    @TempDir
    File testProjectDir

    @Override
    File getProjectDir() {
        return testProjectDir
    }

    // Clean the repository before each test
    def setup() {
        PublicationUtils.cleanRepositoryModules(REPOSITORY)
    }

    // Clean the repository after all tests
    def cleanupSpec() {
        PublicationUtils.cleanRepositoryModules(REPOSITORY)
    }

    @Unroll
    def "Publishing all modules with #updateType version update should result in version #expectedVersion"() {
        given: "A project with modules A, B, C and a bundle module"
        setupTestModules()

        when: "The user runs the 'createModuleBuild' task with the command line parameter '-Ppkg=all'"
        def resultCreateModuleBuild = runTask(":createModuleBuild",
                "-P${com.etendoerp.publication.PublicationUtils.MODULE_NAME_PROP}=${ModuleBuildTemplateLoader.ALL_COMMAND_PROPERTY}",
                "-P${com.etendoerp.publication.PublicationUtils.REPOSITORY_NAME_PROP}=${REPOSITORY_URL}${REPOSITORY}")

        then: "The task finishes successfully"
        assert resultCreateModuleBuild.task(":createModuleBuild").outcome == TaskOutcome.SUCCESS

        and: "Build files are created for all modules"
        validateBuildFilesCreated()

        and: "The bundle module is set up correctly"
        setupBundleModule()

        when: "The user runs the 'publishAll' task with update type '#updateType'"
        def runPublishAllResult = runTask(":publishAll",
                "-P${com.etendoerp.publication.PublicationUtils.MODULE_NAME_PROP}=${MODULE_BUNDLE}",
                "-PupdateLeaf=true",
                "-Pupdate=${updateType}")

        then: "The task finishes successfully"
        assert runPublishAllResult.task(":publishAll").outcome == TaskOutcome.SUCCESS

        and: "All modules are published with the expected version and correct content"
        validateModulesPublishedWithContent(expectedVersion)

        where:
        updateType | expectedVersion
        "patch"    | "1.0.1"
        "minor"    | "1.1.0"
        "major"    | "2.0.0"
    }

    /**
     * Sets up the test modules A, B, and C in the test project
     */
    protected void setupTestModules() {
        def modulesDir = new File(testProjectDir, "modules")
        modulesDir.mkdirs()

        // Setup Module A
        copyModuleToProject(MODULE_A, modulesDir)

        // Setup Module B (depends on A)
        copyModuleToProject(MODULE_B, modulesDir)

        // Setup Module C (no dependencies)
        copyModuleToProject(MODULE_C, modulesDir)
    }

    /**
     * Copies a module from the test resources to the project modules directory
     */
    protected void copyModuleToProject(String moduleName, File modulesDir) {
        FileUtils.copyDirectoryToDirectory(new File("${ENVIRONMENTS_LOCATION}/${moduleName}"), modulesDir)
        File moduleLocation = new File(testProjectDir, "modules/${moduleName}")
        assert moduleLocation.exists() : "Module ${moduleName} directory does not exist: ${moduleLocation.absolutePath}"

        // Delete existing build.gradle to be recreated
        File buildFile = new File(moduleLocation, "build.gradle")
        if (buildFile.exists()) {
            buildFile.delete()
        }
    }

    /**
     * Validates that build.gradle files have been created for all modules
     */
    protected void validateBuildFilesCreated() {
        [MODULE_A, MODULE_B, MODULE_C].each { moduleName ->
            File moduleLocation = new File(testProjectDir, "modules/${moduleName}")
            File buildFile = new File(moduleLocation, "build.gradle")
            assert buildFile.exists() : "Build file for ${moduleName} was not created"
            fixCoreVersion(buildFile, getCurrentCoreVersion())
        }
    }

    private void setupBundleModule() {
        def modulesDir = new File(testProjectDir, "modules")

        // Copy bundle module
        FileUtils.copyDirectoryToDirectory(new File("${ENVIRONMENTS_LOCATION}/${MODULE_BUNDLE}"), modulesDir)
        File moduleBundleLocation = new File(testProjectDir, "modules/${MODULE_BUNDLE}")
        assert moduleBundleLocation.exists() : "Module Bundle directory does not exist: ${moduleBundleLocation.absolutePath}"

        // Create extension-modules.gradle file
        def modules = [
                MODULE_A,  // A - Base module (no dependencies)
                MODULE_C,  // C - Independent module (no dependencies)
                MODULE_B   // B - Depends on A (must come after A)
        ]
        File extensionModulesFile = createExtensionModulesFileInModule(moduleBundleLocation, modules)
        assert extensionModulesFile.exists() : "Extension modules file does not exist: ${extensionModulesFile.absolutePath}"

        // Validate bundle build file
        File bundleBuildFile = new File(moduleBundleLocation, "build.gradle")
        assert bundleBuildFile.exists() : "Bundle build file was not created"
        fixCoreVersion(bundleBuildFile, getCurrentCoreVersion())

        // Validate bundle dependencies
        String bundleBuildContent = bundleBuildFile.text
        assert bundleBuildContent.contains("dependencies {")
        assert bundleBuildContent.contains("implementation 'com.test:moduleBpublish:1.0.0'")
        assert bundleBuildContent.contains("implementation 'com.test:moduleCpublish:1.0.0'")
    }

    /**
     * Validates that all modules have been published with the expected version
     */
    protected void validateModulesPublished(String expectedVersion) {
        Map<String, List<String>> modulesData = new TreeMap(String.CASE_INSENSITIVE_ORDER)
        modulesData.put(MODULE_A, [expectedVersion])
        modulesData.put(MODULE_B, [expectedVersion])
        modulesData.put(MODULE_C, [expectedVersion])
        modulesData.put(MODULE_BUNDLE, [expectedVersion])

        PublicationUtils.repoContainsModules(REPOSITORY, modulesData)
    }
    /**
     * Validates that all modules have been published with the expected version and content
     */
    protected void validateModulesPublishedWithContent(String expectedVersion) {
        // First validate that modules exist in repository
        validateModulesPublished(expectedVersion)

        PublicationUtils.validateModuleContents(MODULE_A, expectedVersion, "com/test/moduleApublish/ModuleA.class",
                "com.test.moduleApublish/src/com/test/moduleApublish/ModuleA.java", REPOSITORY, REPOSITORY_URL, testProjectDir)

        PublicationUtils.validateModuleContents(MODULE_B, expectedVersion, "com/test/ModuleB.class",
                "com.test.moduleBpublish/src/com/test/ModuleB.java", REPOSITORY, REPOSITORY_URL, testProjectDir)

        PublicationUtils.validateModuleContents(MODULE_C, expectedVersion, "com/test/ModuleC.class",
                "com.test.moduleCpublish/src/com/test/ModuleC.java", REPOSITORY, REPOSITORY_URL, testProjectDir)
    }
}
