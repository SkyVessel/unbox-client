plugins {
    id("fuzs.multiloader.multiloader-convention-plugins-common")
}

dependencies {
    compileOnly(files("../../puzzleslib/Common/build/libs/puzzleslib-v26.1.16-mc26.1.x+common.jar"))
    compileOnly(files("../../iteminteractions/Common/build/libs/iteminteractions-v26.1.9-mc26.1.x+common.jar"))
}

neoForge { accessTransformers.from(file("../../puzzleslib/Common/build/generated/resources/META-INF/accesstransformer.cfg")) }
