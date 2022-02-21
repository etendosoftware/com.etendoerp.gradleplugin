package com.etendoerp

import com.etendoerp.core.CoreMetadata
import com.etendoerp.legacy.ant.ConsistencyVerification

class EtendoPluginExtension {
    String coreVersion = '[20.1.2,)' // default core version

    boolean loadCompilationDependencies = false
    boolean loadTestDependencies = false

    // Flag used to ignore loading the source modules to perform resolution conflicts.
    boolean ignoreSourceModulesResolution = false

    // Flag used to perform or not the resolution conflicts
    boolean performResolutionConflicts = true

    // Flag used to ignore throwing a error if there is conflict resolutions with the Core dependency.
    boolean forceResolution = false

    // Flag used to apply the subproject dependencies to the main project
    boolean applyDependenciesToMainProject = true

    // Flag used to prevent overwriting the transitive source modules when performing the expand
    boolean overwriteTransitiveExpandModules = true

    // Flag used to exclude the Core dependency from each subproject to all the configurations.
    boolean excludeCoreDependencyFromSubprojectConfigurations = true

    // Flag used to indicate that the current Core version support jars (default true)
    boolean supportJars = true

    // List of Etendo artifacts to always extract
    List<String> ignoredArtifacts = []

    // Flag use to prevent throwing error on version inconsistency between modules
    boolean ignoreConsistencyVerification = false

    String coreGroup = CoreMetadata.DEFAULT_ETENDO_CORE_GROUP
    String coreName = CoreMetadata.DEFAULT_ETENDO_CORE_NAME


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

}
