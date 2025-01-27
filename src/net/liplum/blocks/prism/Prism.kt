package net.liplum.blocks.prism

import arc.graphics.Color
import arc.graphics.g2d.Draw
import arc.math.Angles
import arc.math.Mathf
import arc.math.geom.Vec2
import arc.struct.EnumSet
import arc.util.Time
import arc.util.io.Reads
import arc.util.io.Writes
import mindustry.content.UnitTypes
import mindustry.entities.TargetPriority
import mindustry.gen.*
import mindustry.graphics.Drawf
import mindustry.graphics.Layer
import mindustry.graphics.Pal
import mindustry.logic.LAccess
import mindustry.logic.Ranged
import mindustry.ui.Bar
import mindustry.world.Block
import mindustry.world.blocks.ControlBlock
import mindustry.world.blocks.defense.turrets.Turret
import mindustry.world.meta.BlockFlag
import mindustry.world.meta.BlockGroup
import net.liplum.ClientOnly
import net.liplum.DebugOnly
import net.liplum.R
import net.liplum.blocks.prism.CrystalManager.Companion.read
import net.liplum.blocks.prism.CrystalManager.Companion.write
import net.liplum.draw
import net.liplum.math.PolarPos
import net.liplum.utils.*
import kotlin.math.abs
import kotlin.math.log2

open class Prism(name: String) : Block(name) {
    var PS: FUNC = quadratic(1.2f, 0.2f)
    /**
     * Above ground level.
     */
    @JvmField var Agl = 20f
    @JvmField var deflectionAngle = 25f
    @JvmField var prismRange = 10f
    @JvmField var prismRevolutionSpeed = 0.05f
    @JvmField var playerControllable = true
    @JvmField var rotateSpeed = 0f
    @JvmField var maxCrystal = 3
    @ClientOnly @JvmField var prismRotationSpeed = 0.05f
    @ClientOnly @JvmField var elevation = -1f
    @ClientOnly lateinit var BaseTR: TR
    @ClientOnly lateinit var CrystalTRs: Array<TR>
    @ClientOnly @JvmField var CrystalVariants = 7
    @ClientOnly @JvmField var clockwiseColor: Color = R.C.prismClockwise
    @ClientOnly @JvmField var antiClockwiseColor: Color = R.C.prismAntiClockwise
    @JvmField var tintBullet = true
    @ClientOnly lateinit var UpTR: TR
    @ClientOnly lateinit var DownTR: TR
    @ClientOnly lateinit var LeftTR: TR
    @ClientOnly lateinit var RightTR: TR
    @ClientOnly lateinit var RightUpStartTR: TR
    @ClientOnly lateinit var RightUpEndTR: TR
    @ClientOnly lateinit var LeftDownStartTR: TR
    @ClientOnly lateinit var LeftDownEndTR: TR

    init {
        absorbLasers = true
        update = true
        solid = true
        outlineIcon = true
        priority = TargetPriority.turret
        group = BlockGroup.turrets
        flags = EnumSet.of(BlockFlag.turret)
        noUpdateDisabled = true
        sync = true
    }

    open val Crystal.color: Color
        get() = if (this.isClockwise)
            clockwiseColor
        else
            antiClockwiseColor

    fun PSA(speed: Float, cur: Float, target: Float, totalLength: Float): Float {
        return speed * PS(abs(target - cur) / totalLength)
    }

    override fun load() {
        super.load()
        BaseTR = this.sub("base")
        CrystalTRs = this.sheet("crystals", CrystalVariants)
        UpTR = this.sub("up")
        LeftTR = this.sub("left")
        DownTR = this.sub("down")
        RightTR = this.sub("right")
        RightUpStartTR = this.sub("rightup-start")
        RightUpEndTR = this.sub("rightup-end")
        LeftDownStartTR = this.sub("leftdown-start")
        LeftDownEndTR = this.sub("leftdown-end")
    }

    var perDeflectionAngle = 0f
    override fun init() {
        super.init()
        perDeflectionAngle = deflectionAngle * 2 / 3
        if (elevation < 0) {
            elevation = size / 2f
        }
        clipSize = Agl + (prismRange * 2 * maxCrystal) + elevation
    }

