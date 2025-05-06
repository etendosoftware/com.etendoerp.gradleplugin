package com.etendoerp.gradle.jars

import com.etendoerp.gradle.tests.EtendoSpecification

abstract class EtendoCoreSourcesSpecificationTest extends EtendoSpecification {

    public final static String ETENDO_CORE_GROUP   = "com.etendoerp.platform"
    public final static String ETENDO_CORE_NAME = System.getProperty("etendoCoreName");
    public final static String ETENDO_CORE_VERSION = "[1.0.0,)@zip"
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


    def expandMock() {

        String core = getCore()

        buildFile << """
        
        configurations {
            coreDepMock
        }
        
        dependencies {
           coreDepMock '${core}'
        }
       
        project.tasks.register("unpackCoreToTempMock", Copy) {
            def extractDir =  getTemporaryDir()
            from {
                project.configurations.coreDepMock.collect {
                    project.zipTree(it).getAsFileTree()
                }
            }
            into extractDir
        }
        
        /** Expand the Core dependency */
        project.tasks.register("expandCoreMock", Sync) {
            dependsOn project.tasks.findByName("unpackCoreToTempMock")
            from project.tasks.findByName("unpackCoreToTempMock").getTemporaryDir()
            into "\${project.projectDir}"
            // Preserve files that are allowed to be modified by the user, and those not included in the Core zip
            preserve {
                include 'attachments'
                include 'gradle.properties'
                include 'modules/'
                include 'settings.gradle'
                include 'gradlew.bat'
                include 'gradlew'
                include 'build.gradle'
                include 'gradle/'
                include 'config/Openbravo.properties'
                include 'config/log4j2-web.xml'
                include 'config/log4j2.xml'
                include 'config/Format.xml'
                include 'config/redisson-config.yaml'
            }
        }
        
        """

        buildFile << """
        repositories {
            maven {
                url "${getCoreRepo()}"
                credentials {
                    username "${args.get("nexusUser")}"
                    password "${args.get("nexusPassword")}"
                }
            }
        }
        """
    }

}
