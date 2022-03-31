package com.etendoerp.gradle.jars

import com.etendoerp.jars.PathUtils

class JarsUtils {

    final static String IMPLEMENTATION = "implementation"

    static def generateDependenciesBlock(List<String> dependencies) {
        return """
            dependencies {
                ${dependenciesList(dependencies)}
            }
        """
    }

    static def dependenciesList(List<String> dependencies) {
        def deps = ""
        dependencies.each {
            deps += "${IMPLEMENTATION} '${it}' \n"
        }
        return deps
    }

    static def importsList(List<String> imports) {
        def importList = ""

        imports.each {
            importList += "import ${it};\n"
        }
        return importList
    }

    static String dummyBuildXml() {
        return """<project>
                </project>
        """
    }

    static def generateMethods(List<String> methods){
        def methodList = ""
        methods.each {
            methodList += "${it} \n"
        }
        return methodList
    }

    final static String BUILD_CLASSES= "build/classes"

    static void validateClassFiles(String baseLocation, String module, List<String> customClasses, List<String> nestedClasses) {
        def location = "${baseLocation}/${BUILD_CLASSES}/${PathUtils.fromModuleToPath(module)}"

        for (String customClass : customClasses) {
            assert new File("${location}/${customClass}.class").exists()
            for (String nested : nestedClasses) {
                def nestedClass = "${customClass}\$${nested}"
                assert new File("${location}/${nestedClass}.class").exists()
            }
        }
    }

    static void addCoreMockTask(File buildFile, String core, String repo, String user, String password) {
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
                url "${repo}"
                credentials {
                    username "${user}"
                    password "${password}"
                }
            }
        }
        """
    }
}

