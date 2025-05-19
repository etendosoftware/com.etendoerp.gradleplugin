package com.etendoerp.gradle.jars.resolution

import com.etendoerp.gradle.tests.EtendoSpecification
import org.gradle.testkit.runner.TaskOutcome
import org.gradle.testkit.runner.UnexpectedBuildFailure


abstract class EtendoCoreResolutionSpecificationTest extends EtendoSpecification {

    public final static String ETENDO_CORE_GROUP   = "com.etendoerp.platform"
    public final static String ETENDO_CORE_NAME    = System.getProperty("etendoCoreName")
    public final static String ETENDO_CORE_CURRENT_VERSION    = System.getProperty("etendoCoreVersion")
    public final static String ETENDO_CORE_VERSION = "[1.0.0,)"
    public final static String ETENDO_CORE_REPO    = "https://repo.futit.cloud/repository/etendo-resolution-test/"

    public final static String CORE = "${ETENDO_CORE_GROUP}:${ETENDO_CORE_NAME}:${ETENDO_CORE_VERSION}"

    public final static String ETENDO_LATEST_SNAPSHOT = System.getProperty("etendoCoreVersion")



    String getCore() {
        return "${getCoreGroup()}:${getCoreName()}:${getCoreVersion()}"
    }

    String getCoreGroup() {
        return ETENDO_CORE_GROUP
    }

    String getCoreName() {
        return ETENDO_CORE_NAME
    }

    String getCoreVersion() {
        return ETENDO_CORE_VERSION
    }

    String getCurrentCoreVersion() {
        return ETENDO_CORE_CURRENT_VERSION
    }

    String getCoreRepo() {
        return RESOLUTION_TEST_REPO
    }


    void loadCore(Map map=[:]) {

        Map pluginVariables = (map["pluginVariables"] ?: [:] ) as Map
        String coreType = map["coreType"] ?: ""

        if (pluginVariables && pluginVariables.size()) {
            pluginVariables.put("ignoreDisplayMenu", true)
            changeExtensionPluginVariables(pluginVariables)
        }

        addRepositoryToBuildFile(getCoreRepo())
        if (coreType.equalsIgnoreCase("sources")) {
            // Add the Core version to expand
        } else if (coreType.equalsIgnoreCase("jar")) {
            // Add the Core 'implementation' to resolve

            buildFile << """
                dependencies {
                    implementation("${getCoreGroup()}:${getCoreName()}:${getCoreVersion()}")
                }
            """
        }

    }

    void resolveCore(Map map=[:]) {
        String coreType = map["coreType"] ?: ""
        File testProjectDir = map["testProjectDir"] as File

        if (coreType.equalsIgnoreCase("sources")) {
            def expandTaskResult = runTask(":expandCore","-DnexusUser=${args.get("nexusUser")}", "-DnexusPassword=${args.get("nexusPassword")}")
            expandTaskResult.task(":expandCore").outcome == TaskOutcome.SUCCESS
            File modulesCore = new File(testProjectDir, "modules_core")
            assert modulesCore.exists()
        } else if (coreType.equalsIgnoreCase("jar")) {
            def dependenciesTaskResult = runTask(":dependencies","--refresh-dependencies", "-DnexusUser=${args.get("nexusUser")}", "-DnexusPassword=${args.get("nexusPassword")}")
            dependenciesTaskResult.task(":dependencies").outcome == TaskOutcome.SUCCESS
            File modules = new File(testProjectDir, "build/etendo/modules")
            assert modules.exists()
        }
    }

    void runSmartBuildTask(boolean isSuccess, String exceptionMessage="", String... args) {
        def success = true
        def exception  = null
        def smartTaskResult = null
        try {
            smartTaskResult = runTask(":smartbuild", args)
        } catch (UnexpectedBuildFailure ignored) {
            exception = ignored
            success = false
        }

        if (isSuccess) {
            assert success
            assert smartTaskResult
            assert smartTaskResult.task(":smartbuild").outcome == TaskOutcome.SUCCESS || TaskOutcome.UP_TO_DATE
        } else {
            assert !success
            assert exception
            assert exception.message.contains(exceptionMessage)
        }
    }

/**
 * Replaces the literal version 'x.y.z' with the actual version of the core in a build.gradle file
 * @param file file The build.gradle file to be modified
 * @param replacement The actual version of the core to replace 'x.y.z'
 */
    void fixCoreVersion(File file, String replacement) {
        if (!file || !file.exists()) {
            return
        }

        String originalContent = file.text

        String updatedContent = originalContent.replaceAll(/\s*x\.y\.z\s*\)/,
                "${replacement}]")

        if (updatedContent != originalContent) {
            file.text = updatedContent
        }
    }

}
