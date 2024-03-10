package com.etendoerp.dependencies.processor

enum JarScope {

    COMPILATION ('COMPILATION', 'implementation'),
    TEST        ('TEST', 'testImplementation'),

    private final String scope
    private final String configuration

    JarScope(String scope, String configuration) {
        this.scope = scope
        this.configuration = configuration
    }

    String getScope() {
        return scope
    }

    String getConfiguration() {
        return configuration
    }

    static boolean containsScope(String scope) {
        values()*.scope.contains(scope)
    }

    String toString() {
        scope
    }

}