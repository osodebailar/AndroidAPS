package info.nightscout.androidaps.dana

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.res.ResourcesCompat
import dagger.android.support.DaggerFragment
import info.nightscout.androidaps.activities.TDDStatsActivity
import info.nightscout.androidaps.dana.databinding.DanarFragmentBinding
import info.nightscout.androidaps.dialogs.ProfileViewerDialog
import info.nightscout.androidaps.events.EventExtendedBolusChange
import info.nightscout.androidaps.events.EventInitializationChanged
import info.nightscout.androidaps.events.EventPumpStatusChanged
import info.nightscout.androidaps.events.EventTempBasalChange
import info.nightscout.androidaps.interfaces.ActivePluginProvider
import info.nightscout.androidaps.interfaces.CommandQueueProvider
import info.nightscout.androidaps.interfaces.PumpInterface
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.logging.UserEntryLogger
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.pump.common.defs.PumpType
import info.nightscout.androidaps.queue.events.EventQueueChanged
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.T
import info.nightscout.androidaps.utils.ViewAnimation
import info.nightscout.androidaps.utils.WarnColors
import info.nightscout.androidaps.utils.alertDialogs.OKDialog
import info.nightscout.androidaps.utils.extensions.toVisibility
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.rx.AapsSchedulers
import info.nightscout.androidaps.utils.sharedPreferences.SP
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.plusAssign
import javax.inject.Inject

class DanaFragment : DaggerFragment() {

    @Inject lateinit var rxBus: RxBusWrapper
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var commandQueue: CommandQueueProvider
    @Inject lateinit var activePlugin: ActivePluginProvider
    @Inject lateinit var danaPump: DanaPump
    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var sp: SP
    @Inject lateinit var warnColors: WarnColors
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var uel: UserEntryLogger

    private var disposable: CompositeDisposable = CompositeDisposable()

    private lateinit var mHandler: Handler
    private lateinit var mRunnable:Runnable
    private val loopHandler = Handler()
    private lateinit var refreshLoop: Runnable

