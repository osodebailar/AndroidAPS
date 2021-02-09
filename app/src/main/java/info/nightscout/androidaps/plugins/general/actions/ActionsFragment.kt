package info.nightscout.androidaps.plugins.general.actions

import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import dagger.android.support.DaggerFragment
import info.nightscout.androidaps.Config
import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.R
import info.nightscout.androidaps.activities.ErrorHelperActivity
import info.nightscout.androidaps.activities.TDDStatsActivity
import info.nightscout.androidaps.dialogs.*
import info.nightscout.androidaps.events.*
import info.nightscout.androidaps.historyBrowser.HistoryBrowseActivity
import info.nightscout.androidaps.interfaces.ActivePluginProvider
import info.nightscout.androidaps.interfaces.CommandQueueProvider
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.general.actions.defs.CustomAction
import info.nightscout.androidaps.plugins.general.overview.StatusLightHandler
import info.nightscout.androidaps.plugins.pump.omnipod.OmnipodPumpPlugin
import info.nightscout.androidaps.queue.Callback
import info.nightscout.androidaps.skins.SkinProvider
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.alertDialogs.OKDialog
import info.nightscout.androidaps.utils.buildHelper.BuildHelper
import info.nightscout.androidaps.utils.extensions.plusAssign
import info.nightscout.androidaps.utils.extensions.toVisibility
import info.nightscout.androidaps.utils.protection.ProtectionCheck
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.sharedPreferences.SP
import info.nightscout.androidaps.utils.ui.SingleClickButton
import info.nightscout.androidaps.utils.ui.UIRunnable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import java.util.*
import javax.inject.Inject

class ActionsFragment : DaggerFragment() {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var rxBus: RxBusWrapper
    @Inject lateinit var sp: SP
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var ctx: Context
    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var statusLightHandler: StatusLightHandler
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var activePlugin: ActivePluginProvider
    @Inject lateinit var commandQueue: CommandQueueProvider
    @Inject lateinit var buildHelper: BuildHelper
    @Inject lateinit var protectionCheck: ProtectionCheck
    @Inject lateinit var skinProvider: SkinProvider
    @Inject lateinit var config: Config

    private var disposable: CompositeDisposable = CompositeDisposable()

    private val pumpCustomActions = HashMap<String, CustomAction>()
    private val pumpCustomButtons = ArrayList<SingleClickButton>()
    private var smallWidth = false
    private var smallHeight = false
    private lateinit var dm: DisplayMetrics

    private var buttonsLayout: LinearLayout? = null
    private var profileSwitch: SingleClickButton? = null
    private var tempTarget: SingleClickButton? = null
    private var extendedBolus: SingleClickButton? = null
    private var extendedBolusCancel: SingleClickButton? = null
    private var setTempBasal: SingleClickButton? = null
    private var cancelTempBasal: SingleClickButton? = null
    private var fill: SingleClickButton? = null
    private var historyBrowser: SingleClickButton? = null
    private var tddStats: SingleClickButton? = null
    private var pumpBatteryChange: SingleClickButton? = null

    private var cannulaAge: TextView? = null
    private var insulinAge: TextView? = null
    private var reservoirLevel: TextView? = null
    private var sensorAge: TextView? = null
    private var sensorLevel: TextView? = null
    private var pbAge: TextView? = null
    private var batteryLevel: TextView? = null
    private var sensorLevelLabel: TextView? = null
    private var insulinLevelLabel: TextView? = null
    private var pbLevelLabel: TextView? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        //check screen width
        dm = DisplayMetrics()
        activity?.windowManager?.defaultDisplay?.getMetrics(dm)

        val screenWidth = dm.widthPixels
        val screenHeight = dm.heightPixels
        smallWidth = screenWidth <= Constants.SMALL_WIDTH
        smallHeight = screenHeight <= Constants.SMALL_HEIGHT
        val landscape = screenHeight < screenWidth

