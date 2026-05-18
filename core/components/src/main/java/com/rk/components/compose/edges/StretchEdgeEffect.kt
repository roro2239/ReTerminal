package com.rk.components.compose.edges

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.view.animation.AnimationUtils
import androidx.annotation.IntDef
import kotlin.math.E
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

class StretchEdgeEffect : EdgeEffectCompat {
    private var mDistance = 0f
    private var mVelocity = 0f
    private var mStartTime = 0L
    private var mState = STATE_IDLE
    private var mPullDistance = 0f
    private var mWidth = 0f
    private var mHeight = 0f
    private var mInvalidate = EMPTY_RUNNABLE
    private var mPostInvalidateOnAnimation = EMPTY_RUNNABLE
    private val mTmpOut = FloatArray(5)

    constructor(context: Context) : super(context)

    constructor(context: Context, invalidate: Runnable, postInvalidateOnAnimation: Runnable) : this(context) {
        mInvalidate = invalidate
        mPostInvalidateOnAnimation = postInvalidateOnAnimation
    }

    fun setOnInvalidate(invalidate: Runnable) {
        mInvalidate = invalidate
    }

    fun setPostInvalidateOnAnimation(postInvalidateOnAnimation: Runnable) {
        mPostInvalidateOnAnimation = postInvalidateOnAnimation
    }

    @EdgeEffectType
    private fun getCurrentEdgeEffectBehavior(): Int {
        return if (!ValueAnimator.areAnimatorsEnabled()) TYPE_NONE else TYPE_STRETCH
    }

    override fun setSize(width: Int, height: Int) {
        mWidth = width.toFloat()
        mHeight = height.toFloat()
    }

    override fun isFinished(): Boolean {
        return mState == STATE_IDLE
    }

    override fun finish() {
        mState = STATE_IDLE
        mDistance = 0f
        mVelocity = 0f
    }

    private fun invalidateIfNotFinished() {
        if (!isFinished) {
            mInvalidate.run()
        }
    }

    override fun onPull(deltaDistance: Float) {
        onPull(deltaDistance, 0.5f)
    }

    override fun onPull(deltaDistance: Float, displacement: Float) {
        val edgeEffectBehavior = getCurrentEdgeEffectBehavior()
        if (edgeEffectBehavior == TYPE_NONE) {
            finish()
            return
        }
        val now = AnimationUtils.currentAnimationTimeMillis()
        if (mState != STATE_PULL) {
            mPullDistance = mDistance
        }
        mState = STATE_PULL
        mStartTime = now
        mPullDistance += deltaDistance
        mPullDistance = min(1f, mPullDistance)
        mDistance = max(0f, mPullDistance)
        mVelocity = 0f
        if (mDistance == 0f) {
            mState = STATE_IDLE
        }
        invalidateIfNotFinished()
    }

    override fun onPullDistance(deltaDistance: Float, displacement: Float): Float {
        val edgeEffectBehavior = getCurrentEdgeEffectBehavior()
        if (edgeEffectBehavior == TYPE_NONE) {
            return 0f
        }
        val finalDistance = max(0f, deltaDistance + mDistance)
        val delta = finalDistance - mDistance
        if (delta == 0f && mDistance == 0f) {
            return 0f
        }
        onPull(delta, displacement)
        return delta
    }

    override fun getDistance(): Float {
        return mDistance
    }

    override fun onRelease() {
        mPullDistance = 0f
        if (mState != STATE_PULL && mState != STATE_PULL_DECAY) {
            return
        }
        mState = STATE_RECEDE
        mVelocity = 0f
        mStartTime = AnimationUtils.currentAnimationTimeMillis()
        invalidateIfNotFinished()
    }

    override fun onAbsorb(velocity: Int) {
        val edgeEffectBehavior = getCurrentEdgeEffectBehavior()
        if (edgeEffectBehavior == TYPE_STRETCH) {
            mState = STATE_RECEDE
            mVelocity = velocity * ON_ABSORB_VELOCITY_ADJUSTMENT
            mStartTime = AnimationUtils.currentAnimationTimeMillis()
            invalidateIfNotFinished()
        } else {
            finish()
        }
    }

    fun applyStretch(canvas: Canvas, @EdgeEffectPosition position: Int) {
        applyStretch(canvas, position, 0, 0)
    }

    fun applyStretch(canvas: Canvas, @EdgeEffectPosition position: Int, translationX: Int, translationY: Int) {
        mTmpOut[0] = 0f
        getScale(mTmpOut, position)
        if (mTmpOut[0] == 1f) {
            canvas.scale(mTmpOut[1], mTmpOut[2], mTmpOut[3] - translationX, mTmpOut[4] - translationY)
        }
    }