    private var _binding: DanarFragmentBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    init {
        refreshLoop = Runnable {
            activity?.runOnUiThread { updateGUI() }
            loopHandler.postDelayed(refreshLoop, T.mins(1).msecs())
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View {
        _binding = DanarFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.danaSwipeRefresh.setColorSchemeResources(R.color.orange, R.color.green, R.color.blue)
        binding.danaSwipeRefresh.setProgressBackgroundColorSchemeColor(ResourcesCompat.getColor(resources, R.color.swipe_background, null))
        // Initialize the handler instance
        mHandler = Handler()
        binding.danaSwipeRefresh.setOnRefreshListener {

            mRunnable = Runnable {
                // Hide swipe to refresh icon animation
                binding.danaSwipeRefresh.isRefreshing = false
                aapsLogger.debug(LTag.PUMP, "swipe to connect to pump")
                danaPump.lastConnection = 0
                commandQueue.readStatus("swipe to connect to pump", null)
            }

            // Execute the task after specified time
            mHandler.postDelayed(
                mRunnable,
                (3000).toLong() // Delay 1 to 5 seconds
            )
        }

        binding.danaPumpstatus.setBackgroundColor(resourceHelper.getAttributeColor(context, R.attr.informationBackground))
        binding.danaPumpstatus.setTextColor(resourceHelper.getAttributeColor(context, R.attr.informationText))

        ViewAnimation.showOut(binding.fabDanaMenuUserOptions)
        ViewAnimation.showOut(binding.danarHistory)
        ViewAnimation.showOut(binding.danarStats)
        ViewAnimation.showOut(binding.danarViewprofile)

        binding.fabDanaMenuUserOptions.setOnClickListener(clickListener)
        binding.fabDanaMenu.setOnClickListener(clickListener)
        binding.danarHistory.setOnClickListener(clickListener)
        binding.danarStats.setOnClickListener(clickListener)
        binding.danarViewprofile.setOnClickListener(clickListener)
        binding.btconnection.setOnClickListener {
            aapsLogger.debug(LTag.PUMP, "Clicked connect to pump")
            danaPump.lastConnection = 0
            commandQueue.readStatus("Clicked connect to pump", null)
        }
        if (activePlugin.activePump.pumpDescription.pumpType == PumpType.DanaRS)
            binding.btconnection.setOnLongClickListener {
                activity?.let {
                    OKDialog.showConfirmation(it, resourceHelper.gs(R.string.resetpairing), Runnable {
                        uel.log("CLEAR PAIRING KEYS")
                        (activePlugin.activePump as DanaPumpInterface).clearPairing()
                    })

                }
                true
            }
    }

    private val clickListener: View.OnClickListener = View.OnClickListener { view ->
        when ( view.id ){
            R.id.fabDanaMenu -> {
                if ( binding.danarViewprofile.visibility == View.GONE) {
                    ViewAnimation.showIn(binding.fabDanaMenuUserOptions)
                    ViewAnimation.showIn(binding.danarHistory)
                    ViewAnimation.showIn(binding.danarStats)
                    ViewAnimation.showIn(binding.danarViewprofile)
                } else  {
                    ViewAnimation.showOut(binding.fabDanaMenuUserOptions)
                    ViewAnimation.showOut(binding.danarHistory)
                    ViewAnimation.showOut(binding.danarStats)
                    ViewAnimation.showOut(binding.danarViewprofile)
                }
            }
            R.id.fabDanaMenuUserOptions -> {
                startActivity(Intent(context, info.nightscout.androidaps.dana.activities.DanaUserOptionsActivity::class.java))
                ViewAnimation.showOut(binding.fabDanaMenuUserOptions)
                ViewAnimation.showOut(binding.danarHistory)
                ViewAnimation.showOut(binding.danarStats)
                ViewAnimation.showOut(binding.danarViewprofile)
            }
            R.id.danar_history -> {
                startActivity(Intent(context, info.nightscout.androidaps.dana.activities.DanaHistoryActivity::class.java))
                ViewAnimation.showOut(binding.fabDanaMenuUserOptions)
                ViewAnimation.showOut(binding.danarHistory)
                ViewAnimation.showOut(binding.danarStats)
                ViewAnimation.showOut(binding.danarViewprofile)
            }
            R.id.danar_stats -> {
                startActivity(Intent(context, TDDStatsActivity::class.java))
                ViewAnimation.showOut(binding.fabDanaMenuUserOptions)
                ViewAnimation.showOut(binding.danarHistory)
                ViewAnimation.showOut(binding.danarStats)
                ViewAnimation.showOut(binding.danarViewprofile)
            }
            R.id.danar_viewprofile -> {
                val profile = danaPump.createConvertedProfile()?.getDefaultProfile()
                    ?: return@OnClickListener
                val profileName = danaPump.createConvertedProfile()?.getDefaultProfileName()
                    ?: return@OnClickListener
                val args = Bundle()
                args.putLong("time", DateUtil.now())
                args.putInt("mode", ProfileViewerDialog.Mode.CUSTOM_PROFILE.ordinal)
                args.putString("customProfile", profile.data.toString())
                args.putString("customProfileUnits", profile.units)
                args.putString("customProfileName", profileName)
                val pvd = ProfileViewerDialog()
                pvd.arguments = args
                pvd.show(childFragmentManager, "ProfileViewDialog")
                ViewAnimation.showOut(binding.fabDanaMenuUserOptions)
                ViewAnimation.showOut(binding.danarHistory)
                ViewAnimation.showOut(binding.danarStats)
                ViewAnimation.showOut(binding.danarViewprofile)
            }
        }

    }

    @Synchronized
    override fun onResume() {
        super.onResume()
        loopHandler.postDelayed(refreshLoop, T.mins(1).msecs())
        disposable += rxBus
            .toObservable(EventInitializationChanged::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ updateGUI() }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(info.nightscout.androidaps.dana.events.EventDanaRNewStatus::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ updateGUI() }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventExtendedBolusChange::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ updateGUI() }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventTempBasalChange::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ updateGUI() }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventQueueChanged::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ updateGUI() }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventPumpStatusChanged::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({
                when (it.status) {
                    EventPumpStatusChanged.Status.CONNECTING   ->
                        @Suppress("SetTextI18n")
                        binding.btconnection.text = "{fa-bluetooth-b spin} ${it.secondsElapsed}s"
                    EventPumpStatusChanged.Status.CONNECTED    ->
                        @Suppress("SetTextI18n")
                        binding.btconnection.text = "{fa-bluetooth}"
                    EventPumpStatusChanged.Status.DISCONNECTED ->
                        @Suppress("SetTextI18n")
                        binding.btconnection.text = "{fa-bluetooth-b}"

                    else                                       -> {
                    }
                }
                if (it.getStatus(resourceHelper) != "") {
                    binding.danaPumpstatus.text = it.getStatus(resourceHelper)
                    binding.danaPumpstatuslayout.visibility = View.VISIBLE
                } else {
                    binding.danaPumpstatuslayout.visibility = View.GONE
                }
            }, fabricPrivacy::logException)
        updateGUI()
    }

    @Synchronized
    override fun onPause() {
        super.onPause()
        disposable.clear()
        loopHandler.removeCallbacks(refreshLoop)
    }

    @Synchronized
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    @SuppressLint("SetTextI18n")
    @Synchronized
    fun updateGUI() {
        if (_binding == null) return
        val pump = danaPump
        val plugin: PumpInterface = activePlugin.activePump
        if (pump.lastConnection != 0L) {
            val agoMsec = System.currentTimeMillis() - pump.lastConnection
            val agoMin = (agoMsec.toDouble() / 60.0 / 1000.0).toInt()
            binding.lastconnection.text = dateUtil.timeString(pump.lastConnection) + " (" + resourceHelper.gs(R.string.minago, agoMin) + ")"
            warnColors.setColor(binding.lastconnection, agoMin.toDouble(), 16.0, 31.0, resourceHelper.getAttributeColor(context, R.attr.statuslight_normal),
                    resourceHelper.getAttributeColor(context, R.attr.statuslight_Warning),
                    resourceHelper.getAttributeColor(context, R.attr.statuslight_alarm))
        }
        if (pump.lastBolusTime != 0L) {
            val agoMsec = System.currentTimeMillis() - pump.lastBolusTime
            val agoHours = agoMsec.toDouble() / 60.0 / 60.0 / 1000.0
            if (agoHours < 6)
            // max 6h back
                binding.lastbolus.text = dateUtil.timeString(pump.lastBolusTime) + " " + DateUtil.sinceString(pump.lastBolusTime, resourceHelper) + " " + resourceHelper.gs(R.string.formatinsulinunits, pump.lastBolusAmount)
            else
                binding.lastbolus.text = ""
        }

        binding.dailyunits.text = resourceHelper.gs(R.string.reservoirvalue, pump.dailyTotalUnits, pump.maxDailyTotalUnits)
        warnColors.setColor(binding.dailyunits, pump.dailyTotalUnits, pump.maxDailyTotalUnits * 0.75, pump.maxDailyTotalUnits * 0.9, resourceHelper.getAttributeColor(context, R.attr.statuslight_normal), resourceHelper.getAttributeColor(context, R.attr.statuslight_Warning), resourceHelper.getAttributeColor(context, R.attr.statuslight_alarm))
        binding.basabasalrate.text = "( " + (pump.activeProfile + 1) + " )  " + resourceHelper.gs(R.string.pump_basebasalrate, plugin.baseBasalRate)
        // DanaRPlugin, DanaRKoreanPlugin
        if (activePlugin.activePump.isFakingTempsByExtendedBoluses) {
            binding.tempbasal.text = activePlugin.activeTreatments.getRealTempBasalFromHistory(System.currentTimeMillis())?.toStringFull()
                ?: ""
        } else {
            // v2 plugin
            binding.tempbasal.text = activePlugin.activeTreatments.getTempBasalFromHistory(System.currentTimeMillis())?.toStringFull()
                ?: ""
        }
        binding.extendedbolus.text = activePlugin.activeTreatments.getExtendedBolusFromHistory(System.currentTimeMillis())?.toString()
            ?: ""
        binding.reservoir.text = resourceHelper.gs(R.string.reservoirvalue, pump.reservoirRemainingUnits, 300)
        warnColors.setColorInverse(binding.reservoir, pump.reservoirRemainingUnits, 50.0, 20.0,  resourceHelper.getAttributeColor(context, R.attr.statuslight_normal),
                resourceHelper.getAttributeColor(context, R.attr.statuslight_Warning),
                resourceHelper.getAttributeColor(context, R.attr.statuslight_alarm))
        binding.battery.text = "{fa-battery-" + pump.batteryRemaining / 25 + "}"
        warnColors.setColorInverse(binding.battery, pump.batteryRemaining.toDouble(), 51.0, 26.0,  resourceHelper.getAttributeColor(context, R.attr.statuslight_normal),
                resourceHelper.getAttributeColor(context, R.attr.statuslight_Warning),
                resourceHelper.getAttributeColor(context, R.attr.statuslight_alarm))
        binding.iob.text = resourceHelper.gs(R.string.formatinsulinunits, pump.iob)
        binding.firmware.text = resourceHelper.gs(R.string.dana_model, pump.modelFriendlyName(), pump.hwModel, pump.protocol, pump.productCode)
        binding.basalstep.text = pump.basalStep.toString()
        binding.bolusstep.text = pump.bolusStep.toString()
        binding.serialNumber.text = pump.serialNumber
        val status = commandQueue.spannedStatus()
        if (status.toString() == "") {
            binding.queue.visibility = View.GONE
        } else {
            binding.queue.visibility = View.VISIBLE
            binding.queue.text = status
        }
        //hide user options button if not an RS pump or old firmware
        binding.fabDanaMenuUserOptions.visibility = (pump.hwModel != 1 && pump.protocol != 0x00).toVisibility()
    }
}
