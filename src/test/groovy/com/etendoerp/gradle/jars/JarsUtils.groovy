package com.etendoerp.gradle.jars

import com.etendoerp.jars.PathUtils

class JarsUtils {

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
            deps += "implementation '${it}' \n"
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
}

