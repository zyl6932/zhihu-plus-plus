/*
 * Zhihu++ - Free & Ad-Free Zhihu client for Android.
 * Copyright (C) 2024-2026, zly2006 <i@zly2006.me>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation (version 3 only).
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.github.zly2006.zhihu.viewmodel.feed

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.github.zly2006.zhihu.data.AccountData
import com.github.zly2006.zhihu.data.SearchResult
import com.github.zly2006.zhihu.util.signFetchRequest
import kotlinx.serialization.json.jsonArray
import java.net.URLEncoder

class SearchViewModel(
    val searchQuery: String,
) : BaseFeedViewModel() {
    var sortOption by mutableStateOf(SearchSortOption.Default)
        private set
    var contentType by mutableStateOf(SearchContentType.All)
        private set
    var timeRange by mutableStateOf(SearchTimeRange.All)
        private set

    override val initialUrl: String
        get() {
            val searchSource = if (hasActiveFilter) "Filter" else "Normal"
            val params = buildList {
                add("gk_version" to "gz-gaokao")
                add("t" to "general")
                add("q" to searchQuery)
                add("correction" to "1")
                add("search_source" to searchSource)
                add("limit" to "10")
                add("show_all_topics" to "0")
                if (contentType.value.isNotEmpty()) {
                    add("vertical" to contentType.value)
                    add("vertical_info" to SEARCH_VERTICAL_INFO)
                }
                if (sortOption.value.isNotEmpty()) {
                    add("sort" to sortOption.value)
                }
                if (timeRange.value.isNotEmpty()) {
                    add("time_interval" to timeRange.value)
                }
            }.joinToString("&") { (key, value) ->
                "$key=${URLEncoder.encode(value, "UTF-8")}"
            }
            return "https://www.zhihu.com/api/v4/search_v3?$params"
        }

    // Override include to request necessary fields for search results
    override val include = "data[*].highlight,object,type"

    private val hasActiveFilter: Boolean
        get() = sortOption != SearchSortOption.Default ||
            contentType != SearchContentType.All ||
            timeRange != SearchTimeRange.All

    fun updateSortOption(context: Context, option: SearchSortOption) {
        if (sortOption == option) return
        sortOption = option
        refresh(context)
    }

    fun updateContentType(context: Context, type: SearchContentType) {
        if (contentType == type) return
        contentType = type
        refresh(context)
    }

    fun updateTimeRange(context: Context, range: SearchTimeRange) {
        if (timeRange == range) return
        timeRange = range
        refresh(context)
    }

    override suspend fun fetchFeeds(context: Context) {
        try {
            val url = lastPaging?.next ?: initialUrl
            val jojo = AccountData.fetchGet(context, url) {
                url {
                    parameters["include"] = include
                }
                signFetchRequest()
            }!!
            val jsonArray = jojo["data"]!!.jsonArray

            // Parse search results and convert to Feed objects
            val feeds = jsonArray.mapNotNull { element ->
                try {
                    val searchResult = AccountData.decodeJson<SearchResult>(element)
                    searchResult.toFeed()
                } catch (e: Exception) {
                    Log.e("SearchViewModel", "Failed to decode search result: $element", e)
                    null
                }
            }

            processResponse(context, feeds, jsonArray)

            // Handle pagination
            if ("paging" in jojo) {
                lastPaging = AccountData.decodeJson(jojo["paging"]!!)
            }
        } catch (e: Exception) {
            Log.e("SearchViewModel", "Failed to fetch search results", e)
            throw e
        } finally {
            isLoading = false
        }
    }
}

enum class SearchSortOption(
    val label: String,
    val value: String,
) {
    Default("综合排序", ""),
    Latest("最新发布", "created_time"),
    MostVoted("最多赞同", "upvoted_count"),
}

enum class SearchContentType(
    val label: String,
    val value: String,
) {
    All("全部内容", ""),
    Answer("回答", "answer"),
    Article("文章", "article"),
    Video("视频", "zvideo"),
}

enum class SearchTimeRange(
    val label: String,
    val value: String,
) {
    All("不限时间", ""),
    Day("一天内", "a_day"),
    Week("一周内", "a_week"),
    Month("一个月内", "a_month"),
    ThreeMonths("三个月内", "three_months"),
    HalfYear("半年内", "half_a_year"),
    Year("一年内", "a_year"),
}

private const val SEARCH_VERTICAL_INFO = "0,0,0,0,0,0,0,0,0,0,0,0"
