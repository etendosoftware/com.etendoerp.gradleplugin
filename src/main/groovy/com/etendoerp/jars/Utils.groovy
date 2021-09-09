package com.etendoerp.jars

import groovy.util.logging.Log
import org.gradle.api.Project

import java.nio.file.NoSuchFileException

class Utils {
    static  ArrayList <String> generated = null
    final static String fileDir = "/tmp/generated"

    static def  loadGeneratedEntitiesFile(Project project){

        generated = new ArrayList<String>()
        def file = new File("${project.buildDir.absolutePath}${fileDir}")
        if (!file.exists()){
            throw new IllegalArgumentException( "generated file not exist, run compilecomplete or generate.entities to create the generated file")
        }
        file.eachLine { line ->
            generated.add(line)
        }


        return generated
    }

    static def existGeneratedClass (String path, Project project) {
        loadGeneratedEntitiesFile(project)
        return generated.contains(path)
    }
}
