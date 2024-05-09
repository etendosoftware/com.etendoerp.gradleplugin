package com.etendoerp.dbdeps;

import org.gradle.api.Project;

class DepsLoader {
  static void load(Project project) {
    DBDepsLoader.load(project);
    SyncDepsLoader.load(project);
  }
}
