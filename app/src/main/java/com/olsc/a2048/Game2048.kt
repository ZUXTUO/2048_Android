package com.olsc.a2048

import kotlin.random.Random

/**
 * 2048 游戏引擎：4x4 网格、滑动合并、得分、胜负判定。
 * 纯 Kotlin 实现，不依赖 Android，便于单元测试。
 */
class Game2048(private val spawnNewTiles: Boolean = true) {

    companion object {
        const val SIZE = 4
        const val WIN_VALUE = 2048
    }

    /** 移动方向。滑动合并时朝目标方向压缩。 */
    enum class Direction(val dr: Int, val dc: Int) {
        UP(-1, 0), DOWN(1, 0), LEFT(0, -1), RIGHT(0, 1)
    }

    /**
     * 一次滑动产生的一条瓦片动画指令。
     * @param fromRow/fromCol 瓦片移动前的格子
     * @param toRow/toCol 瓦片移动后的格子
     * @param value 瓦片数值
     * @param merge 该瓦片是合并后的幸存者（需要弹跳强调）
     * @param consumed 该瓦片被合并吃掉（移动到目标后消失）
     */
    data class TileAnim(
        val fromRow: Int, val fromCol: Int,
        val toRow: Int, val toCol: Int,
        val value: Int,
        val merge: Boolean = false,
        val consumed: Boolean = false,
    )

    private val random = Random.Default

    /** 当前棋盘，[grid][r][c]，0 表示空。 */
    val grid = Array(SIZE) { IntArray(SIZE) }

    var score = 0
        private set

    var best = 0
        private set

    var gameOver = false
        private set

    var won = false
        private set

    fun reset() {
        for (r in 0 until SIZE) for (c in 0 until SIZE) grid[r][c] = 0
        score = 0
        gameOver = false
        won = false
        addRandomTile()
        addRandomTile()
    }

    /** 在随机空位生成 2（90%）或 4（10%）。返回生成位置，无空位返回 null。 */
    fun addRandomTile(): Pair<Int, Int>? {
        val empty = ArrayList<Pair<Int, Int>>(SIZE * SIZE)
        for (r in 0 until SIZE) for (c in 0 until SIZE) if (grid[r][c] == 0) empty.add(r to c)
        if (empty.isEmpty()) return null
        val (r, c) = empty[random.nextInt(empty.size)]
        grid[r][c] = if (random.nextInt(10) == 0) 4 else 2
        return r to c
    }

    fun setBest(value: Int) {
        best = value
    }

    /** 从存档恢复棋盘（用于 Activity 重建）。 */
    fun restoreState(gridValues: IntArray, savedScore: Int, savedBest: Int) {
        require(gridValues.size == SIZE * SIZE)
        for (r in 0 until SIZE) for (c in 0 until SIZE) {
            grid[r][c] = gridValues[r * SIZE + c]
        }
        score = savedScore
        best = savedBest
        gameOver = !hasAnyMove()
        won = grid.any { row -> row.any { it >= WIN_VALUE } }
    }

    /** 展平的棋盘值，用于存档。 */
    fun flattenedGrid(): IntArray {
        val out = IntArray(SIZE * SIZE)
        for (r in 0 until SIZE) for (c in 0 until SIZE) out[r * SIZE + c] = grid[r][c]
        return out
    }

    /** 一次滑动后的完整结果，供 UI 驱动动画。 */
    data class MoveResult(
        val anims: List<TileAnim>,
        val newTileRow: Int,
        val newTileCol: Int,
        val score: Int,
        val gameOver: Boolean,
        val won: Boolean,
    )