        return inflater.inflate(skinProvider.activeSkin().actionsLayout(landscape, smallWidth), container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        extendedBolus?.setOnClickListener {
            activity?.let { activity ->
                protectionCheck.queryProtection(activity, ProtectionCheck.Protection.BOLUS, UIRunnable {
                    OKDialog.showConfirmation(activity, resourceHelper.gs(R.string.extended_bolus), resourceHelper.gs(R.string.ebstopsloop),
                        Runnable {
                            ExtendedBolusDialog().show(childFragmentManager, "Actions")
                        }, null)
                })
            }
        }
        extendedBolusCancel?.setOnClickListener {
            if (activePlugin.activeTreatments.isInHistoryExtendedBoluslInProgress) {
                aapsLogger.debug("USER ENTRY: CANCEL EXTENDED BOLUS")
                commandQueue.cancelExtended(object : Callback() {
                    override fun run() {
                        if (!result.success) {
                            val i = Intent(ctx, ErrorHelperActivity::class.java)
                            i.putExtra("soundid", R.raw.boluserror)
                            i.putExtra("status", result.comment)
                            i.putExtra("title", resourceHelper.gs(R.string.extendedbolusdeliveryerror))
                            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            ctx.startActivity(i)
                        }
                    }
                })
            }
        }
        setTempBasal?.setOnClickListener {
            TempBasalDialog().show(childFragmentManager, "Actions")
        }
        cancelTempBasal?.setOnClickListener {
            if (activePlugin.activeTreatments.isTempBasalInProgress) {
                aapsLogger.debug("USER ENTRY: CANCEL TEMP BASAL")
                commandQueue.cancelTempBasal(true, object : Callback() {
                    override fun run() {
                        if (!result.success) {
                            val i = Intent(ctx, ErrorHelperActivity::class.java)
                            i.putExtra("soundid", R.raw.boluserror)
                            i.putExtra("status", result.comment)
                            i.putExtra("title", resourceHelper.gs(R.string.tempbasaldeliveryerror))
                            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            ctx.startActivity(i)
                        }
                    }
                })
            }
        }
      /*  actions_fill.setOnClickListener {
            activity?.let { activity ->
                protectionCheck.queryProtection(activity, ProtectionCheck.Protection.BOLUS, UIRunnable { FillDialog().show(childFragmentManager, "FillDialog") })
            }
        } */
        //actions_historybrowser.setOnClickListener { startActivity(Intent(context, HistoryBrowseActivity::class.java)) }
       // actions_tddstats.setOnClickListener { startActivity(Intent(context, TDDStatsActivity::class.java)) }
        view.findViewById<SingleClickButton>(R.id.actions_bgcheck).setOnClickListener {
            CareDialog().setOptions(CareDialog.EventType.BGCHECK, R.string.careportal_bgcheck).show(childFragmentManager, "Actions")
        }
       /* actions_cgmsensorinsert.setOnClickListener {
            CareDialog().setOptions(CareDialog.EventType.SENSOR_INSERT, R.string.careportal_cgmsensorinsert).show(childFragmentManager, "Actions")
        }
        pumpBatteryChange?.setOnClickListener {
            CareDialog().setOptions(CareDialog.EventType.BATTERY_CHANGE, R.string.careportal_pumpbatterychange).show(childFragmentManager, "Actions")
        }*/
            view.findViewById<SingleClickButton>(R.id.actions_note).setOnClickListener {
            CareDialog().setOptions(CareDialog.EventType.NOTE, R.string.careportal_note).show(childFragmentManager, "Actions")
        }
        view.findViewById<SingleClickButton>(R.id.actions_exercise).setOnClickListener {
            CareDialog().setOptions(CareDialog.EventType.EXERCISE, R.string.careportal_exercise).show(childFragmentManager, "Actions")
        }
      /*  actions_question.setOnClickListener {
            CareDialog().setOptions(CareDialog.EventType.QUESTION, R.string.careportal_question).show(childFragmentManager, "Actions")
        }
        view.findViewById<SingleClickButton>(R.id.actions_announcement).setOnClickListener {
            CareDialog().setOptions(CareDialog.EventType.ANNOUNCEMENT, R.string.careportal_announcement).show(childFragmentManager, "Actions")
        }*/

        sp.putBoolean(R.string.key_objectiveuseactions, true)
    }

