package com.etendoerp.jars

import org.gradle.api.Project

class Utils {
    static  ArrayList <String> generated = null
    final static String fileDir = "/tmp/generated"

    static def  loadGeneratedEntitiesFile(Project project){
        if(generated == null){
            generated = new ArrayList<String>()
            new File("${project.buildDir.absolutePath}${fileDir}").eachLine { line ->
                generated.add(line)
            }
        }
        return generated
    }

    static def existGeneratedClass (String path, Project project) {
        loadGeneratedEntitiesFile(project)
        return generated.contains(path)
    }
}
