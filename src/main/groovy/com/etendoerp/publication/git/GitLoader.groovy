package com.etendoerp.publication.git

import org.gradle.api.Project

class GitLoader {
    static load(Project project) {
        PushTags.load(project)
    }
}
