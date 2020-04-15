/*
 * Copyright 2020 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app.tivi.showdetails.details.view

import android.view.ViewGroup
import androidx.animation.transitionDefinition
import androidx.compose.Composable
import androidx.compose.MutableState
import androidx.compose.Providers
import androidx.compose.mutableStateOf
import androidx.compose.remember
import androidx.compose.state
import androidx.compose.staticAmbientOf
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.ui.animation.DpPropKey
import androidx.ui.animation.Transition
import androidx.ui.core.Alignment
import androidx.ui.core.ContentScale
import androidx.ui.core.DensityAmbient
import androidx.ui.core.Modifier
import androidx.ui.core.drawOpacity
import androidx.ui.core.onPositioned
import androidx.ui.core.positionInRoot
import androidx.ui.foundation.Box
import androidx.ui.foundation.Clickable
import androidx.ui.foundation.HorizontalScroller
import androidx.ui.foundation.Icon
import androidx.ui.foundation.ScrollerPosition
import androidx.ui.foundation.Text
import androidx.ui.foundation.VerticalScroller
import androidx.ui.foundation.contentColor
import androidx.ui.foundation.drawBackground
import androidx.ui.foundation.drawBorder
import androidx.ui.foundation.shape.corner.RoundedCornerShape
import androidx.ui.graphics.Color
import androidx.ui.layout.Column
import androidx.ui.layout.FlowRow
import androidx.ui.layout.Row
import androidx.ui.layout.SizeMode
import androidx.ui.layout.Spacer
import androidx.ui.layout.Stack
import androidx.ui.layout.aspectRatio
import androidx.ui.layout.fillMaxWidth
import androidx.ui.layout.padding
import androidx.ui.layout.preferredHeight
import androidx.ui.layout.preferredHeightIn
import androidx.ui.layout.preferredSize
import androidx.ui.layout.preferredSizeIn
import androidx.ui.layout.preferredWidth
import androidx.ui.layout.wrapContentHeight
import androidx.ui.layout.wrapContentSize
import androidx.ui.material.Card
import androidx.ui.material.EmphasisAmbient
import androidx.ui.material.IconButton
import androidx.ui.material.LinearProgressIndicator
import androidx.ui.material.MaterialTheme
import androidx.ui.material.ProvideEmphasis
import androidx.ui.material.Surface
import androidx.ui.material.TopAppBar
import androidx.ui.material.icons.Icons
import androidx.ui.material.icons.filled.ArrowBack
import androidx.ui.material.icons.filled.MoreVert
import androidx.ui.material.icons.filled.Star
import androidx.ui.material.ripple.ripple
import androidx.ui.res.stringResource
import androidx.ui.unit.Dp
import androidx.ui.unit.IntPx
import androidx.ui.unit.dp
import app.tivi.common.compose.ExpandingSummary
import app.tivi.common.compose.InsetsAmbient
import app.tivi.common.compose.InsetsHolder
import app.tivi.common.compose.LoadNetworkImageWithCrossfade
import app.tivi.common.compose.MaterialThemeFromAndroidTheme
import app.tivi.common.compose.PopupMenu
import app.tivi.common.compose.PopupMenuItem
import app.tivi.common.compose.VectorImage
import app.tivi.common.compose.WrapWithAmbients
import app.tivi.common.compose.observe
import app.tivi.common.compose.observeInsets
import app.tivi.common.compose.paddingHV
import app.tivi.common.compose.setContentWithLifecycle
import app.tivi.common.imageloading.TrimTransparentEdgesTransformation
import app.tivi.data.entities.Episode
import app.tivi.data.entities.ImageType
import app.tivi.data.entities.Season
import app.tivi.data.entities.ShowTmdbImage
import app.tivi.data.entities.TiviShow
import app.tivi.data.entities.findHighestRatedPoster
import app.tivi.data.resultentities.EpisodeWithWatches
import app.tivi.data.resultentities.RelatedShowEntryWithShow
import app.tivi.data.resultentities.SeasonWithEpisodesAndWatches
import app.tivi.data.resultentities.numberAired
import app.tivi.data.resultentities.numberWatched
import app.tivi.data.views.FollowedShowsWatchStats
import app.tivi.showdetails.details.ChangeSeasonExpandedAction
import app.tivi.showdetails.details.ClearPendingUiEffect
import app.tivi.showdetails.details.FocusSeasonUiEffect
import app.tivi.showdetails.details.OpenEpisodeDetails
import app.tivi.showdetails.details.OpenShowDetails
import app.tivi.showdetails.details.ShowDetailsAction
import app.tivi.showdetails.details.ShowDetailsViewState
import app.tivi.showdetails.details.UiEffect
import app.tivi.ui.animations.lerp
import app.tivi.util.TiviDateFormatter
import coil.transform.RoundedCornersTransformation

val ShowDetailsTextCreatorAmbient = staticAmbientOf<ShowDetailsTextCreator>()

fun ViewGroup.composeShowDetails(
    lifecycleOwner: LifecycleOwner,
    state: LiveData<ShowDetailsViewState>,
    pendingUiEffects: LiveData<List<UiEffect>>,
    insets: LiveData<WindowInsetsCompat>,
    actioner: (ShowDetailsAction) -> Unit,
    tiviDateFormatter: TiviDateFormatter,
    textCreator: ShowDetailsTextCreator
): Any = setContentWithLifecycle(lifecycleOwner) {
    WrapWithAmbients(tiviDateFormatter, InsetsHolder()) {
        Providers(ShowDetailsTextCreatorAmbient provides textCreator) {
            observeInsets(insets)

            val viewState = observe(state)
            val uiEffects = observe(pendingUiEffects) ?: emptyList()
            if (viewState != null) {
                MaterialThemeFromAndroidTheme(context) {
                    ShowDetails(viewState, uiEffects, actioner)
                }
            }
        }
    }
}

@Composable
fun ShowDetails(
    viewState: ShowDetailsViewState,
    uiEffects: List<UiEffect>,
    actioner: (ShowDetailsAction) -> Unit
) = Stack {
    val scrollerPosition = ScrollerPosition()
    val backdropHeight = mutableStateOf(IntPx.Zero)

    VerticalScroller(scrollerPosition = scrollerPosition) {
        Column {
            val backdropImage = viewState.backdropImage
            Surface(
                modifier = Modifier.aspectRatio(16f / 10)
                    .onPositioned { backdropHeight.value = it.size.height }
            ) {
                Stack {
                    if (backdropImage != null) {
                        LoadNetworkImageWithCrossfade(
                            backdropImage,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.matchParentSize()
                        )
                    }
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth().wrapContentHeight(Alignment.Top),
                elevation = 2.dp
            ) {
                Column {
                    ShowDetailsAppBar(
                        show = viewState.show,
                        elevation = 0.dp,
                        backgroundColor = Color.Transparent
                    )

                    Row {
                        val poster = viewState.posterImage
                        if (poster != null) {
                            Spacer(modifier = Modifier.preferredWidth(16.dp))

                            val cornerRadius = with(DensityAmbient.current) { 4.dp.toPx() }
                            val transforms = remember {
                                listOf(RoundedCornersTransformation(cornerRadius.value))
                            }

                            LoadNetworkImageWithCrossfade(
                                poster,
                                transformations = transforms,
                                alignment = Alignment.TopStart,
                                modifier = Modifier.weight(1f, fill = false)
                                    .aspectRatio(2 / 3f)
                            )
                        }

                        Spacer(modifier = Modifier.preferredWidth(16.dp))

                        Box(Modifier.weight(1f, fill = false)) {
                            FlowRow(
                                mainAxisSize = SizeMode.Expand,
                                mainAxisSpacing = 8.dp,
                                crossAxisSpacing = 8.dp
                            ) {
                                ProvideEmphasis(EmphasisAmbient.current.high) {
                                    val show = viewState.show
                                    if (show.traktRating != null) {
                                        TraktRatingInfoPanel(show)
                                    }
                                    if (show.network != null || show.networkLogoPath != null) {
                                        NetworkInfoPanel(viewState.show)
                                    }
                                    if (show.certification != null) {
                                        CertificateInfoPanel(viewState.show)
                                    }
                                    if (show.runtime != null) {
                                        RuntimeInfoPanel(viewState.show)
                                    }
                                    if (show.airsDay != null && show.airsTime != null &&
                                        show.airsTimeZone != null) {
                                        AirsInfoPanel(viewState.show)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.preferredWidth(16.dp))
                    }

                    Spacer(modifier = Modifier.preferredHeight(16.dp))

                    Header(stringResource(R.string.details_about))

                    if (viewState.show.summary != null) {
                        ExpandingSummary(
                            viewState.show.summary!!,
                            modifier = Modifier.paddingHV(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }

                    val genres = viewState.show.genres
                    if (genres.isNotEmpty()) {
                        ProvideEmphasis(EmphasisAmbient.current.high) {
                            val textCreator = ShowDetailsTextCreatorAmbient.current
                            Text(
                                textCreator.genreString(genres).toString(),
                                style = MaterialTheme.typography.body2,
                                modifier = Modifier.paddingHV(horizontal = 16.dp, vertical = 8.dp)
                                    .fillMaxWidth()
                            )
                        }
                    }

                    val nextEpisodeToWatch = viewState.nextEpisodeToWatch()
                    if (nextEpisodeToWatch?.episode != null && nextEpisodeToWatch.season != null) {
                        Spacer(modifier = Modifier.preferredHeight(8.dp))

                        Header(stringResource(id = R.string.details_next_episode_to_watch))

                        NextEpisodeToWatch(
                            season = nextEpisodeToWatch.season!!,
                            episode = nextEpisodeToWatch.episode!!,
                            onClick = {
                                actioner(OpenEpisodeDetails(nextEpisodeToWatch.episode!!.id))
                            }
                        )
                    }

                    val relatedShows = viewState.relatedShows() ?: emptyList()
                    if (relatedShows.isNotEmpty()) {
                        Spacer(modifier = Modifier.preferredHeight(8.dp))
                        Header(stringResource(R.string.details_related))
                        RelatedShows(relatedShows, actioner)
                    }

                    val viewStats = viewState.viewStats()
                    if (viewStats != null) {
                        Spacer(modifier = Modifier.preferredHeight(8.dp))
                        Header(stringResource(R.string.details_view_stats))
                        WatchStats(viewStats)
                    }

                    val seasons = viewState.seasons()
                    if (seasons != null && seasons.isNotEmpty()) {
                        Spacer(modifier = Modifier.preferredHeight(8.dp))
                        Header(stringResource(R.string.show_details_seasons))
                        Seasons(
                            seasons,
                            viewState.expandedSeasonIds,
                            actioner,
                            scrollerPosition,
                            uiEffects.firstOrNull { it is FocusSeasonUiEffect } as? FocusSeasonUiEffect
                        )
                    }

                    // Spacer to push up the content from under the navigation bar
                    val insets = InsetsAmbient.current
                    val spacerHeight = with(DensityAmbient.current) { 8.dp + insets.bottom.toDp() }
                    Spacer(Modifier.preferredHeight(spacerHeight))
                }
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth().gravity(Alignment.TopStart)
    ) {
        val insets = InsetsAmbient.current

        val trigger = (backdropHeight.value - insets.top).coerceAtLeast(IntPx.Zero).value

        if (insets.top > IntPx.Zero) {
            val topInset = with(DensityAmbient.current) { insets.top.toDp() }

            val alpha = lerp(
                startValue = 0.5f,
                endValue = 1f,
                fraction = if (trigger > 0) {
                    (scrollerPosition.value / trigger).coerceIn(0f, 1f)
                } else 0f
            )
            Box(
                Modifier.preferredHeight(topInset)
                    .fillMaxWidth()
                    .drawBackground(color = MaterialTheme.colors.background.copy(alpha = alpha))
            )
        }

        val showOverlayAppBar = mutableStateOf(true)

        showOverlayAppBar.value = scrollerPosition.value > trigger

        val transition = remember {
            transitionDefinition {
                state(true) {
                    this[elevationPropKey] = 4.dp
                }
                state(false) {
                    this[elevationPropKey] = 2.dp
                }

                transition {
                    elevationPropKey using tween { duration = 200 }
                }
            }
        }

        Transition(
            definition = transition,
            toState = showOverlayAppBar.value
        ) { transitionState ->
            if (showOverlayAppBar.value) {
                ShowDetailsAppBar(
                    show = viewState.show,
                    elevation = transitionState[elevationPropKey],
                    backgroundColor = MaterialTheme.colors.surface,
                    modifier = Modifier.drawOpacity(if (showOverlayAppBar.value) 1f else 0f)
                )
            }
        }
    }
}

private val elevationPropKey = DpPropKey()

@Composable
private fun NetworkInfoPanel(
    show: TiviShow,
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        Text(
            text = stringResource(R.string.network_title),
            style = MaterialTheme.typography.subtitle2
        )

        Spacer(Modifier.preferredHeight(4.dp))

        val networkLogoPath = show.networkLogoPath
        val networkName = show.network

        if (networkLogoPath != null) {
            val tmdbImage = remember(networkLogoPath) {
                ShowTmdbImage(path = networkLogoPath, type = ImageType.LOGO, showId = 0)
            }
            val transforms = remember {
                listOf(TrimTransparentEdgesTransformation)
            }

            LoadNetworkImageWithCrossfade(
                tmdbImage,
                transformations = transforms,
                contentScale = ContentScale.Fit,
                alignment = Alignment.TopStart,
                modifier = Modifier.preferredSizeIn(maxWidth = 72.dp, maxHeight = 32.dp)
            )
        } else if (networkName != null) {
            Text(
                text = networkName,
                style = MaterialTheme.typography.body2
            )
        }
    }
}

@Composable
private fun RuntimeInfoPanel(
    show: TiviShow,
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        Text(
            text = stringResource(R.string.runtime_title),
            style = MaterialTheme.typography.subtitle2
        )

        Spacer(Modifier.preferredHeight(4.dp))

        Text(
            text = stringResource(R.string.minutes_format, show.runtime ?: 0),
            style = MaterialTheme.typography.body2
        )
    }
}

@Composable
private fun AirsInfoPanel(
    show: TiviShow,
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        Text(
            text = stringResource(R.string.airs_title),
            style = MaterialTheme.typography.subtitle2
        )

        Spacer(Modifier.preferredHeight(4.dp))

        val textCreator = ShowDetailsTextCreatorAmbient.current
        Text(
            text = textCreator.airsText(show)?.toString() ?: "No air date",
            style = MaterialTheme.typography.body2
        )
    }
}

@Composable
private fun CertificateInfoPanel(
    show: TiviShow,
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        Text(
            text = stringResource(R.string.certificate_title),
            style = MaterialTheme.typography.subtitle2
        )

        Spacer(Modifier.preferredHeight(4.dp))

        Text(
            text = show.certification ?: "No certificate",
            style = MaterialTheme.typography.body2,
            modifier = Modifier.drawBorder(
                size = 1.dp,
                color = MaterialTheme.colors.onSurface,
                shape = RoundedCornerShape(2.dp)
            ).paddingHV(horizontal = 4.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun TraktRatingInfoPanel(
    show: TiviShow,
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        Text(
            text = stringResource(R.string.trakt_rating_title),
            style = MaterialTheme.typography.subtitle2
        )

        Spacer(Modifier.preferredHeight(4.dp))

        Row {
            VectorImage(
                vector = Icons.Default.Star,
                contentScale = ContentScale.Inside,
                tintColor = MaterialTheme.colors.secondaryVariant,
                modifier = Modifier.preferredSize(32.dp)
            )

            Spacer(Modifier.preferredWidth(4.dp))

            Column {
                Text(
                    text = stringResource(R.string.trakt_rating_text,
                        (show.traktRating ?: 0f) * 10f),
                    style = MaterialTheme.typography.body2
                )

                Text(
                    text = stringResource(R.string.trakt_rating_votes,
                        (show.traktVotes ?: 0) / 1000f),
                    style = MaterialTheme.typography.caption
                )
            }
        }
    }
}

@Composable
private fun Header(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.subtitle1,
        modifier = Modifier.paddingHV(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun RelatedShows(
    related: List<RelatedShowEntryWithShow>,
    actioner: (ShowDetailsAction) -> Unit,
    modifier: Modifier = Modifier
) {
    // TODO: ideally we would use AdapterList here, but it only works for vertical lists, not
    // horizontal

    HorizontalScroller(modifier = modifier.paddingHV(vertical = 8.dp)) {
        Row(modifier.paddingHV(horizontal = 16.dp)) {
            related.forEachIndexed { index, relatedEntry ->
                val poster = relatedEntry.images.findHighestRatedPoster()
                if (poster != null) {
                    Card {
                        Clickable(
                            onClick = { actioner(OpenShowDetails(relatedEntry.show.id)) },
                            modifier = Modifier.ripple()
                        ) {
                            LoadNetworkImageWithCrossfade(
                                poster,
                                modifier = Modifier.aspectRatio(2 / 3f).preferredWidth(64.dp)
                            )
                        }
                    }
                    if (index + 1 < related.size) {
                        // Add a spacer if there are still more items to add
                        Spacer(modifier = Modifier.preferredWidth(4.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun NextEpisodeToWatch(
    season: Season,
    episode: Episode,
    onClick: () -> Unit
) {
    Clickable(onClick = onClick, modifier = Modifier.ripple()) {
        Column(
            modifier = Modifier.paddingHV(16.dp, 8.dp)
                .fillMaxWidth()
                .preferredHeightIn(minHeight = 48.dp)
                .wrapContentSize(Alignment.CenterStart)
        ) {
            val textCreator = ShowDetailsTextCreatorAmbient.current

            Text(
                textCreator.seasonEpisodeTitleText(season, episode),
                style = MaterialTheme.typography.caption
            )

            Spacer(modifier = Modifier.preferredHeight(4.dp))

            Text(
                episode.title ?: stringResource(R.string.episode_title_fallback, episode.number!!),
                style = MaterialTheme.typography.body1
            )
        }
    }
}

@Composable
private fun WatchStats(stats: FollowedShowsWatchStats) {
    Column(
        modifier = Modifier.fillMaxWidth()
            .padding(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 8.dp)
    ) {
        LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth(),
            progress = stats.watchedEpisodeCount / stats.episodeCount.toFloat()
        )

        Spacer(modifier = Modifier.preferredHeight(8.dp))

        val textCreator = ShowDetailsTextCreatorAmbient.current

        // TODO: Do something better with CharSequences containing markup/spans
        Text(
            text = textCreator.followedShowEpisodeWatchStatus(stats).toString(),
            style = MaterialTheme.typography.body2
        )
    }
}

@Composable
private fun Seasons(
    seasons: List<SeasonWithEpisodesAndWatches>,
    expandedSeasonIds: Set<Long>,
    actioner: (ShowDetailsAction) -> Unit,
    scrollerPosition: ScrollerPosition,
    pendingFocusSeasonUiEffect: FocusSeasonUiEffect? = null
) {
    val onSeasonClicked = { season: Season ->
        actioner(ChangeSeasonExpandedAction(season.id, season.id !in expandedSeasonIds))
    }
    val onEpisodeClicked = { episode: Episode ->
        actioner(OpenEpisodeDetails(episode.id))
    }

    seasons.forEach {
        val mod = if (pendingFocusSeasonUiEffect != null &&
            pendingFocusSeasonUiEffect.seasonId == it.season.id &&
            !scrollerPosition.isAnimating) {

            // Offset, to not scroll the item under the status bar, and leave a gap
            val offset = InsetsAmbient.current.top +
                with(DensityAmbient.current) { 56.dp.toIntPx() }

            Modifier.onPositioned { coords ->
                val targetY = coords.positionInRoot.y.value +
                    scrollerPosition.value -
                    offset.value

                scrollerPosition.smoothScrollTo(targetY) { _, _ ->
                    actioner(ClearPendingUiEffect(pendingFocusSeasonUiEffect))
                }
            }
        } else Modifier

        SeasonWithEpisodesRow(
            it.season,
            it.episodes,
            it.season.id in expandedSeasonIds,
            onSeasonClicked,
            onEpisodeClicked,
            mod.fillMaxWidth()
        )
    }
}

@Composable
private fun SeasonWithEpisodesRow(
    season: Season,
    episodes: List<EpisodeWithWatches>,
    expanded: Boolean,
    onSeasonClicked: (Season) -> Unit,
    onEpisodeClicked: (Episode) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        elevation = if (expanded) 2.dp else 0.dp,
        modifier = modifier
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (expanded) VerticalDivider()

            Clickable(
                onClick = { onSeasonClicked(season) },
                modifier = Modifier.ripple()
            ) {
                SeasonRow(
                    season,
                    episodes,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (expanded) {
                episodes.forEach { episodeEntry ->
                    Clickable(
                        onClick = { onEpisodeClicked(episodeEntry.episode!!) },
                        modifier = Modifier.ripple()
                    ) {
                        EpisodeWithWatchesRow(
                            episodeEntry,
                            Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SeasonRow(
    season: Season,
    episodesWithWatches: List<EpisodeWithWatches>,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.preferredHeightIn(minHeight = 48.dp)
            .wrapContentHeight(Alignment.CenterVertically)
            .padding(start = 16.dp, top = 12.dp, bottom = 12.dp)
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            val textCreator = ShowDetailsTextCreatorAmbient.current

            val emphasis = when {
                season.ignored -> EmphasisAmbient.current.disabled
                else -> EmphasisAmbient.current.high
            }
            ProvideEmphasis(emphasis) {
                Text(
                    text = season.title
                        ?: stringResource(R.string.season_title_fallback, season.number!!),
                    style = MaterialTheme.typography.body1
                )

                Spacer(Modifier.preferredHeight(4.dp))

                Text(
                    text = textCreator.seasonSummaryText(episodesWithWatches).toString(),
                    style = MaterialTheme.typography.caption
                )
            }

            if (!season.ignored) {
                Spacer(Modifier.preferredHeight(4.dp))

                LinearProgressIndicator(
                    episodesWithWatches.numberWatched / episodesWithWatches.size.toFloat(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        val showPopup = state { false }
        SeasonRowOverflowMenu(season, episodesWithWatches, showPopup)

        IconButton(onClick = { showPopup.value = true }) {
            Icon(Icons.Default.MoreVert)
        }
    }
}

@Composable
private fun EpisodeWithWatchesRow(
    episodeWithWatches: EpisodeWithWatches,
    modifier: Modifier = Modifier
) {
    val episode = episodeWithWatches.episode!!

    Row(
        modifier = modifier.preferredHeightIn(minHeight = 48.dp)
            .wrapContentHeight(Alignment.CenterVertically)
            .paddingHV(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            val textCreator = ShowDetailsTextCreatorAmbient.current

            ProvideEmphasis(EmphasisAmbient.current.high) {
                Text(
                    text = textCreator.episodeNumberText(episode).toString(),
                    style = MaterialTheme.typography.caption
                )

                Spacer(Modifier.preferredHeight(2.dp))

                Text(
                    text = episode.title
                        ?: stringResource(R.string.episode_title_fallback, episode.number!!),
                    style = MaterialTheme.typography.body2
                )
            }
        }

        ProvideEmphasis(EmphasisAmbient.current.medium) {
            var needSpacer = false
            if (episodeWithWatches.hasPending()) {
                VectorImage(
                    Icons.Default.Star,
                    modifier = Modifier.gravity(Alignment.CenterVertically)
                )
                needSpacer = true
            }
            if (episodeWithWatches.isWatched()) {
                if (needSpacer) {
                    Spacer(Modifier.preferredWidth(4.dp))
                }
                VectorImage(
                    id = when {
                        episodeWithWatches.onlyPendingDeletes() -> R.drawable.ic_eye_off_24dp
                        else -> R.drawable.ic_eye_24dp
                    },
                    modifier = Modifier.gravity(Alignment.CenterVertically)
                )
            }
        }
    }
}

@Composable
private fun VerticalDivider(
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.preferredHeight(Dp.Hairline)
        .fillMaxWidth()
        .drawBackground(contentColor().copy(alpha = 0.15f)))
}

@Composable
private fun SeasonRowOverflowMenu(
    season: Season,
    episodesWithWatches: List<EpisodeWithWatches>,
    popupVisible: MutableState<Boolean>
) {
    val items = ArrayList<PopupMenuItem>()

    items += if (season.ignored) {
        PopupMenuItem(
            title = stringResource(id = R.string.popup_season_follow)
        )
    } else {
        PopupMenuItem(
            title = stringResource(id = R.string.popup_season_ignore)
        )
    }

    // Season number starts from 1, rather than 0
    if (season.number ?: -100 >= 2) {
        items += PopupMenuItem(
            title = stringResource(id = R.string.popup_season_ignore_previous)
        )
    }

    if (episodesWithWatches.numberWatched > 0) {
        items += PopupMenuItem(
            title = stringResource(id = R.string.popup_season_mark_all_unwatched)
        )
    }

    if (episodesWithWatches.numberWatched < episodesWithWatches.size) {
        items += PopupMenuItem(
            title = stringResource(id = R.string.popup_season_mark_watched_all)
        )
    }

    if (episodesWithWatches.numberWatched < episodesWithWatches.numberAired &&
        episodesWithWatches.numberAired < episodesWithWatches.size) {
        items += PopupMenuItem(
            title = stringResource(id = R.string.popup_season_mark_watched_aired)
        )
    }

    PopupMenu(
        items = items,
        visible = popupVisible,
        alignment = Alignment.CenterEnd
    )
}

@Composable
private fun ShowDetailsAppBar(
    show: TiviShow,
    elevation: Dp,
    backgroundColor: Color,
    modifier: Modifier = Modifier
) {
    TopAppBar(
        title = {
            Text(text = show.title ?: "")
        },
        navigationIcon = {
            IconButton(onClick = { /* TODO */ }) {
                Icon(Icons.Default.ArrowBack)
            }
        },
        elevation = elevation,
        backgroundColor = backgroundColor,
        modifier = modifier
    )
}