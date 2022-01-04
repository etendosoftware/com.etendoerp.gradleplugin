package com.etendoerp.legacy.modules

import com.etendoerp.jars.PathUtils
import com.etendoerp.legacy.modules.expand.ExpandCustomModule
import com.etendoerp.legacy.modules.expand.ExpandModules
import com.etendoerp.legacy.utils.NexusUtils
import com.etendoerp.publication.PublicationUtils
import org.gradle.api.Project
import org.gradle.api.file.FileTree
import org.gradle.api.tasks.Delete

class ExpandModulesLoader {

    static void load(Project project) {
        ExpandCustomModule.load(project)
        ExpandModules.load(project)
    }
}
