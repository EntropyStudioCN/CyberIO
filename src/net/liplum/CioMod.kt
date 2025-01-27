package net.liplum

import arc.Core
import arc.Events
import arc.util.Log
import arc.util.Time
import mindustry.Vars
import mindustry.game.EventType.*
import mindustry.io.JsonIO
import mindustry.mod.Mod
import mindustry.mod.Mods
import net.liplum.api.cyber.DataCenter
import net.liplum.api.cyber.StreamCenter
import net.liplum.blocks.cloud.LiplumCloud
import net.liplum.blocks.cloud.SharedRoom
import net.liplum.inputs.UnitTap
import net.liplum.lib.animations.ganim.GlobalAnimation.Companion.loadAllResources
import net.liplum.npc.NpcSystem
import net.liplum.registries.*
import net.liplum.render.LinkDrawer
import net.liplum.ui.SettingsUI
import net.liplum.utils.G

class CioMod : Mod() {
    companion object {
        @JvmField val IsClient = !Vars.headless
        @JvmField var DebugMode = true
        @JvmField var TestGlCompatibility = false
        @JvmField var ExperimentalMode = false
        @JvmField var CanGlobalAnimationPlay = false
        @JvmField var UpdateFrequency = 5f
        lateinit var Info: Mods.LoadedMod
    }

    init {
        Log.info("Cyber IO mod loaded.")
        GL.handleCompatible()
        //listen for game load event
        Events.on(ClientLoadEvent::class.java) {
            //show welcome dialog upon startup
            Time.runTask(10f) { Welcome.showWelcomeDialog() }
        }
        Events.on(FileTreeInitEvent::class.java) {
            Core.app.post {
                CioShaders.init()
                Script.init()
                Script.initInterpreter()
                Script.loadStory("TestStory")
            }
        }
        Events.on(DisposeEvent::class.java) {
            CioShaders.dispose()
        }
    }

    override fun init() {
        Welcome.modifierModInfo()
        UpdateFrequency = if (Vars.mobile || Vars.testMobile)
            10f
        else
            5f
        JsonIO.json.addClassTag(SharedRoom::class.java.name, SharedRoom::class.java)
        // Cloud is developing
        DebugOnly {
            Events.on(WorldLoadEvent::class.java) {
                LiplumCloud.reset()
                LiplumCloud.read()
            }
            Events.on(SaveWriteEvent::class.java) {
                LiplumCloud.reset()
                LiplumCloud.save()
            }
        }
        DebugOnly {
            Vars.enableConsole = true
        }
        tintedBulletsRegistryLoad()
        Events.run(Trigger.update) {
            val state = Vars.state
            if (state.isGame && !state.isPaused) {
                LiplumCloud.update()
            }
        }
        DataCenter.initData()
        StreamCenter.initAndLoad()
        CioShaders.loadResource()
        loadAllResources()
        Events.run(Trigger.preDraw) {
            G.init()
        }
        SettingsUI.appendSettings()
        Settings.updateSettings()
        LinkDrawer.register()
        NpcSystem.register()
        Core.input.addProcessor(UnitTap)
        Log.info("Cyber IO initialized.")
    }

    override fun loadContent() {
        Info = Vars.mods.locateMod(Meta.ModID)
        CioSounds.load()
        EntityRegistry.registerAll()
        CioCLs.load()
        ContentRegistry.loadContent()
        PrismBlackList.load()
        CanGlobalAnimationPlay = true
        Log.info("Cyber IO mod's contents loaded.")
    }
}