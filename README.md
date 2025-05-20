### README

To work with the plugin locally, clone the repository with the name 'buildSrc' in the root directory of the project.

``` shell
git clone git@github.com:etendosoftware/com.etendoerp.gradleplugin.git buildSrc
```

Then, go to the ```build.gradle``` file of Etendo and comment the version of the plugin.

``` build.gradle
plugins {
    ...
    id 'com.etendoerp.gradleplugin' // version '<version>'
}
```

Finally, go to ```buildSrc``` directory and copy the ```gradle.properties.template``` file to ```gradle.properties```. If needed, put your credentials in this file.
