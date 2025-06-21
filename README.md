<center>
# CorePlugin

Kotlin rewrite of Mindurka's main plugin.
</center>

> ⚠️ `todo!();`
> 
> Still in development!

## Packages

- `buj.tl`
Language tables.

- `mindurka.api`
API for CorePlugin.

- `mindurka.ui`
UI library.

- `mindurka.util`
Helpers n stuff.

## Writing gamemodes

`build.gradle`
```groovy
plugins {
    id "org.jetbrains.kotlin.jvm" version "2.1.21"
    id "com.google.devtools.ksp" version "2.1.21-2.0.1"
}

dependencies {
    compileOnly "com.github.Darkdustry-Coders.CorePlugin"
    compileOnly "com.github.Darkdustry-Coders.CorePlugin:annotations"

    ksp "com.github.Darkdustry-Coders.CorePlugin:processor"
}
```

`gradle.properties`
```properties
# Kotlin std is already included in CorePlugin
kotlin.stdlib.default.dependency = false
```

`src/main/../Main.kt`
```kotlin
import mindustry.mod.Plugin
import mindurka.api.Gamemode

class Main: Plugin() {
    override fun init() {
        Gamemode.init(javaClass)
    }
}
```
