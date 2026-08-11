package com.olsc.a2048

import com.olsc.a2048.Game2048.Direction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Game2048Test {

    private fun Game2048.setRow(r: Int, vararg values: Int) {
        for (c in values.indices) grid[r][c] = values[c]
    }

    private fun Game2048.setCol(c: Int, vararg values: Int) {
        for (r in values.indices) grid[r][c] = values[r]
    }

    @Test
    fun `slide left merges and compacts`() {
        val g = Game2048(spawnNewTiles = false)
        g.setRow(0, 2, 2, 4, 0)
        val result = g.move(Direction.LEFT)
        val anims = result!!.anims
        assertEquals(4, g.grid[0][0])
        assertEquals(4, g.grid[0][1])
        assertEquals(0, g.grid[0][2])
        assertEquals(0, g.grid[0][3])
        assertEquals(4, g.score)
        assertTrue(anims.isNotEmpty())
    }

    @Test
    fun `tiles merge only once per move`() {
        val g = Game2048(spawnNewTiles = false)
        g.setRow(0, 2, 2, 2, 2)
        g.move(Direction.LEFT)
        assertEquals(4, g.grid[0][0])
        assertEquals(4, g.grid[0][1])
        assertEquals(0, g.grid[0][2])
        assertEquals(0, g.grid[0][3])
        assertEquals(8, g.score)
    }

    @Test
    fun `no move when nothing can move`() {
        val g = Game2048(spawnNewTiles = false)
        g.setRow(0, 2, 4, 2, 4)
        g.setRow(1, 4, 2, 4, 2)
        g.setRow(2, 2, 4, 2, 4)
        g.setRow(3, 4, 2, 4, 2)
        val result = g.move(Direction.LEFT)
        assertTrue(result == null)
        assertTrue(g.gameOver)
    }

    @Test
    fun `right slide moves toward right edge`() {
        val g = Game2048(spawnNewTiles = false)
        g.setRow(0, 2, 0, 2, 4)
        g.move(Direction.RIGHT)
        assertEquals(0, g.grid[0][0])
        assertEquals(0, g.grid[0][1])
        assertEquals(4, g.grid[0][2])
        assertEquals(4, g.grid[0][3])
        assertEquals(4, g.score)
    }

    @Test
    fun `up slide compacts column`() {
        val g = Game2048(spawnNewTiles = false)
        g.setCol(0, 2, 0, 2, 4)
        g.move(Direction.UP)
        assertEquals(4, g.grid[0][0])
        assertEquals(4, g.grid[1][0])
        assertEquals(0, g.grid[2][0])
        assertEquals(0, g.grid[3][0])
        assertEquals(4, g.score)
    }

    @Test
    fun `down slide compacts column`() {
        val g = Game2048(spawnNewTiles = false)
        g.setCol(0, 2, 0, 2, 4)
        g.move(Direction.DOWN)
        assertEquals(0, g.grid[0][0])
        assertEquals(0, g.grid[1][0])
        assertEquals(4, g.grid[2][0])
        assertEquals(4, g.grid[3][0])
        assertEquals(4, g.score)
    }

    @Test
    fun `gap between equal tiles still merges`() {
        val g = Game2048(spawnNewTiles = false)
        g.setRow(0, 2, 0, 0, 2)
        g.move(Direction.LEFT)
        assertEquals(4, g.grid[0][0])
        assertEquals(0, g.grid[0][1])
        assertEquals(0, g.grid[0][2])
        assertEquals(0, g.grid[0][3])
        assertEquals(4, g.score)
    }

    @Test
    fun `reset spawns two tiles`() {
        val g = Game2048(spawnNewTiles = false)
        g.reset()
        var count = 0
        for (r in 0 until 4) for (c in 0 until 4) if (g.grid[r][c] != 0) count++
        assertEquals(2, count)
        assertFalse(g.gameOver)
    }

    @Test
    fun `restoreState rebuilds grid score and best`() {
        val g = Game2048(spawnNewTiles = false)
        g.setRow(0, 2, 0, 2, 4)
        g.move(Direction.RIGHT)
        val g2 = Game2048(spawnNewTiles = false)
        g2.restoreState(g.flattenedGrid(), g.score, 100)
        assertEquals(4, g2.grid[0][2])
        assertEquals(4, g2.grid[0][3])
        assertEquals(4, g2.score)
        assertEquals(100, g2.best)
        assertFalse(g2.gameOver)
    }

    @Test
    fun `restoreState detects dead grid`() {
        val g = Game2048(spawnNewTiles = false)
        g.setRow(0, 2, 4, 2, 4)
        g.setRow(1, 4, 2, 4, 2)
        g.setRow(2, 2, 4, 2, 4)
        g.setRow(3, 4, 2, 4, 2)
        val g2 = Game2048(spawnNewTiles = false)
        g2.restoreState(g.flattenedGrid(), 0, 0)
        assertTrue(g2.gameOver)
    }

    @Test
    fun `merge anims carry correct coordinates`() {
        val g = Game2048(spawnNewTiles = false)
        g.setRow(0, 2, 2, 0, 0)
        val result = g.move(Direction.LEFT)
        val anims = result!!.anims
        val merge = anims.firstOrNull { it.merge }
        assertTrue(merge != null)
        assertEquals(0, merge!!.fromRow)
        assertEquals(0, merge.fromCol)
        assertEquals(0, merge.toRow)
        assertEquals(0, merge.toCol)
        assertEquals(4, merge.value)
        val consumed = anims.firstOrNull { it.consumed }
        assertTrue(consumed != null)
        assertEquals(0, consumed!!.toRow)
        assertEquals(0, consumed.toCol)
    }
}
