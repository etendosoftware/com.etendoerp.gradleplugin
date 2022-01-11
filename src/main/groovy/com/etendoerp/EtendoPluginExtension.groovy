package com.etendoerp

import com.etendoerp.core.CoreMetadata

class EtendoPluginExtension {
    String coreVersion = '[20.1.2,)' // default core version

    boolean loadCompilationDependencies = false
    boolean loadTestDependencies = false

    // Flag used to ignore loading the source modules to perform resolution conflicts.
    boolean ignoreSourceModulesResolution = false

    // Flag used to ignore throwing a error if there is conflict resolutions with the Core dependency.
    boolean forceResolution = false
    String coreGroup = CoreMetadata.DEFAULT_ETENDO_CORE_GROUP
    String coreName = CoreMetadata.DEFAULT_ETENDO_CORE_NAME

}
