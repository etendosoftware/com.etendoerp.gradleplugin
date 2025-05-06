package com.etendoerp.gradle.jars

import com.etendoerp.gradle.tests.EtendoSpecification

abstract class EtendoCoreJarSpecificationTest extends EtendoSpecification {

    public final static String ETENDO_CORE_GROUP   = "com.etendoerp.platform"
    public final static String ETENDO_CORE_NAME = System.getProperty("etendoCoreName");
    public final static String ETENDO_CORE_VERSION = "[1.0.0,)"
    public final static String ETENDO_CORE_REPO    = System.getProperty("etendoCoreRepo")

    public final static String CORE = "${ETENDO_CORE_GROUP}:${ETENDO_CORE_NAME}:${ETENDO_CORE_VERSION}"


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

    def setup() {

        String core = getCore()

        buildFile << JarsUtils.generateDependenciesBlock([core])

        buildFile << """
        repositories {
            maven {
                url "${getCoreRepo()}"
            }
        }
        """
    }

}
