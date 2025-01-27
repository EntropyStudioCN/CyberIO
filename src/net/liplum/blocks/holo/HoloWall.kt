package net.liplum.blocks.holo

import arc.Events
import arc.graphics.Blending
import arc.graphics.g2d.Draw
import arc.graphics.g2d.Fill
import arc.graphics.g2d.TextureRegion
import arc.math.Mathf
import arc.util.Time
import arc.util.io.Reads
import arc.util.io.Writes
import mindustry.Vars
import mindustry.game.EventType
import mindustry.gen.Bullet
import mindustry.gen.Call
import mindustry.graphics.Drawf
import mindustry.graphics.Layer
import mindustry.graphics.Pal
import mindustry.ui.Bar
import mindustry.world.blocks.defense.Wall
import net.liplum.ClientOnly
import net.liplum.DebugOnly
import net.liplum.R
import net.liplum.lib.animations.Floating
import net.liplum.api.holo.IHoloEntity
import net.liplum.lib.shaders.use
import net.liplum.registries.CioShaders
import net.liplum.seconds
import net.liplum.utils.*

open class HoloWall(name: String) : Wall(name) {
    @JvmField var restoreReload = 10 * 60f
    @ClientOnly lateinit var BaseTR: TR
    @ClientOnly lateinit var ImageTR: TR
    @ClientOnly lateinit var DyedImageTR: TR
    @JvmField var minHealthProportion = 0.05f
    //@JvmField var maxSleepyTime = 30 * 60f
    @ClientOnly @JvmField var FloatingRange = 0.6f

    init {
        solid = false
        solidifes = true
        canOverdrive = true
        update = true
        hasShadow = false
        absorbLasers = true
        flashHit = false
        floating = true
        sync = true
    }

    override fun load() {
        super.load()
        BaseTR = this.sub("base")
        ImageTR = this.sub("image")
        DyedImageTR = this.sub("dyed-image") or ImageTR
    }

    override fun icons(): Array<TextureRegion> {
        return arrayOf(BaseTR, DyedImageTR)
    }

    override fun setBars() {
        super.setBars()
        DebugOnly {
            bars.add<HoloBuild>(R.Bar.IsProjectingN) {
                Bar(
                    { R.Bar.IsProjecting.bundle(it.isProjecting.yesNo()) },
                    { Pal.bar },
                    { if (it.isProjecting) 1f else 0f }
                )
            }
            bars.add<HoloBuild>(R.Bar.RestRestoreN) {
                Bar(
                    { R.Bar.RestRestore.bundle(it.restRestore.toInt()) },
                    { Pal.bar },
                    { it.restRestore / it.maxHealth }
                )
            }
            bars.add<HoloBuild>(R.Bar.ChargeN) {
                Bar(
                    { R.Bar.Charge.bundle(it.restoreCharge.seconds) },
                    { Pal.power },
                    { it.restoreCharge / restoreReload }
                )
            }
            bars.add<HoloBuild>(R.Bar.LastDamagedN) {
                Bar(
                    { R.Bar.LastDamaged.bundle(it.lastDamagedTime.seconds) },
                    { Pal.power },
                    { it.lastDamagedTime / restoreReload }
                )
            }
        }
    }

    open inner class HoloBuild : WallBuild(), IHoloEntity {
        var restoreCharge = restoreReload
        open val isProjecting: Boolean
            get() = health > maxHealth * minHealthProportion
        open var restRestore = 0f
            set(value) {
                field = value.coerceAtLeast(0f)
            }
        open var lastDamagedTime = restoreReload
        @ClientOnly @JvmField
        var floating: Floating = Floating(FloatingRange).randomXY().changeRate(1)
        override fun collide(other: Bullet): Boolean {
            return isProjecting
        }

        open val canRestructure: Boolean
            get() = lastDamagedTime > restoreReload || !isProjecting

        override fun killThoroughly() {
            kill()
        }

        override fun damage(damage: Float) {
            if (!this.dead()) {
                val dm = Vars.state.rules.blockHealth(team)
                var d = damage
                if (dm.isZero()) {
                    d = this.health + 1.0f
                } else {
                    d /= dm
                }
                d = handleDamage(d)
                val restHealth = (health - d).coerceAtLeast(maxHealth * minHealthProportion)
                Call.tileDamage(this, restHealth)
                lastDamagedTime = 0f
                //noSleep()
            }
        }

        override fun draw() {
            Draw.z(Layer.blockUnder)
            Drawf.shadow(x, y, 10f)
            Draw.z(Layer.block)
            Draw.rect(BaseTR, x, y)
            updateFloating()
            if (isProjecting) {
                CioShaders.Hologram.use(Layer.power) {
                    val healthPct = healthPct
                    it.alpha = healthPct / 4f * 3f
                    it.opacityNoise *= 2f - healthPct
                    it.flickering = it.DefaultFlickering + (1f - healthPct)
                    it.blendHoloColorOpacity = 0f
                    Draw.color(R.C.Holo)
                    Draw.rect(
                        ImageTR,
                        x + floating.xOffset,
                        y + floating.yOffset
                    )
                    Draw.reset()
                }

                if (flashHit) {
                    if (hit < 0.0001f) return
                    Draw.color(flashColor)
                    Draw.alpha(hit * 0.5f * healthPct)
                    Draw.blend(Blending.additive)
                    Fill.rect(x, y, (Vars.tilesize * size).toFloat(), (Vars.tilesize * size).toFloat())
                    Draw.blend()
                    Draw.reset()
                    if (!Vars.state.isPaused) {
                        hit = Mathf.clamp(hit - Time.delta / 10f)
                    }
                }
            }
            Draw.reset()
        }
        @ClientOnly
        open fun updateFloating() {
            val d = G.D(0.1f * FloatingRange * delta() * (2f - healthPct))
            floating.move(d)
        }

        open val canRestore: Boolean
            get() = !isProjecting || health < maxHealth
        open val isRecovering: Boolean
            get() = restRestore > 0.5f

        override fun updateTile() {
            lastDamagedTime += delta()
            if (restoreCharge < restoreReload && !isRecovering && canRestructure) {
                restoreCharge += delta()
            }
            if (isRecovering) {
                val restored = if (restRestore <= maxHealth * minHealthProportion)
                    restRestore
                else
                    restRestore * delta() * 0.01f
                health = health.coerceAtLeast(0f)
                heal(restored)
                restRestore -= restored
            }

            if (canRestore && restoreCharge >= restoreReload) {
                restoreCharge = 0f
                if (health != maxHealth) {
                    dead = false
                    restRestore = maxHealth
                }
            }
            /*if (!isRecovering &&
                !canRestore &&
                restoreCharge >= restoreReload &&
                lastDamagedTime >= maxSleepyTime
            ) {
                sleep()
            }*/
        }

        override fun drawCracks() {
        }

        override fun checkSolid(): Boolean {
            return isProjecting
        }

        override fun write(write: Writes) {
            super.write(write)
            write.f(restoreCharge)
            write.f(restRestore)
            write.f(lastDamagedTime)
        }

        override fun read(read: Reads, revision: Byte) {
            super.read(read, revision)
            restoreCharge = read.f()
            restRestore = read.f()
            lastDamagedTime = read.f()
        }

        val blockType: HoloWall
            get() = this@HoloWall
    }

    companion object {
        @JvmStatic
        fun registerInitHealthHandler() {
            Events.on(EventType.BlockBuildEndEvent::class.java) {
                val hb = it.tile.build as? HoloBuild
                if (hb != null) {
                    hb.health = hb.maxHealth * hb.blockType.minHealthProportion * 0.9f
                }
            }
        }
    }
}