package info.nightscout.androidaps.skins

import android.util.DisplayMetrics
import android.util.TypedValue.COMPLEX_UNIT_PX
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.LayoutRes
import androidx.annotation.StringRes
import androidx.constraintlayout.widget.ConstraintLayout
import info.nightscout.androidaps.R

interface SkinInterface {

    @get:StringRes val description: Int

    val mainGraphHeight: Int // in dp
    val secondaryGraphHeight: Int // in dp

    @LayoutRes
    fun actionsLayout(isLandscape: Boolean, isSmallWidth: Boolean): Int = R.layout.actions_fragment

    fun preProcessLandscapeOverviewLayout(dm: DisplayMetrics, view: View, isLandscape: Boolean, isTablet: Boolean, isSmallHeight: Boolean) {
        // pre-process landscape mode
        val screenWidth = dm.widthPixels
        val screenHeight = dm.heightPixels
        val landscape = screenHeight < screenWidth

        if (landscape) {
            val iobLayout = view.findViewById<LinearLayout>(R.id.iob_layout)
            val iobLayoutParams = iobLayout.layoutParams as ConstraintLayout.LayoutParams
            iobLayoutParams.startToStart = ConstraintLayout.LayoutParams.UNSET
            iobLayoutParams.topToBottom = ConstraintLayout.LayoutParams.UNSET
            iobLayoutParams.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
            val basalLayoutParams = view.findViewById<LinearLayout>(R.id.basal_layout).layoutParams as ConstraintLayout.LayoutParams
            basalLayoutParams.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
            val extendedLayoutParams = view.findViewById<LinearLayout>(R.id.extended_layout).layoutParams as ConstraintLayout.LayoutParams
            extendedLayoutParams.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
            val asLayoutParams = view.findViewById<LinearLayout>(R.id.as_layout).layoutParams as ConstraintLayout.LayoutParams
            asLayoutParams.topToTop = ConstraintLayout.LayoutParams.PARENT_ID

            if (isTablet) {
                for (v in listOf<TextView?>(

                )) v?.setTextSize(COMPLEX_UNIT_PX, v.textSize * 1.5f)
                for (v in listOf<TextView?>(

                )) if (v != null) {
                    v.setTextSize(COMPLEX_UNIT_PX, v.textSize * 1.3f)
                }
            }
        }
    }

}