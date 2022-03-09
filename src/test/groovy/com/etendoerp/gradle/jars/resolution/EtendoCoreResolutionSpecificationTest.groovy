package com.etendoerp.gradle.jars.resolution

import com.etendoerp.gradle.tests.EtendoSpecification
import org.gradle.testkit.runner.TaskOutcome
import org.gradle.testkit.runner.UnexpectedBuildFailure


abstract class EtendoCoreResolutionSpecificationTest extends EtendoSpecification{

    public final static String ETENDO_CORE_GROUP   = "com.etendoerp.platform"
    public final static String ETENDO_CORE_NAME    = "etendo-core"
    public final static String ETENDO_CORE_VERSION = "[1.0.0,)"
    public final static String ETENDO_CORE_REPO    = "https://repo.futit.cloud/repository/etendo-resolution-test/"

    public final static String CORE = "${ETENDO_CORE_GROUP}:${ETENDO_CORE_NAME}:${ETENDO_CORE_VERSION}"

    public final static String ETENDO_22q1_VERSION = "[22.1.+, 22.2.0)"
    public final static String ETENDO_21q1_SNAPSHOT = "22.1.1-SNAPSHOT"


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

    String getCoreRepo() {
        return ETENDO_CORE_REPO
    }


    void loadCore(Map map=[:]) {

        Map pluginVariables = (map["pluginVariables"] ?: [:] ) as Map
        String coreType = map["coreType"] ?: ""

        if (pluginVariables && pluginVariables.size()) {
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

}
