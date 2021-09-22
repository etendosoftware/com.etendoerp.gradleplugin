package com.etendoerp.publication


import org.gradle.api.Project

class PublicationLoader {

    static void load(Project project) {
        SubprojectJarsPublication.load(project)
    }

}
