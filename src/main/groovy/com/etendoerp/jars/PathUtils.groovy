package com.etendoerp.jars

class PathUtils {

    final static String PACKAGE_SEPARATOR = "."
    final static String JAVA_NESTED = "\$"

    static String fromModuleToPath(String module) {
        return module.replace(PACKAGE_SEPARATOR, File.separator)
    }

    static String createPath(String... dirs) {
        return dirs.join(File.separator).concat(File.separator)
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

}
