package com.etendoerp

import com.etendoerp.core.CoreMetadata

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

    String coreGroup = CoreMetadata.DEFAULT_ETENDO_CORE_GROUP
    String coreName = CoreMetadata.DEFAULT_ETENDO_CORE_NAME

}