    /**
     * 朝 [direction] 滑动并合并。返回动画指令；若没有任何移动则返回 null。
     */
    fun move(direction: Direction): MoveResult? {
        val anims = ArrayList<TileAnim>()
        var anyMoved = false

        // 按方向把网格投影成若干条"从左到右"的线，统一做左滑合并，再映射回坐标。
        for (i in 0 until SIZE) {
            val cells = ArrayList<Pair<Int, Int>>(SIZE) // 线上的格子坐标
            val values = IntArray(SIZE)
            for (j in 0 until SIZE) {
                val (r, c) = when (direction) {
                    Direction.LEFT -> i to j
                    Direction.RIGHT -> i to (SIZE - 1 - j)
                    Direction.UP -> j to i
                    Direction.DOWN -> (SIZE - 1 - j) to i
                }
                cells.add(r to c)
                values[j] = grid[r][c]
            }

            val result = slideLine(values)
            anyMoved = anyMoved || result.moved

            for (j in 0 until SIZE) {
                val (r, c) = cells[j]
                grid[r][c] = result.line[j]
            }

            // 输出动画指令：幸存者 + 被吃掉的瓦片
            for (k in result.survivors.indices) {
                val s = result.survivors[k]
                val (toR, toC) = cells[k]
                if (s.sourceIndex == k && !s.merged) continue // 没动也没合并

                if (s.merged) {
                    // 幸存者：来自 s.sourceIndex，弹跳
                    val (fromR, fromC) = cells[s.sourceIndex]
                    anims.add(
                        TileAnim(fromR, fromC, toR, toC, s.value, merge = true)
                    )
                    // 被吃掉的瓦片：来自 s.mergedFrom
                    val (cR, cC) = cells[s.mergedFrom]
                    anims.add(
                        TileAnim(cR, cC, toR, toC, s.value / 2, consumed = true)
                    )
                } else {
                    val (fromR, fromC) = cells[s.sourceIndex]
                    anims.add(
                        TileAnim(fromR, fromC, toR, toC, s.value)
                    )
                }
            }
        }

        if (anyMoved) {
            val newTile = if (spawnNewTiles) addRandomTile() else null
            if (score > best) best = score
            gameOver = !hasAnyMove()
            return MoveResult(
                anims,
                newTile?.first ?: -1,
                newTile?.second ?: -1,
                score,
                gameOver,
                won,
            )
        }
        // 无移动时也刷新死局判定：棋盘可能此前已不可移动
        gameOver = !hasAnyMove()
        return null
    }

    /** 判断是否还有任何可移动（空格或相邻相等）。 */
    fun hasAnyMove(): Boolean {
        for (r in 0 until SIZE) for (c in 0 until SIZE) {
            if (grid[r][c] == 0) return true
            if (r + 1 < SIZE && grid[r][c] == grid[r + 1][c]) return true
            if (c + 1 < SIZE && grid[r][c] == grid[r][c + 1]) return true
        }
        return false
    }

    /** 一行的左滑合并结果。 */
    private data class SlideResult(
        val line: IntArray,
        val survivors: List<Survivor>,
        val moved: Boolean,
    )

    private data class Survivor(val sourceIndex: Int, val mergedFrom: Int, val value: Int, val merged: Boolean)

    private fun slideLine(values: IntArray): SlideResult {
        val line = IntArray(SIZE)
        val survivors = ArrayList<Survivor>(SIZE)

        var write = 0
        var read = 0
        var moved = false

        while (read < SIZE) {
            if (values[read] == 0) { read++; continue }
            val v = values[read]

            // 与下一个非零相同则合并
            var next = read + 1
            while (next < SIZE && values[next] == 0) next++
            if (next < SIZE && values[next] == v) {
                val newVal = v * 2
                line[write] = newVal
                survivors.add(Survivor(read, next, newVal, merged = true))
                score += newVal
                if (newVal >= WIN_VALUE) won = true
                moved = true // 合并必然改变棋盘
                write++
                read = next + 1
            } else {
                line[write] = v
                survivors.add(Survivor(read, -1, v, merged = false))
                if (read != write) moved = true
                write++
                read++
            }
        }
        return SlideResult(line, survivors, moved)
    }
}
