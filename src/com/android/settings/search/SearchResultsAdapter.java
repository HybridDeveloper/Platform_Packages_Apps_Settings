/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.android.settings.search;

import android.content.Context;
import android.support.annotation.MainThread;
import android.support.annotation.VisibleForTesting;
import android.support.v7.util.DiffUtil;
import android.support.v7.widget.RecyclerView;
import android.util.ArrayMap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.android.settings.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SearchResultsAdapter extends RecyclerView.Adapter<SearchViewHolder> {

    private final SearchFragment mFragment;

    private List<SearchResult> mSearchResults;
    private Map<String, Set<? extends SearchResult>> mResultsMap;
    private final SearchFeatureProvider mSearchFeatureProvider;

    public SearchResultsAdapter(SearchFragment fragment,
            SearchFeatureProvider searchFeatureProvider) {
        mFragment = fragment;
        mSearchResults = new ArrayList<>();
        mResultsMap = new ArrayMap<>();
        mSearchFeatureProvider = searchFeatureProvider;

        setHasStableIds(true);
    }

    @Override
    public SearchViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        final Context context = parent.getContext();
        final LayoutInflater inflater = LayoutInflater.from(context);
        final View view;
        switch (viewType) {
            case ResultPayload.PayloadType.INTENT:
                view = inflater.inflate(R.layout.search_intent_item, parent, false);
                return new IntentSearchViewHolder(view);
            case ResultPayload.PayloadType.INLINE_SWITCH:
                view = inflater.inflate(R.layout.search_inline_switch_item, parent, false);
                return new InlineSwitchViewHolder(view, context);
            case ResultPayload.PayloadType.SAVED_QUERY:
                view = inflater.inflate(R.layout.search_saved_query_item, parent, false);
                return new SavedQueryViewHolder(view);
            default:
                return null;
        }
    }

    @Override
    public void onBindViewHolder(SearchViewHolder holder, int position) {
        holder.onBind(mFragment, mSearchResults.get(position));
    }

    @Override
    public long getItemId(int position) {
        return mSearchResults.get(position).stableId;
    }

    @Override
    public int getItemViewType(int position) {
        return mSearchResults.get(position).viewType;
    }

    @Override
    public int getItemCount() {
        return mSearchResults.size();
    }

    /**
     * Store the results from each of the loaders to be merged when all loaders are finished.
     *
     * @param results         the results from the loader.
     * @param loaderClassName class name of the loader.
     */
    @MainThread
    public void addSearchResults(Set<? extends SearchResult> results, String loaderClassName) {
        if (results == null) {
            return;
        }
        mResultsMap.put(loaderClassName, results);
    }

    /**
     * Displays recent searched queries.
     *
     * @return The number of saved queries to display
     */
    public int displaySavedQuery(List<? extends SearchResult> data) {
        clearResults();
        mSearchResults.addAll(data);
        notifyDataSetChanged();
        return mSearchResults.size();
    }

    /**
     * Merge the results from each of the loaders into one list for the adapter.
     * Prioritizes results from the local database over installed apps.
     *
     * @param query user query corresponding to these results
     * @return Number of matched results
     */
    public int displaySearchResults(String query) {
        List<? extends SearchResult> databaseResults = null;
        List<? extends SearchResult> installedAppResults = null;
        final String dbLoaderKey = DatabaseResultLoader.class.getName();
        final String appLoaderKey = InstalledAppResultLoader.class.getName();
        int dbSize = 0;
        int appSize = 0;
        if (mResultsMap.containsKey(dbLoaderKey)) {
            databaseResults = new ArrayList<>(mResultsMap.get(dbLoaderKey));
            dbSize = databaseResults.size();
            Collections.sort(databaseResults);
        }
        if (mResultsMap.containsKey(appLoaderKey)) {
            installedAppResults = new ArrayList<>(mResultsMap.get(appLoaderKey));
            appSize = installedAppResults.size();
            Collections.sort(installedAppResults);
        }
        final List<SearchResult> newResults = new ArrayList<>(dbSize + appSize);

        int dbIndex = 0;
        int appIndex = 0;
        int rank = SearchResult.TOP_RANK;

        while (rank <= SearchResult.BOTTOM_RANK) {
            while ((dbIndex < dbSize) && (databaseResults.get(dbIndex).rank == rank)) {
                newResults.add(databaseResults.get(dbIndex++));
            }
            while ((appIndex < appSize) && (installedAppResults.get(appIndex).rank == rank)) {
                newResults.add(installedAppResults.get(appIndex++));
            }
            rank++;
        }

        while (dbIndex < dbSize) {
            newResults.add(databaseResults.get(dbIndex++));
        }
        while (appIndex < appSize) {
            newResults.add(installedAppResults.get(appIndex++));
        }

        final boolean isSmartSearchRankingEnabled = mSearchFeatureProvider
                .isSmartSearchRankingEnabled(mFragment.getContext().getApplicationContext());

        if (isSmartSearchRankingEnabled) {
            // TODO: run this in parallel to loading the results if takes too long
            mSearchFeatureProvider.rankSearchResults(query, newResults);
        }

        final DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(
                new SearchResultDiffCallback(mSearchResults, newResults),
                isSmartSearchRankingEnabled);
        mSearchResults = newResults;
        diffResult.dispatchUpdatesTo(this);

        return mSearchResults.size();
    }

    public void clearResults() {
        mSearchResults.clear();
        mResultsMap.clear();
        notifyDataSetChanged();
    }

    @VisibleForTesting
    public List<SearchResult> getSearchResults() {
        return mSearchResults;
    }
}
