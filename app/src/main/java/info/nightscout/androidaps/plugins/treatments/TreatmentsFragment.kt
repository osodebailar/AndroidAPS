package info.nightscout.androidaps.plugins.treatments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import com.google.android.material.tabs.TabLayout
import dagger.android.support.DaggerFragment
import info.nightscout.androidaps.R
import info.nightscout.androidaps.databinding.TreatmentsFragmentBinding
import info.nightscout.androidaps.events.EventExtendedBolusChange
import info.nightscout.androidaps.interfaces.ActivePluginProvider
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.treatments.fragments.*
import info.nightscout.androidaps.utils.FabricPrivacy
import io.reactivex.rxkotlin.plusAssign
import info.nightscout.androidaps.utils.extensions.toVisibility
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.rx.AapsSchedulers
import io.reactivex.disposables.CompositeDisposable
import javax.inject.Inject

class TreatmentsFragment : DaggerFragment() {

    @Inject lateinit var rxBus: RxBusWrapper
    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var activePlugin: ActivePluginProvider
    @Inject lateinit var treatmentsPlugin: TreatmentsPlugin
    @Inject lateinit var aapsSchedulers: AapsSchedulers

    private val disposable = CompositeDisposable()

    private var _binding: TreatmentsFragmentBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        TreatmentsFragmentBinding.inflate(inflater, container, false).also { _binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.tabLayout.addTab(binding.tabLayout.newTab().setText(R.string.bolus))

        if (activePlugin.activePump.pumpDescription.isExtendedBolusCapable || treatmentsPlugin.extendedBolusesFromHistory.size() > 0)
            binding.tabLayout.addTab(binding.tabLayout.newTab().setText(R.string.extended_bolus))

        binding.tabLayout.addTab(binding.tabLayout.newTab().setText(R.string.tempbasal_label))
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText(R.string.careportal_temporarytarget))
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText(R.string.careportal_profileswitch))
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText(R.string.careportal))


        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                if ( tab.text == getText(R.string.bolus)) {
                    setFragment(TreatmentsBolusFragment())
                }
                if (activePlugin.activePump.pumpDescription.isExtendedBolusCapable || treatmentsPlugin.extendedBolusesFromHistory.size() > 0){
                    if ( tab.text == getText(R.string.extended_bolus)) {
                        setFragment(TreatmentsExtendedBolusesFragment())
                    }
                }
                if ( tab.text == getText(R.string.tempbasal_label)) {
                    setFragment(TreatmentsTemporaryBasalsFragment())
                }
                if ( tab.text == getText(R.string.careportal_temporarytarget)) {
                    setFragment(TreatmentsTempTargetFragment())
                }
                if ( tab.text == getText(R.string.careportal_profileswitch)) {
                    setFragment(TreatmentsProfileSwitchFragment())
                }
                if ( tab.text == getText(R.string.careportal)) {
                    setFragment(TreatmentsCareportalFragment())
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })

        setFragment(TreatmentsBolusFragment())
    }

    @Synchronized
    override fun onResume() {
        super.onResume()
        disposable += rxBus
            .toObservable(EventExtendedBolusChange::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ updateGui() }, fabricPrivacy::logException)
        updateGui()
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

    private fun setFragment(selectedFragment: Fragment) {
        childFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, selectedFragment) // f2_container is your FrameLayout container
            .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
            .addToBackStack(null)
            .commit()
    }


    private fun updateGui() {
        if (_binding == null) return
    }
}