package com.etendoerp.legacy.utils

import org.gradle.api.Project

class DependenciesUtils {

    /**
     * Method to add dependency in build.gradle and load it into memory
     * */
    static void modifyDependencies(String group, String artifact, String version ) {

        BufferedReader br_build = new BufferedReader(new FileReader("./build.gradle"));
        String newBuildFile = "";
        String line;

        while ((line = br_build.readLine()) != null) {
            newBuildFile += line + "\n";
            if (line.contentEquals("dependencies {")){
                newBuildFile += "    moduleDeps group: '"+group+"', name: '"+artifact+"', version:'["+version+",)', ext:'zip', transitive: true\n"
            }
        }
        br_build.close();

        FileWriter fw_build = new FileWriter("./build.gradle");
        fw_build.write(newBuildFile);
        fw_build.close();
    }


/**
 * Creates the dependencies text wrapper.
 * Inserts the pom.xml dependencies if the searchPom parameter is true
 * @param searchPom Boolean to search dependencies on the pom.xml file
 * @param pkgVar String containing the package name
 * @return String containing the dependencies text
 */
    static String createDependenciesText(Project project, searchPom, pkgVar) {
        def pomDep = searchPom ? generatePomDependencies(project, pkgVar) : ""
        def text = "\ndependencies {\n" +
                "    //Add dependencies here\n" +
                pomDep +
                "}\n"
        return text
    }

    /**
     * Generates the pom.xml dependencies in a specific format
     * @param pkgVar String containing the package name
     * @return String containing the dependencies
     */
    static String generatePomDependencies(Project project, String pkgVar, def config = "compile") {
        String prefix       = "$config"
        String delimiter    = ":"
        String dependencies = ""

        def mapTypes = ["zip": "@"]
        def srcPomFile = "modules/" + pkgVar + "/pom.xml"

        // pom file not found
        if (!new File(srcPomFile).exists()) {
            project.logger.info('pom file path * ' + srcPomFile + ' * not found.')
            return dependencies
        }

        def pomParser = new XmlParser().parse(srcPomFile)

        pomParser["dependencies"]["dependency"].each { dep ->
            def groupId = dep["groupId"].text()
            def artifactId = dep["artifactId"].text()
            def version = dep["version"].text()
            def type = dep["type"]

            def depName = groupId + delimiter +
                    artifactId + delimiter +
                    version

            // continue if type node not exists
            if (!type) {
                project.logger.info('Node * TYPE * not found in * ' + depName + ' * dependency')
                dependencies += "    ${prefix} '$depName' \n"
                return
            }

            type = type.text()
            String typePrefix

            if (!(typePrefix = mapTypes.get(type))) {
                project.logger.info('Type * ' + type + ' * not recognized in * ' + depName + ' * dependency')
                return
            }

            dependencies += prefix +
                    "'" +
                    depName +
                    typePrefix + type +
                    "'" + "\n"
        }
        return dependencies.substring(0, dependencies.length() - 1)
    }

}
