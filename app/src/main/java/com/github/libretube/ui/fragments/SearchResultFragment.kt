package com.github.libretube.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.navArgs
import androidx.paging.LoadState
import androidx.recyclerview.widget.GridLayoutManager
import com.github.libretube.R
import com.github.libretube.constants.PreferenceKeys
import com.github.libretube.databinding.FragmentSearchResultBinding
import com.github.libretube.db.DatabaseHelper
import com.github.libretube.db.obj.SearchHistoryItem
import com.github.libretube.extensions.ceilHalf
import com.github.libretube.helpers.PreferenceHelper
import com.github.libretube.ui.activities.MainActivity
import com.github.libretube.ui.adapters.SearchResultsAdapter
import com.github.libretube.ui.base.DynamicLayoutManagerFragment
import com.github.libretube.ui.models.SearchResultViewModel
import com.github.libretube.util.TextUtils.toTimeInSeconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

class SearchResultFragment : DynamicLayoutManagerFragment() {
    private var _binding: FragmentSearchResultBinding? = null
    private val binding get() = _binding!!
    private val args by navArgs<SearchResultFragmentArgs>()
    private val viewModel by viewModels<SearchResultViewModel>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSearchResultBinding.inflate(inflater)
        return binding.root
    }

    override fun setLayoutManagers(gridItems: Int) {
        _binding?.searchRecycler?.layoutManager = GridLayoutManager(context, gridItems.ceilHalf())
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // fixes a bug that the search query will stay the old one when searching for multiple
        // different queries in a row and navigating to the previous ones through back presses
        (context as MainActivity).setQuerySilent(args.query)

        // add the query to the history
        addToHistory(args.query)

        // filter options
        binding.filterChipGroup.setOnCheckedStateChangeListener { _, _ ->
            viewModel.setFilter(when (
                binding.filterChipGroup.checkedChipId
            ) {
                R.id.chip_all -> "all"
                R.id.chip_videos -> "videos"
                R.id.chip_channels -> "channels"
                R.id.chip_playlists -> "playlists"
                R.id.chip_music_songs -> "music_songs"
                R.id.chip_music_videos -> "music_videos"
                R.id.chip_music_albums -> "music_albums"
                R.id.chip_music_playlists -> "music_playlists"
                R.id.chip_music_artists -> "music_artists"
                else -> throw IllegalArgumentException("Filter out of range")
            })
        }

        val timeStamp = args.query.toHttpUrlOrNull()?.queryParameter("t")?.toTimeInSeconds()
        val searchResultsAdapter = SearchResultsAdapter(timeStamp ?: 0)
        binding.searchRecycler.adapter = searchResultsAdapter

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                searchResultsAdapter.loadStateFlow.collect {
                    val isLoading = it.source.refresh is LoadState.Loading
                    binding.progress.isVisible = isLoading
                    binding.searchResultsLayout.isGone = isLoading
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.searchResultsFlow.collectLatest {
                    searchResultsAdapter.submitData(it)
                }
            }
        }
    }

    private fun addToHistory(query: String) {
        val searchHistoryEnabled =
            PreferenceHelper.getBoolean(PreferenceKeys.SEARCH_HISTORY_TOGGLE, true)
        if (searchHistoryEnabled && query.isNotEmpty()) {
            lifecycleScope.launch(Dispatchers.IO) {
                DatabaseHelper.addToSearchHistory(SearchHistoryItem(query.trim()))
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
