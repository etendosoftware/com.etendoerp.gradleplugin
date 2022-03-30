package com.etendoerp.css

import org.gradle.api.Project
import org.gradle.api.file.FileTree

class CssCompileLoader {

    static final STYLE_FLAG     = "--style"
    static final DEFAULT_STYLE  = "expanded"
    static final STYLE_PROPERTY = "style"

    static void load(Project project) {
        project.tasks.register("cssCompile") {
            doLast {
                def style = project.findProperty(STYLE_PROPERTY) ?: DEFAULT_STYLE
                def styleArg = "${STYLE_FLAG}=${style}"

                def gen_css = []
                def lookupAt = ['modules_core', 'modules', 'web']
                lookupAt.forEach({dirName ->
                    FileTree tree = project.fileTree(dirName).matching {
                        include '**/*.scss'
                    }.each {
                        def less_fl = it.toString()
                        def css_fl = less_fl.replaceAll(".scss", ".css")
                        println(it)
                        gen_css.add(css_fl)
                        List argsList = [less_fl, css_fl, styleArg]
                        project.exec {
                            executable "sass"
                            args argsList
                        }
                    }
                })
            }
        }
    }
}
