package com.etendoerp.jars

import org.gradle.api.Project

class PathUtils {

    final static String PACKAGE_SEPARATOR = "."
    final static String JAVA_NESTED = "\$"

    static String fromModuleToPath(String module) {
        return module.replace(PACKAGE_SEPARATOR, File.separator)
    }

    static String createPath(String... dirs) {
        return dirs.join(File.separator).concat(File.separator)
    }
    
    static String createPathWithCustomSeparator(String separator, String... dirs) {
        return dirs.join(separator).concat(separator)
    }

    static String fromPathToPackageFormat(String packagePath) {
        return packagePath.replace(File.separator, PACKAGE_SEPARATOR)
    }

    /**
     * Converts the path of a '.class' with format 'package/path/ClassName.class'
     * to 'package.path.ClassName'.
     * If the java .class is a nested class (contains '$'), only the first java class name is used.
     * @param packagePath
     * @return
     */
    static String fromPathWithClassToPackageFormat(String packagePath) {
        List<String> paths = Arrays.asList(packagePath.split(File.separator))
        String className = paths.last().replace(".class","")

        // Nested classes
        if(className.contains(JAVA_NESTED)) {
            className = className.substring(0, className.indexOf(JAVA_NESTED))
        }

        paths.set(paths.size() - 1, className)

        return paths.join(PACKAGE_SEPARATOR)
    }

    static  List<String> fromPackageToPathClass(List<String> classes){
       ArrayList<String> result = new ArrayList<>()

        for (String currentClass in classes){
            result.add(currentClass.replace('.','/').concat('.class'))
            result.add(currentClass.replace('.','/').concat('$*.class'))
        }
        return result
    }

    static  List<String> fromPackageToPathJava(List<String> classes){
        ArrayList<String> result = new ArrayList<>()

        for (String currentClass in classes){
            result.add(currentClass.replace('.','/').concat('.java'))
        }
        return result
    }


    static  List<String> getClassExcludingGenerated(Project project, String classLocation){
        return  project.fileTree(classLocation).matching ({
            include('**/*.class')
            exclude (fromPackageToPathClass(Utils.generated))
        }).collect({ it.getAbsolutePath()})
    }

}