    @Synchronized
    override fun onResume() {
        super.onResume()
        disposable += rxBus
            .toObservable(EventInitializationChanged::class.java)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ updateGui() }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventRefreshOverview::class.java)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ updateGui() }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventExtendedBolusChange::class.java)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ updateGui() }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventTempBasalChange::class.java)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ updateGui() }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventCustomActionsChanged::class.java)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ updateGui() }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventCareportalEventChange::class.java)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ updateGui() }, fabricPrivacy::logException)
        updateGui()
    }

    @Synchronized
    override fun onPause() {
        super.onPause()
        disposable.clear()
    }

    @Synchronized
    fun updateGui() {

        val pump = activePlugin.activePump

     /*   actions_profileswitch?.visibility = (
            activePlugin.activeProfileInterface.profile != null &&
                pump.pumpDescription.isSetBasalProfileCapable &&
                pump.isInitialized &&
                !pump.isSuspended).toVisibility()*/

        if (!pump.pumpDescription.isExtendedBolusCapable || !pump.isInitialized || pump.isSuspended || pump.isFakingTempsByExtendedBoluses) {
            extendedBolus?.visibility = View.GONE
            extendedBolusCancel?.visibility = View.GONE
        } else {
            val activeExtendedBolus = activePlugin.activeTreatments.getExtendedBolusFromHistory(System.currentTimeMillis())
            if (activeExtendedBolus != null) {
                extendedBolus?.visibility = View.GONE
                extendedBolusCancel?.visibility = View.VISIBLE
                @Suppress("SetTextI18n")
                extendedBolusCancel?.text = resourceHelper.gs(R.string.cancel) + " " + activeExtendedBolus.toStringMedium()
            } else {
                extendedBolus?.visibility = View.VISIBLE
                extendedBolusCancel?.visibility = View.GONE
            }
        }

        if (!pump.pumpDescription.isTempBasalCapable || !pump.isInitialized || pump.isSuspended) {
            setTempBasal?.visibility = View.GONE
            cancelTempBasal?.visibility = View.GONE
        } else {
            val activeTemp = activePlugin.activeTreatments.getTempBasalFromHistory(System.currentTimeMillis())
            if (activeTemp != null) {
                setTempBasal?.visibility = View.GONE
                cancelTempBasal?.visibility = View.VISIBLE
                @Suppress("SetTextI18n")
                cancelTempBasal?.text = resourceHelper.gs(R.string.cancel) + " " + activeTemp.toStringShort()
            } else {
                setTempBasal?.visibility = View.VISIBLE
                cancelTempBasal?.visibility = View.GONE
            }
        }

      /*  actions_historybrowser.visibility = (profile != null).toVisibility()
        actions_fill?.visibility = (pump.pumpDescription.isRefillingCapable && pump.isInitialized && !pump.isSuspended).toVisibility()
        actions_pumpbatterychange?.visibility = (pump.pumpDescription.isBatteryReplaceable || (pump is OmnipodPumpPlugin && pump.isUseRileyLinkBatteryLevel && pump.isBatteryChangeLoggingEnabled)).toVisibility()
        actions_temptarget?.visibility = (profile != null && config.APS).toVisibility()
        actions_tddstats?.visibility = pump.pumpDescription.supportsTDDs.toVisibility()*/

        // statusLightHandler.updateStatusLights(careportal_canulaage, careportal_insulinage, null, careportal_sensorage, careportal_pbage, null)
        checkPumpCustomActions()

    }


    val Int.dp: Int
        get() = (this * Resources.getSystem().displayMetrics.density + 0.5f).toInt()

    private fun checkPumpCustomActions() {
        val activePump = activePlugin.activePump
        val customActions = activePump.customActions ?: return
        removePumpCustomActions()

        for (customAction in customActions) {
            if (!customAction.isEnabled) continue

            val btn = SingleClickButton(requireContext(), null, R.attr.materialButtonStyle)
            btn.text = resourceHelper.gs(customAction.name)

            val layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)
            layoutParams.setMargins(24, 8, 24, 8) // 10,3,10,3

            btn.layoutParams = layoutParams
            btn.cornerRadius = 16.dp
            btn.setOnClickListener { v ->
                val b = v as SingleClickButton
                this.pumpCustomActions[b.text.toString()]?.let {
                    activePlugin.activePump.executeCustomAction(it.customActionType)
                }
            }
            val left = activity?.let { ContextCompat.getDrawable(it, customAction.iconResourceId) }
            btn.setCompoundDrawablesWithIntrinsicBounds(left, null, null, null)

            buttonsLayout?.addView(btn)

            this.pumpCustomActions[resourceHelper.gs(customAction.name)] = customAction
            this.pumpCustomButtons.add(btn)
        }
    }

    private fun removePumpCustomActions() {
        for (customButton in pumpCustomButtons) buttonsLayout?.removeView(customButton)
        pumpCustomButtons.clear()
    }
}