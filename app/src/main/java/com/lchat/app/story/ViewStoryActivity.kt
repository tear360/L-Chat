package com.lchat.app.story

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.setPadding
import com.lchat.app.databinding.ActivityViewStoryBinding
import com.lchat.app.model.StoryItem
import com.lchat.app.util.AppUtils
import com.lchat.app.util.ImageStore
import java.io.Serializable

class ViewStoryActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_STORIES = "stories"
        const val EXTRA_FROM_NAME = "fromName"
        const val EXTRA_FROM_UID = "fromUid"
        private const val STORY_DURATION = 5000L
    }

    private lateinit var binding: ActivityViewStoryBinding
    private var stories: ArrayList<StoryItem> = ArrayList()
    private var index = 0
    private var animator: ValueAnimator? = null
    private var segments = ArrayList<View>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityViewStoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        @Suppress("UNCHECKED_CAST")
        stories = (intent.getSerializableExtra(EXTRA_STORIES) as? ArrayList<StoryItem>)
            ?: ArrayList()
        index = intent.getIntExtra("index", 0)

        if (stories.isEmpty()) {
            finish()
            return
        }

        val fromUid = intent.getStringExtra(EXTRA_FROM_UID) ?: stories.first().fromUid
        val fromName = intent.getStringExtra(EXTRA_FROM_NAME) ?: stories.first().fromName

        val color = AppUtils.avatarColor(fromUid)
        binding.avatarBg.background = AppUtils.circle(color)
        binding.avatarInitials.text = AppUtils.initials(fromName)
        binding.avatarInitials.setTextColor(AppUtils.initialsTextColor(color))

        buildSegments()
        binding.closeButton.setOnClickListener { finish() }
        binding.nextZone.setOnClickListener { next() }
        binding.prevZone.setOnClickListener { previous() }

        render()
    }

    override fun onPause() {
        super.onPause()
        cancelTimer()
    }

    override fun onResume() {
        super.onResume()
        if (index in stories.indices && (animator == null || animator?.isStarted != true)) {
            startSegmentAnimation()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cancelTimer()
    }

    private fun cancelTimer() {
        animator?.removeAllListeners()
        animator?.cancel()
    }

    private fun buildSegments() {
        binding.segments.removeAllViews()
        segments.clear()
        val margin = (4 * resources.displayMetrics.density).toInt().coerceAtLeast(1)
        val height = (3 * resources.displayMetrics.density).toInt().coerceAtLeast(2)
        for (i in stories.indices) {
            val view = View(this)
            val params = LinearLayout.LayoutParams(0, height, 1f)
            params.marginStart = margin
            params.marginEnd = margin
            view.layoutParams = params
            view.setBackgroundColor(0x4DFFFFFF)
            binding.segments.addView(view)
            segments.add(view)
        }
    }

    private fun render() {
        if (index >= stories.size) {
            finish()
            return
        }

        val story = stories[index]
        markSeen(story.id)

        binding.storyUsername.text = story.fromName
        binding.storyTime.text = AppUtils.formatTime(story.createdAt)

        if (story.caption.isNotEmpty()) {
            binding.storyCaption.text = story.caption
            binding.storyCaption.visibility = View.VISIBLE
        } else {
            binding.storyCaption.visibility = View.GONE
        }

        binding.imageProgress.visibility = View.VISIBLE
        val bitmap = ImageStore.decode(story.imageData)
        if (bitmap != null) {
            binding.storyImage.setImageBitmap(bitmap)
        }
        binding.imageProgress.visibility = View.GONE

        startSegmentAnimation()
    }

    private fun startSegmentAnimation() {
        cancelTimer()

        for (i in segments.indices) {
            val segment = segments[i]
            when {
                i < index -> {
                    segment.setBackgroundColor(0xFFFFFFFF.toInt())
                    segment.scaleX = 1f
                }
                i == index -> {
                    segment.setBackgroundColor(0xFFFFFFFF.toInt())
                    segment.scaleX = 0f
                    segment.pivotX = 0f
                }
                else -> {
                    segment.setBackgroundColor(0x4DFFFFFF)
                    segment.scaleX = 1f
                }
            }
        }

        if (index >= segments.size) return

        val anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = STORY_DURATION
            interpolator = LinearInterpolator()
            addUpdateListener { valueAnimator ->
                if (index < segments.size) {
                    segments[index].scaleX = valueAnimator.animatedValue as Float
                }
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    next()
                }
            })
        }
        animator = anim
        anim.start()
    }

    private fun next() {
        animator?.removeAllListeners()
        animator?.cancel()
        index++
        if (index >= stories.size) {
            finish()
        } else {
            render()
        }
    }

    private fun previous() {
        animator?.removeAllListeners()
        animator?.cancel()
        if (index > 0) {
            index--
        }
        render()
    }

    private fun markSeen(storyId: String) {
        val prefs = getSharedPreferences("lchat_prefs", Context.MODE_PRIVATE)
        val seen = HashSet(prefs.getStringSet("seen_stories", emptySet()) ?: emptySet())
        if (seen.add(storyId)) {
            prefs.edit().putStringSet("seen_stories", seen).apply()
        }
    }
}
