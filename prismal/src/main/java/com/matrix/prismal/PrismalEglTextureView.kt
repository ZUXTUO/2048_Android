package com.matrix.prismal

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLSurfaceView
import android.os.Handler
import android.os.HandlerThread
import android.util.AttributeSet
import android.util.Log
import android.view.TextureView

/**
 * 用 TextureView 承载 OpenGL 渲染，替代 GLSurfaceView。
 *
 * 原因：在部分 ROM（如 MIUI）上，GLSurfaceView 的 SurfaceView 表面即使渲染正常
 * （glReadPixels 能读出彩色），也无法被窗口合成器显示出来，表现为一片纯黑。
 * TextureView 走普通 View 的合成管线，可彻底规避该兼容问题，且支持透明。
 *
 * 接口对齐 GLSurfaceView：setRenderer / setRenderMode / queueEvent / requestRender。
 */
class PrismalEglTextureView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : TextureView(context, attrs) {

    private var glRenderer: GLSurfaceView.Renderer? = null
    private var continuous = false

    private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE
    private var surfaceReady = false
    private var viewW = 1
    private var viewH = 1
    private var renderPending = false

    private val renderThread: HandlerThread = HandlerThread("prismal-gl").apply { start() }
    private val renderHandler: Handler = Handler(renderThread.looper)

    init {
        surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                viewW = width.coerceAtLeast(1)
                viewH = height.coerceAtLeast(1)
                renderHandler.post { initGL() }
            }

            override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
                viewW = width.coerceAtLeast(1)
                viewH = height.coerceAtLeast(1)
                renderHandler.post {
                    if (surfaceReady) {
                        makeCurrent()
                        glRenderer?.onSurfaceChanged(null, viewW, viewH)
                        requestRenderInternal()
                    }
                }
            }

            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                renderHandler.post { destroyGL() }
                return true
            }

            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
        }
    }

    // ---------- 与 GLSurfaceView 对齐的接口 ----------

    fun setRenderer(renderer: GLSurfaceView.Renderer) {
        glRenderer = renderer
    }

    fun setRenderMode(mode: Int) {
        continuous = mode == GLSurfaceView.RENDERMODE_CONTINUOUSLY
        if (continuous) renderHandler.post { renderLoop() }
    }

    /** 在 GL 线程执行命令（对齐 GLSurfaceView.queueEvent 语义：执行后可触发一次渲染）。 */
    fun queueEvent(runnable: Runnable) {
        renderHandler.post {
            runnable.run()
            if (!continuous) requestRenderInternal()
        }
    }

    fun requestRender() {
        renderHandler.post { requestRenderInternal() }
    }

    // ---------- 内部 GL 生命周期 ----------

    private fun initGL() {
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (eglDisplay == EGL14.EGL_NO_DISPLAY) {
            Log.e(TAG, "eglGetDisplay failed")
            return
        }
        val version = IntArray(2)
        if (!EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) {
            Log.e(TAG, "eglInitialize failed")
            return
        }
        val attribs = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_DEPTH_SIZE, 16,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_NONE,
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        if (!EGL14.eglChooseConfig(eglDisplay, attribs, 0, configs, 0, 1, numConfigs, 0) || numConfigs[0] == 0) {
            Log.e(TAG, "eglChooseConfig failed")
            return
        }
        val contextAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
        eglContext = EGL14.eglCreateContext(eglDisplay, configs[0], EGL14.EGL_NO_CONTEXT, contextAttribs, 0)
        if (eglContext == EGL14.EGL_NO_CONTEXT) {
            Log.e(TAG, "eglCreateContext failed")
            return
        }
        val surfaceAttribs = intArrayOf(EGL14.EGL_NONE)
        // 明确 buffer 尺寸，避免 SurfaceTexture 默认小 buffer 导致显示异常
        surfaceTexture?.setDefaultBufferSize(viewW, viewH)
        eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, configs[0], surfaceTexture, surfaceAttribs, 0)
        if (eglSurface == EGL14.EGL_NO_SURFACE) {
            Log.e(TAG, "eglCreateWindowSurface failed")
            return
        }
        if (!makeCurrent()) {
            Log.e(TAG, "eglMakeCurrent failed")
            return
        }
        surfaceReady = true
        glRenderer?.onSurfaceCreated(null, null)
        glRenderer?.onSurfaceChanged(null, viewW, viewH)
        requestRenderInternal()
    }

    private fun makeCurrent(): Boolean =
        EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)

    private fun requestRenderInternal() {
        if (!surfaceReady || renderPending) return
        renderPending = true
        renderHandler.post {
            renderPending = false
            drawFrame()
        }
    }

    private fun renderLoop() {
        if (!continuous || !surfaceReady) return
        drawFrame()
        renderHandler.postDelayed({ renderLoop() }, 16L)
    }

    private fun drawFrame() {
        if (!surfaceReady) return
        if (!makeCurrent()) return
        glRenderer?.onDrawFrame(null)
        EGL14.eglSwapBuffers(eglDisplay, eglSurface)
        // 通知 TextureView 重绘以显示新帧
        if (!continuous) postInvalidate()
    }

    private fun destroyGL() {
        if (!surfaceReady) return
        EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
        EGL14.eglDestroySurface(eglDisplay, eglSurface)
        EGL14.eglDestroyContext(eglDisplay, eglContext)
        EGL14.eglTerminate(eglDisplay)
        eglSurface = EGL14.EGL_NO_SURFACE
        eglContext = EGL14.EGL_NO_CONTEXT
        eglDisplay = EGL14.EGL_NO_DISPLAY
        surfaceReady = false
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        renderHandler.post { destroyGL() }
    }

    private companion object {
        const val TAG = "PrismalEglView"
    }
}
