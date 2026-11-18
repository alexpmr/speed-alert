pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement { repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS); repositories { google(); mavenCentral() } }
rootProject.name="PT2VHF-Speed-Alert"
include(":app", ":osm_centro_oeste", ":osm_sul", ":osm_sudeste")
