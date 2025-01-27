package net.liplum.utils

import arc.Core
import arc.assets.loaders.SoundLoader
import arc.audio.Sound
import arc.func.Cons
import mindustry.Vars
import net.liplum.ClientOnly
import net.liplum.R

val EmptySound: Sound by lazy {
    Sound()
}

fun loadOgg(name: String): Sound =
    loadSound(R.Sound.OGG(name))

fun loadWav(name: String): Sound =
    loadSound(R.Sound.WAV(name))

fun loadMp3(name: String): Sound =
    loadSound(R.Sound.MP3(name))

fun loadAuto(name: String): Sound {
    var found: String? = null
    for (extension in R.Sound.extensions) {
        val path = R.Sound.Gen(name, extension)
        if (Vars.tree.get(path).exists()) {
            found = path
        }
    }
    if (found != null) {
        return loadSound(found)
    }
    return EmptySound
}

fun String.LoadSound(): Sound =
    loadAuto(this)

fun loadSound(path: String): Sound {
    ClientOnly {
        val sound = Sound()
        Core.assets.load(
            path, Sound::class.java, SoundLoader.SoundParameter(sound)
        ).errored = Cons {
            it.printStackTrace()
        }
        return sound
    }
    return EmptySound
}