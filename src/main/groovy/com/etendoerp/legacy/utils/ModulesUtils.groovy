package com.etendoerp.legacy.utils

import org.gradle.api.Project
import org.gradle.api.internal.tasks.userinput.UserInputHandler

class ModulesUtils {
    /**
     * Method to normalize a specific module
     */
    static normalizeModule(Project project, File pkg, String user, String pass){
        //Get info from AD_Module
        def srcFile = "modules/" + pkg.getName() + "/src-db/database/sourcedata/AD_MODULE.xml"
        def ad_module = new groovy.xml.XmlParser().parse(new File(srcFile))
        String localVersion = ad_module["AD_MODULE"]["VERSION"].text()
        String javaPackage = ad_module["AD_MODULE"]["JAVAPACKAGE"].text()
        def group = splitGroup(javaPackage)
        def artifact = splitArtifact(javaPackage)
        println "TRYING TO NORMALIZE : >> Module: "+javaPackage+" Local Version:" + localVersion
        //Consult info from nexus
        def nexusModuleInfo = NexusUtils.nexusModuleInfo(user, pass, group, artifact)
        if (!nexusModuleInfo.isEmpty()) {
            String[] versions = nexusModuleInfo
            if (versions.contains(localVersion)) {
                DependenciesUtils.modifyDependencies(group, artifact, localVersion)
                println "MODULE NORMALIZED: " + javaPackage + "\n"
            } else {
                println "VERSIONS AVAILABLE: "
                def input = project.getServices().get(UserInputHandler.class)
                localVersion = project.getServices().get(UserInputHandler.class).selectOption("Module Version", nexusModuleInfo, versions.last())
                DependenciesUtils.modifyDependencies(group, artifact, localVersion)
                println "MODULE NORMALIZED: " + javaPackage+"\n"

            }
        } else {
            println "The module is not available with your credentials or not exist \n"
        }
    }

    static String splitGroup(String javaPackage){
        ArrayList<String> parts = javaPackage.split('\\.')
        return parts[0]+"."+parts[1]
    }
    static String splitArtifact(String javaPackage){
        ArrayList<String> parts = javaPackage.split("\\.")
        if ( parts.size()>=2){
            parts= parts.subList(2, parts.size())
            return parts.join(".")
        }
        return null
    }

    static File searchFileInDirIgnoreCase(Project project, String locationToSearch, String filename) {
        File locatedFile = null
        File locationToSearchFile = new File(locationToSearch)
        if (locationToSearchFile && locationToSearchFile.exists() && locationToSearchFile.isDirectory()) {
            for (File file : locationToSearchFile.listFiles()) {
                if (file.name.equalsIgnoreCase(filename)) {
                    locatedFile = file
                    break
                }
            }
        }
        return locatedFile
    }

}
