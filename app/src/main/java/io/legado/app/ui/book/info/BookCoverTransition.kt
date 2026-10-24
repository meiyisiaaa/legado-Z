package io.legado.app.ui.book.info

import android.animation.ValueAnimator
import android.transition.ChangeBounds
import android.transition.ChangeClipBounds
import android.transition.ChangeImageTransform
import android.transition.ChangeTransform
import android.transition.TransitionSet
import io.legado.app.help.config.AppConfig

internal const val EXTRA_SHARED_COVER_TRANSITION_NAME = "sharedCoverTransitionName"

internal fun bookCoverTransitionName(bookUrl: String): String = "book-cover:$bookUrl"

internal fun isBookCoverTransitionEnabled(): Boolean =
    !AppConfig.isEInkMode && ValueAnimator.areAnimatorsEnabled()

internal fun newBookCoverTransition(): TransitionSet = TransitionSet().apply {
    ordering = TransitionSet.ORDERING_TOGETHER
    duration = 360L
    addTransition(ChangeBounds())
    addTransition(ChangeTransform())
    addTransition(ChangeClipBounds())
    addTransition(ChangeImageTransform())
}