    override fun setBars() {
        super.setBars()
        bars.add<PrismBuild>(R.Bar.PrismN) {
            Bar(
                {
                    if (it.cm.validAmount > 1)
                        "${it.crystalAmount} ${R.Bar.PrismPl.bundle()}"
                    else
                        "${it.crystalAmount} ${R.Bar.Prism.bundle()}"
                }, AutoRGB,
                { it.crystalAmount.toFloat() / maxCrystal }
            )
        }
        DebugOnly {
            bars.add<PrismBuild>(R.Bar.ProgressN) {
                Bar(
                    { R.Bar.Progress.bundle(it.cm.process) },
                    { Pal.power },
                    { it.cm.process / 1f }
                )
            }
            bars.add<PrismBuild>(R.Bar.StatusN) {
                Bar(
                    { it.cm.status.toString() },
                    { Pal.accent },
                    { 1f }
                )
            }
            bars.add<PrismBuild>("obelisk-count") {
                Bar(
                    { "Obelisk:${it.cm.obeliskCount}" },
                    { Pal.accent },
                    { it.cm.obeliskCount.toFloat() / (maxCrystal - 1) }
                )
            }
        }
    }

    open inner class PrismBuild : Building(), ControlBlock, Ranged {
        @JvmField var cm: CrystalManager = CrystalManager().apply {
            maxAmount = maxCrystal
            prism = this@PrismBuild
            ClientOnly {
                genCrystalImgCallback = {
                    img = CrystalTRs.randomOne()
                }
            }
            addCrystalCallback = {
                revolution = PolarPos(0f, 0f)
                rotation = PolarPos(prismRange, 0f)
                isClockwise = orbitPos % 2 != 0
            }
            for (i in 0 until initCrystalCount) {
                tryAddNew()
            }
        }
        var unit = UnitTypes.block.create(team) as BlockUnitc
        override fun canControl() = playerControllable
        val crystalAmount: Int
            get() = cm.validAmount
        val outer: Prism
            get() = this@Prism
        var logicControlTime = -1f
        val controlByLogic: Boolean
            get() = logicControlTime > 0f
        var logicAngle = 0f
        val realRange: Float
            get() = Agl + (prismRange * 2 * crystalAmount)

        fun removeOutermostPrisel() =
            cm.tryRemoveOutermost()

        override fun onRemoved() =
            cm.unlinkAllObelisks()

        override fun onProximityUpdate() {
            super.onProximityUpdate()
            cm.removeNonexistentObelisk()
            if (cm.canAdd) {
                for (b in proximity) {
                    if (
                        cm.canAdd &&
                        b is Obelisk &&
                        b.canLink(this)
                    ) {
                        cm.tryLink(b)
                    }
                }
            }
            cm.updateObelisk()
        }

        override fun delta(): Float {
            return Time.delta * log2(timeScale + 1f)
        }

        override fun updateTile() {
            cm.spend(delta())
            if (logicControlTime > 0f) {
                logicControlTime -= Time.delta
            }
            val perRevl = prismRevolutionSpeed * delta()
            val perRota = prismRotationSpeed * delta()
            var curPerRelv: Float
            var curPerRota: Float

            cm.update {
                val ip1 = orbitPos + 1
                curPerRelv = perRevl / ip1 * 0.8f
                curPerRota = perRota * ip1 * 0.8f
                if (isControlled) {
                    val ta = PolarPos.toA(unit.aimX() - x, unit.aimY() - y)
                    revolution.a =
                        Angles.moveToward(
                            revolution.a.degree, ta.degree,
                            curPerRelv * 100 * delta()
                        ).radian
                } else if (controlByLogic) {
                    revolution.a =
                        Angles.moveToward(
                            revolution.a.degree, logicAngle,
                            curPerRelv * 100 * delta()
                        ).radian
                } else {
                    revolution.a += if (isClockwise) -curPerRelv else curPerRelv
                }
                var revlR = revolution.r
                var perRevlR = 0.1f * Mathf.log2((ip1 * ip1).toFloat() + 1f)
                val totalLen = Agl + (prismRange * 2 * orbitPos)
                if (isRemoved) {
                    perRevlR = PSA(perRevlR, revlR, 0f, totalLen)
                    revlR -= perRevlR
                } else {
                    perRevlR = PSA(perRevlR, revlR, totalLen, totalLen)
                    revlR += perRevlR
                }
                revlR = revlR.coerceIn(0f, totalLen)
                revolution.r = revlR
                rotation.a += if (isClockwise) -curPerRota else curPerRota
            }
            var priselX: Float
            var priselY: Float
            val realRange = realRange
            val realRangeX2 = realRange * 2
            Groups.bullet.intersect(
                x - realRange,
                y - realRange,
                realRangeX2,
                realRangeX2
            ) {
                cm.update {
                    priselX = revolution.toX() + x
                    priselY = revolution.toY() + y
                    if (it.team == team &&
                        !it.data.isDuplicate &&
                        it.dst(priselX, priselY) <= prismRange
                    ) {
                        it.passThrough()
                    }
                }
            }
        }

        open fun Bullet.passThrough() {
            if (!type.canDispersion) return
            this.setDuplicate()
            val angle = this.rotation()
            val copyRed = this.copy()
            val copyBlue = this.copy()
            val start = angle - deflectionAngle
            copyRed.rotation(start)
            this.rotation(start + perDeflectionAngle)
            copyBlue.rotation(start + perDeflectionAngle * 2)
            if (tintBullet) {
                if (!this.type.isTintIgnored) {
                    val rgbs = tintedRGB(this.type)
                    copyRed.type = rgbs[0]
                    this.type = rgbs[1]
                    copyBlue.type = rgbs[2]
                }
            }
        }

        override fun draw() {
            Draw.rect(BaseTR, x, y)
            val process = cm.process
            Draw.alpha(1f - process)
            Draw.rect(RightUpStartTR, x, y)
            Draw.rect(RightUpStartTR, x, y, 90f)
            Draw.rect(LeftDownStartTR, x, y)
            Draw.rect(LeftDownStartTR, x, y, -90f)
            Draw.alpha(process)
            Draw.rect(RightUpEndTR, x, y)
            Draw.rect(RightUpEndTR, x, y, 90f)
            Draw.rect(LeftDownEndTR, x, y)
            Draw.rect(LeftDownEndTR, x, y, -90f)
            Draw.color()
            val delta = process * G.D(15f)

            Draw.z(Layer.blockOver)
            Draw.rect(UpTR, x, y + delta)
            Draw.rect(DownTR, x, y - delta)
            Draw.rect(LeftTR, x - delta, y)
            Draw.rect(RightTR, x + delta, y)

            Drawf.shadow(UpTR, x, y + delta)
            Drawf.shadow(DownTR, x, y - delta)
            Drawf.shadow(LeftTR, x - delta, y)
            Drawf.shadow(RightTR, x + delta, y)
            var priselX: Float
            var priselY: Float
            cm.render {
                priselX = revolution.toX() + x
                priselY = revolution.toY() + y
                Draw.z(Layer.blockOver)
                Drawf.shadow(
                    img,
                    priselX - elevation * Mathf.log(3f, orbitPos + 3f) * 7f,
                    priselY - elevation * Mathf.log(3f, orbitPos + 3f) * 7f,
                    rotation.a.degree.draw
                )
                Draw.z(Layer.power + 1f)
                Draw.rect(
                    img,
                    priselX,
                    priselY,
                    rotation.a.degree.draw
                )

                DebugOnly {
                    Draw.z(Layer.power - 1f)
                    G.drawDashCircle(priselX, priselY, prismRange, color)
                }
            }
            Draw.reset()
        }

        override fun drawSelect() {
            Draw.z(Layer.turret)
            cm.render {
                G.drawDashCircle(
                    this@PrismBuild,
                    Agl + (prismRange * 2 * orbitPos),
                    color
                )
            }
        }

        override fun unit(): MdtUnit {
            unit.tile(this)
            unit.team(team)
            return (unit as MdtUnit)
        }

        override fun control(type: LAccess, p1: Double, p2: Double, p3: Double, p4: Double) {
            if (type == LAccess.shoot && !unit.isPlayer) {
                logicAngle = toAngle(p1, p2)
                logicControlTime = 60f
            }
            super.control(type, p1, p2, p3, p4)
        }

        open fun findNearestTurret(): Vec2? {
            var pos: Vec2? = null
            Groups.build.each {
                if (it is Turret.TurretBuild && it.dst(this) <= 60f && it.isActive) {
                    pos = Vec2(it.x, it.y)
                    return@each
                }
            }
            return pos
        }

        override fun control(type: LAccess, p1: Any?, p2: Double, p3: Double, p4: Double) {
            if (type == LAccess.shootp && !unit.isPlayer) {
                if (p1 is Posc) {
                    logicControlTime = 60f
                    logicAngle = toAngle(x, y, p1.x, p1.y)
                }
            }
            super.control(type, p1, p2, p3, p4)
        }

        override fun read(read: Reads, revision: Byte) {
            super.read(read, revision)
            cm.read(read)
        }

        override fun write(write: Writes) {
            super.write(write)
            cm.write(write)
        }

        override fun range() = realRange
    }
}