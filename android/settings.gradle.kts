pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "inventario"

include(":app")
include(":core:domain")
include(":core:common")
include(":core:data")
include(":core:designsystem")
include(":core:imagenes")
include(":feature:auth")
include(":feature:escaneo")
include(":feature:catalogo")
include(":feature:movimientos")
include(":feature:compras")
include(":feature:facturas")
include(":feature:reportes")
include(":feature:ajustes")
