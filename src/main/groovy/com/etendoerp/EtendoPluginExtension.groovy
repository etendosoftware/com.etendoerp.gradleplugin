package com.etendoerp

import com.etendoerp.core.CoreMetadata
import com.etendoerp.legacy.ant.ConsistencyVerification

class EtendoPluginExtension {
    String coreVersion = '[22.1.0,)' // default core version
    String coreGroup = CoreMetadata.DEFAULT_ETENDO_CORE_GROUP
    String coreName = CoreMetadata.DEFAULT_ETENDO_CORE_NAME

    boolean loadCompilationDependencies = true
    boolean loadTestDependencies = false

    /**
     * Flag used to ignore loading the source modules to perform resolution conflicts.
     * Default true
     */
    boolean ignoreSourceModulesResolution = true

    /**
     * Flag used to perform or not the resolution conflicts.
     * Default true
     */
    boolean performResolutionConflicts = true

    /**
     * Flag used to ignore throwing a error if there is conflict resolutions with the Core dependency.
     * Default false
     */
    boolean forceResolution = false

    /**
     * Flag used to apply the subproject dependencies to the main project.
     * Default true
     */
    boolean applyDependenciesToMainProject = true

    /**
     * Flag used to prevent overwriting the transitive source modules when performing the expand.
     * Default true
     */
    boolean overwriteTransitiveExpandModules = true

    /**
     * Flag used to exclude the Core dependency from each subproject to all the configurations.
     * Default true
     */
    boolean excludeCoreDependencyFromSubprojectConfigurations = true

    /**
     *  Flag used to indicate that the current Core version support jars.
     *  Default true
     */
    boolean supportJars = true

    /**
     * Flag used to only expand the modules in source format.
     * Default false
     */
    boolean onlySourceModules = false

    /**
     * List of Etendo artifacts to always extract and ignore from the version consistency verification.
     */
    List<String> ignoredArtifacts = []

    /**
     * Flag use to prevent throwing error on version inconsistency between modules.
     * Default false
     */
    boolean ignoreConsistencyVerification = false

    /**
     * Flag used to prevent throwing error when an artifact could not be resolved.
     * This includes transitives ones.
     * Default false
     */
    boolean ignoreUnresolvedArtifacts = false

    /**
     * The list of modules that should not be re expanded.
     * Default empty.
     */
    List<String> sourceModulesInDevelopment = []

    /**
     * Flag used to ignore the Etendo CORE jar dependency located in the
     * build.gradle of the root project.
     * Default false.
     */
    boolean ignoreCoreJarDependency = false

    /**
     * Flag used to prevent the display of the multiple menus.
     * Default false.
     */
    boolean ignoreDisplayMenu = false

    /**
     * Flag used to load the CORE JARs dependencies when is in SOURCES.
     * Default false.
     */
    boolean loadCoreDependencies = false

    /************************ MESSAGES ************************/

    static String ignoredArtifactsMessage(String exampleModule) {
        String message = ""
        message += "* To ignore artifacts versions verification add it to the plugin extension \n"
        message += "* Example: \n"
        message += "* etendo { \n"
        message += "*    ignoredArtifacts = ['com.test.mymodule', '${exampleModule}'] \n"
        message += "* } \n"
        return message
    }

    static String ignoreConsistencyVerificationMessage() {
        String message = ""
        message += "------------------------------------------------------------------------\n"
        message += "To ignore the version consistency verification use the plugin extension.\n"
        message += "etendo {\n"
        message += "    ignoreConsistencyVerification = true \n"
        message += "} \n"
        message += "or run the task with the '-P${ConsistencyVerification.IGNORE_CONSISTENCY}=true' flag. \n"
        return message
    }

    static String forceResolutionMessage() {
        String message = ""
        message += "------------------------------------------------------------------------\n"
        message += "To force the resolution use the plugin extension. \n"
        message += "etendo {\n"
        message += "    forceResolution = true \n"
        message += "} \n"
        return message
    }

    static String sourceModulesInDevelopMessage() {
        return  """
        |----------------------------------------------------------------------------
        |* To ignore the expansion of a custom module add it to the plugin extension.
        |* Example:
        |* etendo {
        |*    sourceModulesInDevelopment = ['com.test.custommodule']
        |* }
        |""".stripMargin()
    }

    static String ignoreCoreJarDependencyMessage() {
        return """
        |* The Etendo CORE jar dependency is located in the build.gradle of the root project.
        |* Remove the dependency or use the plugin extension.
        |* etendo {
        |*  ignoreCoreJarDependency = true
        |* }""".stripMargin()
    }

    static String ignoreUnresolvedArtifactsMessage() {
        return  """
        |----------------------------------------------------------------------------
        |* To ignore the unresolved artifacts use the plugin extension.
        |* Example:
        |* etendo {
        |*    ignoreUnresolvedArtifacts = true
        |* }
        |""".stripMargin()
    }

}
