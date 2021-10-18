package com.etendoerp.gradle.jars

import com.etendoerp.gradle.tests.EtendoSpecification

abstract class EtendoCoreJarSpecificationTest extends EtendoSpecification {
    public final static String ETENDO_CORE_GROUP   = "com.etendoerp.platform"
    public final static String ETENDO_CORE_NAME    = "etendo-core"
    public final static String ETENDO_CORE_VERSION = "1.0.1-20210921.201022-1"
    public final static String ETENDO_CORE_REPO    = "https://repo.futit.cloud/repository/maven-snapshots/"

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