    fun getScale(out: FloatArray, @EdgeEffectPosition position: Int) {
        val edgeEffectBehavior = getCurrentEdgeEffectBehavior()
        if (edgeEffectBehavior == TYPE_STRETCH) {
            if (mState == STATE_RECEDE) {
                updateSpring()
            }
            if (mDistance != 0f) {
                val vec = dampStretchVector(max(-1f, min(1f, mDistance)))
                val scale = 1f + vec
                out[0] = 1f
                when (position) {
                    POSITION_TOP -> {
                        out[1] = 1f
                        out[2] = scale
                        out[3] = 0f
                        out[4] = 0f
                    }
                    POSITION_BOTTOM -> {
                        out[1] = 1f
                        out[2] = scale
                        out[3] = 0f
                        out[4] = mHeight
                    }
                    POSITION_LEFT -> {
                        out[1] = scale
                        out[2] = 1f
                        out[3] = 0f
                        out[4] = 0f
                    }
                    POSITION_RIGHT -> {
                        out[1] = scale
                        out[2] = 1f
                        out[3] = mWidth
                        out[4] = 0f
                    }
                }
            }
        } else {
            mState = STATE_IDLE
            mDistance = 0f
            mVelocity = 0f
        }

        var oneLastFrame = false
        if (mState == STATE_RECEDE && mDistance == 0f && mVelocity == 0f) {
            mState = STATE_IDLE
            oneLastFrame = true
        }
        if (mState != STATE_IDLE || oneLastFrame) {
            mPostInvalidateOnAnimation.run()
        }
    }

    override fun draw(canvas: Canvas): Boolean {
        return false
    }

    override fun getMaxHeight(): Int {
        return mHeight.toInt()
    }

    private fun updateSpring() {
        val time = AnimationUtils.currentAnimationTimeMillis()
        val deltaT = (time - mStartTime) / 1000f
        if (deltaT < 0.001f) {
            return
        }
        mStartTime = time

        if (
            abs(mVelocity) <= LINEAR_VELOCITY_TAKE_OVER &&
            abs(mDistance * mHeight) < LINEAR_DISTANCE_TAKE_OVER &&
            sign(mVelocity) == -sign(mDistance)
        ) {
            mVelocity = sign(mVelocity) * LINEAR_VELOCITY_TAKE_OVER
            val targetDistance = mDistance + mVelocity * deltaT / mHeight
            if (sign(targetDistance) != sign(mDistance)) {
                mDistance = 0f
                mVelocity = 0f
            } else {
                mDistance = targetDistance
            }
            return
        }

        val dampedFreq = NATURAL_FREQUENCY * sqrt(1 - DAMPING_RATIO * DAMPING_RATIO)
        val cosCoeff = (mDistance * mHeight).toDouble()
        val sinCoeff = (1 / dampedFreq) * (DAMPING_RATIO * NATURAL_FREQUENCY * mDistance * mHeight + mVelocity)
        val distance = E.pow(-DAMPING_RATIO * NATURAL_FREQUENCY * deltaT) *
            (cosCoeff * cos(dampedFreq * deltaT) + sinCoeff * sin(dampedFreq * deltaT))
        val velocity = distance * -NATURAL_FREQUENCY * DAMPING_RATIO +
            E.pow(-DAMPING_RATIO * NATURAL_FREQUENCY * deltaT) *
            (-dampedFreq * cosCoeff * sin(dampedFreq * deltaT) + dampedFreq * sinCoeff * cos(dampedFreq * deltaT))
        mDistance = distance.toFloat() / mHeight
        mVelocity = velocity.toFloat()
        if (mDistance > 1f) {
            mDistance = 1f
            mVelocity = 0f
        }
        if (isAtEquilibrium()) {
            mDistance = 0f
            mVelocity = 0f
        }
    }

    private fun isAtEquilibrium(): Boolean {
        val displacement = (mDistance * mHeight).toDouble()
        val velocity = mVelocity.toDouble()
        return displacement < 0 || abs(velocity) < VELOCITY_THRESHOLD && displacement < VALUE_THRESHOLD
    }

    private fun dampStretchVector(normalizedVec: Float): Float {
        val sign = if (normalizedVec > 0) 1f else -1f
        val overscroll = abs(normalizedVec)
        val linearIntensity = LINEAR_STRETCH_INTENSITY * overscroll
        val scalar = E / SCROLL_DIST_AFFECTED_BY_EXP_STRETCH
        val expIntensity = EXP_STRETCH_INTENSITY * (1 - exp(-overscroll * scalar))
        return sign * (linearIntensity + expIntensity).toFloat()
    }

    @IntDef(TYPE_NONE, TYPE_STRETCH)
    @Retention(AnnotationRetention.SOURCE)
    annotation class EdgeEffectType

    @IntDef(POSITION_TOP, POSITION_BOTTOM, POSITION_LEFT, POSITION_RIGHT)
    @Retention(AnnotationRetention.SOURCE)
    annotation class EdgeEffectPosition

    companion object {
        private const val TYPE_NONE = -1
        private const val TYPE_STRETCH = 1
        private const val VELOCITY_THRESHOLD = 0.01
        private const val LINEAR_VELOCITY_TAKE_OVER = 200f
        private const val VALUE_THRESHOLD = 0.001
        private const val LINEAR_DISTANCE_TAKE_OVER = 8.0
        private const val NATURAL_FREQUENCY = 24.657
        private const val DAMPING_RATIO = 0.98
        private const val ON_ABSORB_VELOCITY_ADJUSTMENT = 13f
        private const val LINEAR_STRETCH_INTENSITY = 0.016f
        private const val EXP_STRETCH_INTENSITY = 0.016f
        private const val SCROLL_DIST_AFFECTED_BY_EXP_STRETCH = 0.33f
        private const val STATE_IDLE = 0
        private const val STATE_PULL = 1
        private const val STATE_RECEDE = 3
        private const val STATE_PULL_DECAY = 4

        const val POSITION_TOP = 0
        const val POSITION_BOTTOM = 1
        const val POSITION_LEFT = 2
        const val POSITION_RIGHT = 3

        private val EMPTY_RUNNABLE = Runnable {}
    }
}
