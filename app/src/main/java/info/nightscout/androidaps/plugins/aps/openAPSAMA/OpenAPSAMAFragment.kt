package info.nightscout.androidaps.plugins.aps.openAPSAMA

import android.os.Bundle
import android.os.Handler
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.res.ResourcesCompat
import dagger.android.support.DaggerFragment
import info.nightscout.androidaps.R
import info.nightscout.androidaps.databinding.OpenapsamaFragmentBinding
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.aps.events.EventOpenAPSUpdateGui
import info.nightscout.androidaps.plugins.aps.events.EventOpenAPSUpdateResultGui
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.JSONFormatter
import info.nightscout.androidaps.utils.extensions.plusAssign
import info.nightscout.androidaps.utils.resources.ResourceHelper
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import org.json.JSONArray
import org.json.JSONException
import javax.inject.Inject

class OpenAPSAMAFragment : DaggerFragment() {
    private lateinit var mRunnable:Runnable
    private lateinit var mHandler: Handler
    private var disposable: CompositeDisposable = CompositeDisposable()

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var rxBus: RxBusWrapper
    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var openAPSAMAPlugin: OpenAPSAMAPlugin
    @Inject lateinit var dateUtil: DateUtil

    private var _binding: OpenapsamaFragmentBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View {
        _binding = OpenapsamaFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.swipeRefreshOpenapsAma.setColorSchemeResources(R.color.orange, R.color.green, R.color.blue)
        binding.swipeRefreshOpenapsAma.setProgressBackgroundColorSchemeColor(ResourcesCompat.getColor(resources, R.color.swipe_background, null))
        // Initialize the handler instance
        mHandler = Handler()

        binding.swipeRefreshOpenapsAma.setOnRefreshListener {

            mRunnable = Runnable {
                // Hide swipe to refresh icon animation
                binding.swipeRefreshOpenapsAma.isRefreshing = false
                openAPSAMAPlugin.invoke("OpenAPSAMA button", false)
            }

            // Execute the task after specified time
            mHandler.postDelayed(
                mRunnable,
                (3000).toLong() // Delay 1 to 5 seconds
            )
        }
    }

    @Synchronized
    override fun onResume() {
        super.onResume()

        disposable += rxBus
            .toObservable(EventOpenAPSUpdateGui::class.java)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                updateGUI()
            }, { fabricPrivacy.logException(it) })
        disposable += rxBus
            .toObservable(EventOpenAPSUpdateResultGui::class.java)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                updateResultGUI(it.text)
            }, { fabricPrivacy.logException(it) })

        updateGUI()
    }

    @Synchronized
    override fun onPause() {
        super.onPause()
        disposable.clear()
    }

    @Synchronized
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    @Synchronized
    private fun updateGUI() {
        if (_binding == null) return
        openAPSAMAPlugin.lastAPSResult?.let { lastAPSResult ->
            binding.openapsmaResult.text = JSONFormatter.format(lastAPSResult.json)
            binding.openapsmaRequest.text = lastAPSResult.toSpanned()
        }
        openAPSAMAPlugin.lastDetermineBasalAdapterAMAJS?.let { determineBasalAdapterAMAJS ->
            binding.openapsmaGlucosestatus.text = JSONFormatter.format(determineBasalAdapterAMAJS.glucoseStatusParam)
            binding.openapsmaCurrenttemp.text = JSONFormatter.format(determineBasalAdapterAMAJS.currentTempParam)
            try {
                val iobArray = JSONArray(determineBasalAdapterAMAJS.iobDataParam)
                binding.openapsmaIobdata.text = TextUtils.concat(resourceHelper.gs(R.string.array_of_elements, iobArray.length()) + "\n", JSONFormatter.format(iobArray.getString(0)))
            } catch (e: JSONException) {
                aapsLogger.error(LTag.APS, "Unhandled exception", e)
                @Suppress("SetTextI18n")
                binding.openapsmaIobdata.text = "JSONException see log for details"
            }

            binding.openapsmaProfile.text = JSONFormatter.format(determineBasalAdapterAMAJS.profileParam)
            binding.openapsmaMealdata.text = JSONFormatter.format(determineBasalAdapterAMAJS.mealDataParam)
            binding.openapsmaScriptdebugdata.text = determineBasalAdapterAMAJS.scriptDebug
        }
        if (openAPSAMAPlugin.lastAPSRun != 0L) {
            binding.openapsmaLastrun.text = dateUtil.dateAndTimeString(openAPSAMAPlugin.lastAPSRun)
        }
        openAPSAMAPlugin.lastAutosensResult?.let {
            binding.openapsmaAutosensdata.text = JSONFormatter.format(it.json())
        }
    }

    private fun updateResultGUI(text: String) {
        binding.openapsmaResult.text = text
        binding.openapsmaGlucosestatus.text = ""
        binding.openapsmaCurrenttemp.text = ""
        binding.openapsmaIobdata.text = ""
        binding.openapsmaProfile.text = ""
        binding.openapsmaMealdata.text = ""
        binding.openapsmaAutosensdata.text = ""
        binding.openapsmaScriptdebugdata.text = ""
        binding.openapsmaRequest.text = ""
        binding.openapsmaLastrun.text = ""
    }
}
