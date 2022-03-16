package com.etendoerp.publication

import org.gradle.api.Project
import org.gradle.api.publish.Publication

import java.nio.file.Path
import java.nio.file.Paths;

class CloneDependencies {

    static String[] filesToDelete = ["pom.xml", "assembly.xml", ".hgtags"]
    static String subFolder = "modules/"

    static void load(Project project) {
        project.tasks.register("cloneDependencies") {
            doLast {
                def javaPackage = PublicationUtils.loadModuleName(project)
                def moduleProject = project.findProject(":${PublicationUtils.BASE_MODULE_DIR}")
                def subProject = moduleProject.subprojects.find {
                    it.name == javaPackage
                }
                if (subProject == null) {
                    throw new IllegalArgumentException("The javapackage ${javaPackage} not found")
                }
                List<String> repoList = subProject.findProperty("defaultExtensionModules") as List

                repoList.each {
                    String[] splitURI = ((String) it).split("/")
                    String nameDirectory = splitURI[1].substring(0, splitURI[1].size() - 4)
                    Path modulesDirectory = Paths.get(subFolder + nameDirectory)
                    Git.gitClone(project, modulesDirectory, (String) it)
                }

                // Remove pom and assembly files
                // List of modules in modules without current module
                FileFilter filterFolders = new FileFilter() {
                    @Override
                    boolean accept(File arch) {
                        return arch.isDirectory() && arch.getName() != javaPackage;
                    }
                }

                File[] folderModules = new File(subFolder).listFiles(filterFolders)
                for (File folder : folderModules) {
                    // Filter by files to remove
                    FileFilter filterFilesToDelete = new FileFilter() {
                        @Override
                        boolean accept(File arch) {
                            return arch.isFile() && filesToDelete.contains(arch.getName());
                        }
                    }

                    // Filter and remove the files
                    boolean filesDeleted = false
                    folder.listFiles(filterFilesToDelete).each {
                        it.delete()
                        filesDeleted = true
                    }

                    // Commit and push the changes produced by files removed
                    if (filesDeleted) {
                        project.logger.debug("Removing unused files from " + folder.getName())
                        Path directory = Paths.get(subFolder + folder.getName())
                        Git.gitStage(project, directory)
                        Git.gitCommit(project, directory, "Remove unused files")
                        project.logger.debug("Push to " + folder.getName())
                        Git.gitPush(project, directory)
                    }
                }
            }
        }
    }
}
