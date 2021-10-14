package com.etendoerp.gradle.jars

import com.etendoerp.gradle.tests.EtendoSpecification

abstract class EtendoCoreJarSpecificationTest extends EtendoSpecification {
    public final static String ETENDO_CORE_GROUP   = "com.etendoerp.platformtest"
    public final static String ETENDO_CORE_NAME    = "etendo-core-test"
    public final static String ETENDO_CORE_VERSION = "1.0.0"
    public final static String ETENDO_CORE_REPO    = "https://repo.futit.cloud/repository/etendo-test-core/"

    public final static String CORE = "${ETENDO_CORE_GROUP}:${ETENDO_CORE_NAME}:${ETENDO_CORE_VERSION}"

    def setup() {
        buildFile << JarsUtils.generateDependenciesBlock([CORE])

        buildFile << """
        repositories {
            maven {
                url "${ETENDO_CORE_REPO}"
            }
        }
        """
    }

}
