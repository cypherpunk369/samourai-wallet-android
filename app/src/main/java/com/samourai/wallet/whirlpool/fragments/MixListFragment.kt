package com.samourai.wallet.whirlpool.fragments

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.transition.TransitionManager
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.transition.MaterialFadeThrough
import com.google.android.material.transition.SlideDistanceProvider
import com.samourai.wallet.R
import com.samourai.wallet.databinding.WhirlpoolAddCoinsLayoutBinding
import com.samourai.wallet.databinding.WhirlpoolIntroViewBinding
import com.samourai.wallet.whirlpool.MixDetailsBottomSheet
import com.samourai.wallet.whirlpool.WhirlPoolHomeViewModel
import com.samourai.wallet.whirlpool.WhirlpoolHome
import com.samourai.wallet.whirlpool.WhirlpoolHome.Companion.NEWPOOL_REQ_CODE
import com.samourai.wallet.whirlpool.newPool.NewPoolActivity
import com.samourai.whirlpool.client.wallet.AndroidWhirlpoolWalletService
import com.samourai.whirlpool.client.wallet.beans.MixableStatus
import com.samourai.whirlpool.client.wallet.beans.WhirlpoolUtxoStatus
import io.reactivex.disposables.CompositeDisposable
import kotlinx.coroutines.*
import android.widget.FrameLayout.LayoutParams as LParams


class MixListFragment : Fragment() {


    enum class MixListType {
        REMIX,
        MIXING
    }


