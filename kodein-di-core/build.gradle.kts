plugins {
    id("org.kodein.library.mpp")
}

kodein {
    kotlin {

        add(kodeinTargets.jvm) {
            target.setCompileClasspath()
        }
        add(kodeinTargets.js)
        add(kodeinTargets.native.all)

    }
}

kodeinUpload {
    name = "Kodein-DI-Core"
    description = "KODEIN Dependency Injection Core"
}
