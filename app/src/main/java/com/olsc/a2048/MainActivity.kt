package com.olsc.a2048

import android.animation.ValueAnimator
import android.os.Bundle
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity(), BoardView.Listener {

    private lateinit var board: BoardView
    private lateinit var boardGlass: GlassCardView
    private lateinit var scoreCard: GlassCardView
    private lateinit var bestCard: GlassCardView
    private lateinit var newGameBtn: GlassCardView
    private lateinit var scoreValue: TextView
    private lateinit var bestValue: TextView
    private lateinit var backdrop: GradientBackgroundView
    private lateinit var overlay: FrameLayout
    private lateinit var overlayCard: GlassCardView
    private lateinit var overlayTitle: TextView
    private lateinit var overlayMessage: TextView
    private lateinit var overlayPrimaryBtn: GlassCardView
    private lateinit var overlayPrimaryText: TextView
    private lateinit var overlaySecondaryBtn: GlassCardView

    private val prefs by lazy { getSharedPreferences("a2048", MODE_PRIVATE) }
    private var lastBest = 0
    private var wonAnnounced = false

    private companion object {
        const val KEY_GRID = "grid"
        const val KEY_SCORE = "score"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        board = findViewById(R.id.board)
        boardGlass = findViewById(R.id.boardGlass)
        scoreCard = findViewById(R.id.scoreCard)
        bestCard = findViewById(R.id.bestCard)
        newGameBtn = findViewById(R.id.newGameBtn)
        scoreValue = findViewById(R.id.scoreValue)
        bestValue = findViewById(R.id.bestValue)
        backdrop = findViewById(R.id.backdrop)
        overlay = findViewById(R.id.overlay)
        overlayCard = findViewById(R.id.overlayCard)
        overlayTitle = findViewById(R.id.overlayTitle)
        overlayMessage = findViewById(R.id.overlayMessage)
        overlayPrimaryBtn = findViewById(R.id.overlayPrimaryBtn)
        overlayPrimaryText = findViewById(R.id.overlayPrimaryText)
        overlaySecondaryBtn = findViewById(R.id.overlaySecondaryBtn)

        setupGlassCards()
        setupButtons()

        lastBest = prefs.getInt("best", 0)
        board.listener = this
        board.initBest(lastBest)

        // 等待棋盘完成首次布局后再建瓦片：此时 tileSize 就绪、瓦片已 attach 到窗口，
        // 弹出动画能正常执行，避免瓦片保持透明导致空棋盘
        val restore = savedInstanceState
        board.post {
            if (restore != null) {
                // 系统重建：恢复临时状态
                board.restoreState(restore)
                wonAnnounced = board.isWon
            } else {
                // 常规启动：优先恢复上次自动保存的棋局，没有存档则开新局
                val savedGrid = prefs.getString(KEY_GRID, null)
                val values = savedGrid?.split(',')?.mapNotNull { it.toIntOrNull() }?.toIntArray()
                if (values != null && values.size == Game2048.SIZE * Game2048.SIZE) {
                    val savedScore = prefs.getInt(KEY_SCORE, 0)
                    board.restoreGrid(values, savedScore, lastBest.coerceAtLeast(savedScore))
                    wonAnnounced = board.isWon
                } else {
                    board.newGame()
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        saveGameState()
    }

    override fun onStop() {
        super.onStop()
        saveGameState()
    }

    /** 把当前棋局写入 SharedPreferences，下次启动自动恢复。 */
    private fun saveGameState() {
        if (!::board.isInitialized) return
        prefs.edit()
            .putString(KEY_GRID, board.gridValues.joinToString(","))
            .putInt(KEY_SCORE, board.scoreNow)
            .putInt("best", lastBest)
            .apply()
    }

    // ---------- 玻璃卡片 ----------

    private fun setupGlassCards() {
        // 棋盘面板：大圆角 + 内部折射光晕
        boardGlass.cornerRadius = 22f
        boardGlass.showInnerGlow = true
        // 得分/最佳卡与按钮：小圆角
        listOf(scoreCard, bestCard, newGameBtn).forEach { it.cornerRadius = 16f }
        overlayCard.cornerRadius = 28f
        overlayCard.showInnerGlow = true
        listOf(overlayPrimaryBtn, overlaySecondaryBtn).forEach { it.cornerRadius = 20f }
    }

    private fun setupButtons() {
        pressable(newGameBtn) {
            hideOverlay()
            board.newGame()
        }
        pressable(overlayPrimaryBtn) {
            // 具体行为由 showOverlay 传入的 onPrimary 决定，见 onGameOver/onWon
        }
        pressable(overlaySecondaryBtn) {
            hideOverlay()
        }
    }

    /** 玻璃按钮：按下轻微缩放 + 松手回调。 */
    private fun pressable(btn: GlassCardView, onClick: () -> Unit) {
        btn.setOnClickListener { onClick() }
        btn.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.animate().scaleX(0.94f).scaleY(0.94f).setDuration(90L).start()
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.animate().scaleX(1f).scaleY(1f).setDuration(140L).start()
                    if (event.actionMasked == MotionEvent.ACTION_UP) v.performClick()
                }
            }
            true
        }
    }

    // ---------- BoardView.Listener ----------

    override fun onScoreChanged(score: Int, best: Int) {
        animateNumber(scoreValue, score)
        animateNumber(bestValue, best)
        if (best > lastBest) {
            lastBest = best
            prefs.edit().putInt("best", best).apply()
        }
        // 实时存档：每步移动后即写入，进程被强杀也不丢档
        saveGameState()
    }

    override fun onGameOver(score: Int) {
        wonAnnounced = false
        showOverlay(
            title = getString(R.string.game_over),
            message = getString(R.string.game_over_hint),
            primaryText = getString(R.string.play_again),
            showSecondary = false,
        ) {
            board.newGame()
        }
    }

    override fun onWon(score: Int) {
        if (wonAnnounced) return
        wonAnnounced = true
        showOverlay(
            title = getString(R.string.you_won),
            message = getString(R.string.you_won_hint),
            primaryText = getString(R.string.keep_going),
            showSecondary = true,
        ) {
            hideOverlay()
        }
    }

    // ---------- 遮罩与动画 ----------

    private fun showOverlay(
        title: String,
        message: String,
        primaryText: String,
        showSecondary: Boolean,
        onPrimary: () -> Unit,
    ) {
        overlayTitle.text = title
        overlayMessage.text = message
        overlayPrimaryText.text = primaryText
        overlaySecondaryBtn.visibility = if (showSecondary) View.VISIBLE else View.GONE
        overlayPrimaryBtn.setOnClickListener {
            hideOverlay()
            onPrimary()
        }

        overlay.alpha = 0f
        overlay.visibility = View.VISIBLE
        overlay.animate().alpha(1f).setDuration(220L).start()

        overlayCard.scaleX = 0.82f
        overlayCard.scaleY = 0.82f
        overlayCard.animate()
            .scaleX(1f).scaleY(1f)
            .setDuration(340L)
            .setInterpolator(OvershootInterpolator(1.35f))
            .start()
    }

    private fun hideOverlay() {
        overlay.animate()
            .alpha(0f)
            .setDuration(180L)
            .withEndAction { overlay.visibility = View.GONE }
            .start()
    }

    private var scoreAnimator: ValueAnimator? = null
    private var bestAnimator: ValueAnimator? = null

    private fun animateNumber(textView: TextView, target: Int) {
        val old = textView.text.toString().toIntOrNull() ?: 0
        if (old == target) return
        val animator = ValueAnimator.ofInt(old, target).apply {
            duration = 320L
            interpolator = DecelerateInterpolator(1.4f)
            addUpdateListener { textView.text = it.animatedValue.toString() }
        }
        // 快速连滑时取消前一个动画，避免中间值互相覆盖导致回跳闪烁
        if (textView === scoreValue) scoreAnimator?.cancel() else bestAnimator?.cancel()
        if (textView === scoreValue) scoreAnimator = animator else bestAnimator = animator
        animator.start()
    }

    // ---------- 状态保存 ----------

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        board.saveState(outState)
    }

    private fun dp(v: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)
}