    private val viewModel: WhirlPoolHomeViewModel by activityViewModels()
    private lateinit var binding: WhirlpoolAddCoinsLayoutBinding
    private var mixTypeArg: String? = null
    private val mixListAdapter: MixListAdapter = MixListAdapter()
    private val compositeDisposable = CompositeDisposable()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            mixTypeArg = it.getString(MIX_TYPE)
        }

    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setUpList()
        viewModel.onboardStatus.observe(viewLifecycleOwner, { showOnboard ->
            if (showOnboard) {
                showIntroView()
            } else {
                val isRemixList = MixListType.REMIX.toString() == mixTypeArg;
                val mixList =
                    if (isRemixList) viewModel.remixLive else viewModel.mixingLive

                mixList.observe(viewLifecycleOwner, { list ->
                    if(viewModel.listRefreshStatus.value != false) {
                        return@observe
                    }
                    val onBoardingActive = viewModel.onboardStatus.value ?: showOnboard
                    if (onBoardingActive) {
                        return@observe
                    }
                    if (list.isEmpty() && !binding.mixSwipeContainer.isRefreshing) {
                        showAddMoreCoinView(!isRemixList)
                    } else {
                        if (binding.mixListContainer.visibility != View.VISIBLE && !showOnboard) {
                            showListView()
                        }
                    }
                    compositeDisposable.clear()
                    list.forEach {
                        it.utxoState.observable.subscribe {
                            refreshList()
                        }.apply {
                            compositeDisposable.add(this)
                        }
                    }
                    mixListAdapter.updateList(list)
                })

            }
        })

        viewModel.displaySatsLive.observe(viewLifecycleOwner, {
            mixListAdapter.setDisplaySats(it)
        })
        mixListAdapter.setOnClickListener {
            val mixDetailsBottomSheet =
                MixDetailsBottomSheet.newInstance(it.utxo.tx_hash, it.utxo.tx_output_n)
            mixDetailsBottomSheet.show(childFragmentManager, mixDetailsBottomSheet.tag)
        }
        mixListAdapter.setOnMixingButtonClickListener {
            if (AndroidWhirlpoolWalletService.getInstance().whirlpoolWallet.isPresent) {
                val wallet = AndroidWhirlpoolWalletService.getInstance().whirlpoolWallet.get();
                if(it.utxoState.mixableStatus == MixableStatus.UNCONFIRMED){
                    Snackbar.make(binding.mixListContainer,R.string.unconfirmed,Snackbar.LENGTH_LONG).show()
                    return@setOnMixingButtonClickListener
                }
                if(it.utxoState.mixableStatus == MixableStatus.NO_POOL){
                    return@setOnMixingButtonClickListener
                }
                if (it.utxoState != null && it.utxoState.status != null) {
                    if (it.utxoState.status == WhirlpoolUtxoStatus.MIX_STARTED) {
                        wallet.mixStop(it)
                        wallet.mixQueue(it)
                    }else{
                        wallet.mix(it)
                    }
                }
            }

        }

    }


    private fun refreshList() {
        val list =
            if (mixTypeArg == MixListType.REMIX.toString()) viewModel.remixLive.value else viewModel.mixingLive.value
        if (list != null)
            mixListAdapter.updateList(list)
    }

    private fun showListView() {
        startTransition()
        binding.mixIntroContainer.visibility = View.GONE
        binding.mixMessageContainer.visibility = View.GONE
        binding.mixListContainer.visibility = View.VISIBLE

    }

    private fun showIntroView() {
//        startTransition()
        binding.mixMessageContainer.visibility = View.GONE
        binding.mixListContainer.visibility = View.GONE
        binding.mixIntroContainer.visibility = View.VISIBLE

        val introBinding =
            WhirlpoolIntroViewBinding.inflate(layoutInflater, binding.mixIntroContainer, true)

        if (mixTypeArg == MixListType.MIXING.toString()) {
            introBinding.whirlpoolIntroTopText.text = getString(R.string.coins_that_are_currently)
            introBinding.whirlpoolIntroSubText.text = getString(R.string.your_coins_will_be_split)
            introBinding.whirlpoolIntroImage.setImageResource(R.drawable.ic_nue_mixing_graphic)
        } else {
            introBinding.whirlpoolIntroTopText.text = getString(R.string.coins_that_have_been_mixed)
            introBinding.whirlpoolIntroSubText.text =
                getString(R.string.whirlpool_offers_absolutely)
            introBinding.whirlpoolIntroImage.setImageResource(R.drawable.ic_nue_remixing_graphic)
        }
        introBinding.whirlpoolIntroGetStarted.setOnClickListener {
            activity?.startActivityForResult(
                Intent(activity, NewPoolActivity::class.java),
                WhirlpoolHome.NEWPOOL_REQ_CODE
            )
        }

    }

    private fun startTransition() {
        val fadeThrough = MaterialFadeThrough().apply {
            SlideDistanceProvider(Gravity.BOTTOM)
        }
        TransitionManager.beginDelayedTransition(binding.root, fadeThrough)
    }

    private fun showAddMoreCoinView(isMixing: Boolean) {
        startTransition()
        binding.mixIntroContainer.visibility = View.GONE
        binding.mixMessageContainer.visibility = View.VISIBLE
        binding.mixListContainer.visibility = View.GONE
        binding.addCoinsBtn.setOnClickListener {
            activity?.startActivityForResult(
                Intent(activity, NewPoolActivity::class.java),
                NEWPOOL_REQ_CODE
            )
        }
        if (isMixing) {
            binding.image.visibility = View.VISIBLE
            binding.title.text = getString(R.string.your_coins_have_been_mixed)
            binding.description.text = getString(R.string.once_you_spend_a_mixed)
        } else {
            binding.image.visibility = View.GONE
            binding.title.text = getString(R.string.you_dont_have_any)
            binding.description.text = getString(R.string.your_mixed_coins_have_been)
        }
    }

    private fun setUpList() {
        viewModel.listRefreshStatus.observe(viewLifecycleOwner, {
            binding.mixSwipeContainer.isRefreshing = it
        })
        binding.mixSwipeContainer.setOnRefreshListener {
          viewModel.refreshList(requireContext())
        }
        val drawable = ContextCompat.getDrawable(requireContext(), R.drawable.divider_grey)
        binding.mixListRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = mixListAdapter
            isNestedScrollingEnabled = true
            layoutParams = LParams(
                LParams.MATCH_PARENT,
                LParams.MATCH_PARENT,
            )
            addItemDecoration(
                DividerItemDecoration(
                    requireContext(),
                    LinearLayoutManager(requireContext()).orientation
                ).apply {
                    drawable?.let { this.setDrawable(it) }
                })
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, containerView: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = WhirlpoolAddCoinsLayoutBinding.inflate(inflater, containerView, false)
        return binding.root
    }

    companion object {
        private const val MIX_TYPE = "MIX_TYPE"

        @JvmStatic
        fun newInstance(param1: MixListType) =
            MixListFragment().apply {
                arguments = Bundle().apply {
                    putString(MIX_TYPE, param1.toString())
                }
            }
    }
}