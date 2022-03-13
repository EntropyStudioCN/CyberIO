package net.liplum.blocks.icmachine

import arc.graphics.g2d.Draw
import arc.math.Mathf
import arc.util.Time
import net.liplum.ClientOnly
import net.liplum.animations.anims.Animation
import net.liplum.animations.anims.AnimationObj
import net.liplum.animations.anis.AniState
import net.liplum.animations.anis.config
import net.liplum.blocks.AniedCrafter
import net.liplum.utils.FUNC
import net.liplum.utils.autoAnim
import net.liplum.utils.isZero

private typealias AniStateM = AniState<ICMachine, ICMachine.ICMachineBuild>

private const val workingAnimAlphaA = 0.1f / (0.03f * 0.03f)

open class ICMachine(name: String) : AniedCrafter<ICMachine, ICMachine.ICMachineBuild>(name) {
    @ClientOnly lateinit var IdleState: AniStateM
    @ClientOnly lateinit var WorkingState: AniStateM
    @ClientOnly lateinit var WorkingAnim: Animation
    @ClientOnly @JvmField var WorkingAnimFrameNumber = 4
    @ClientOnly @JvmField var WorkingAnimDuration = 120f
    var workingAnimAlpha: FUNC = { workingAnimAlphaA * it * it }

    init {
        hasPower = true
    }

    override fun genAniState() {
        IdleState = addAniState("Idle")
        WorkingState = addAniState("Working") {
            Draw.alpha(workingAnimAlpha(it.progress))
            it.workingAnimObj.draw(it.x, it.y)
        }
    }

    override fun genAniConfig() {
        config {
            defaultState(IdleState)
            From(IdleState) To WorkingState When {
                !it.progress.isZero() && !it.power.status.isZero()
            }
            From(WorkingState) To IdleState When {
                it.progress.isZero() || it.power.status.isZero()
            }
            build()
        }
    }

    override fun load() {
        super.load()
        WorkingAnim = this.autoAnim("indicator-light", WorkingAnimFrameNumber, WorkingAnimDuration)
    }

    open inner class ICMachineBuild : AniedCrafter<ICMachine, ICMachineBuild>.AniedCrafterBuild() {
        lateinit var workingAnimObj: AnimationObj
        override fun created() {
            super.created()
            ClientOnly {
                workingAnimObj = WorkingAnim.gen()
                workingAnimObj.tmod {
                    it * (0.1f + Mathf.lerp(
                        progress, progress * 8, 0.5f
                    ))
                }
            }
        }

        override fun draw() {
            workingAnimObj.spend(Time.delta)
            super.draw()
        }
    }
}