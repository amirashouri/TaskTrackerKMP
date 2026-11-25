package com.tasktracker.featureprojectspresentation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tasktracker.core.EpicID
import com.tasktracker.core.ProjectID
import com.tasktracker.designsystem.LocalReducedMotion
import com.tasktracker.designsystem.LocalTabBarScrollUnderInset
import com.tasktracker.designsystem.TTGlassSegmentedPicker
import com.tasktracker.designsystem.TTGlassButtonStyle
import com.tasktracker.designsystem.TTSwipeToRevealRow
import com.tasktracker.designsystem.ttAccent
import com.tasktracker.designsystem.ttActionIcons
import com.tasktracker.designsystem.ttBanner
import com.tasktracker.designsystem.ttBannerTone
import com.tasktracker.designsystem.ttError
import com.tasktracker.designsystem.ttGlassButton
import com.tasktracker.designsystem.ttGlassIconButton
import com.tasktracker.designsystem.ttGlassSurface
import com.tasktracker.designsystem.ttIcon
import com.tasktracker.designsystem.ttIconSize
import com.tasktracker.designsystem.ttLabel
import com.tasktracker.designsystem.ttLabelSecondary
import com.tasktracker.designsystem.ttRadius
import com.tasktracker.designsystem.ttSearchField
import com.tasktracker.designsystem.ttSpacing
import com.tasktracker.designsystem.ttText
import com.tasktracker.designsystem.ttTypography
import com.tasktracker.resources.Res
import com.tasktracker.resources.common_cancel
import com.tasktracker.resources.common_delete
import com.tasktracker.resources.project_already_archived
import com.tasktracker.resources.project_archived_notice_counted
import com.tasktracker.resources.project_deleted_notice
import com.tasktracker.resources.project_new
import com.tasktracker.resources.project_remove_archive
import com.tasktracker.resources.project_remove_body_clear
import com.tasktracker.resources.project_remove_body_outstanding
import com.tasktracker.resources.project_remove_title
import com.tasktracker.designsystem.ttEntityIcons
import com.tasktracker.designsystem.ttTabIcons
import com.tasktracker.resources.epic_new
import com.tasktracker.resources.epic_empty_body
import com.tasktracker.resources.epic_empty_title
import com.tasktracker.resources.epic_search_close
import com.tasktracker.resources.epic_search_empty_body
import com.tasktracker.resources.epic_search_empty_title
import com.tasktracker.resources.epic_search_open
import com.tasktracker.resources.epic_search_placeholder
import com.tasktracker.resources.projects_segment_epics
import com.tasktracker.resources.projects_segment_projects
import com.tasktracker.resources.projects_empty_body
import com.tasktracker.resources.projects_empty_title
import com.tasktracker.resources.tab_projects
import org.jetbrains.compose.resources.stringResource

/**
 * Main project list screen supporting swipe-to-reveal actions and contextual removal confirmations
 * (distinguishing between project archival and permanent deletion based on outstanding tasks).
 */
@Composable
fun ProjectsListScreen(
    rows: List<ProjectRow>,
    notice: RemovalNotice?,
    isLoading: Boolean,
    errorMessage: String?,
    onOpen: (ProjectID) -> Unit,
    onConfirmRemove: (ProjectID) -> Unit,
    onDismissRemove: () -> Unit,
    onDismissNotice: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
    onNewProject: (() -> Unit)? = null,
    onEditProject: (ProjectID) -> Unit = {},
    /** Which half of the tab is showing; held by the shell so a tab switch cannot reset it. */
    segment: ProjectsSegment = ProjectsSegment.Projects,
    onSegmentChanged: (ProjectsSegment) -> Unit = {},
    epicRows: List<EpicListRow> = emptyList(),
    epicsLoading: Boolean = false,
    onNewEpic: (() -> Unit)? = null,
    onOpenEpic: (EpicID) -> Unit = {},
    onEditEpic: (EpicID) -> Unit = {},
    onArchiveEpic: (EpicID, Boolean) -> Unit = { _, _ -> },
    onUnarchiveEpic: (EpicID) -> Unit = {},
) {
    var pendingRemovalID by remember { mutableStateOf<ProjectID?>(null) }
    var pendingArchiveID by remember { mutableStateOf<EpicID?>(null) }
    // The field is local screen state, as it is on the backlog. Keying it to the segment means a
    // query cannot outlive the Epic list it narrows and hide rows after every visible control
    // that explains why has gone.
    var epicQuery by remember(segment) { mutableStateOf("") }
    var epicSearchOpen by remember(segment) { mutableStateOf(false) }
    val appliedEpicQuery = rememberDebouncedQuery(epicQuery)
    val focusManager = LocalFocusManager.current
    val shownEpicRows = remember(epicRows, appliedEpicQuery) {
        if (appliedEpicQuery.isBlank()) {
            epicRows
        } else {
            epicRows.filter { it.matchesSearch(appliedEpicQuery) }
        }
    }
    val tabBarInset = LocalTabBarScrollUnderInset.current
    // One pace for the whole segment swap: the header and the list it names move together.
    val reducedMotion = LocalReducedMotion.current
    // The add action creates for the segment that is showing, so it is only offered when that
    // segment has an editor; a button for the other list would open a form the user did not ask for.
    val canCreate = when (segment) {
        ProjectsSegment.Projects -> onNewProject != null
        ProjectsSegment.Epics -> onNewEpic != null
    }

    BoxWithConstraints(
        modifier = modifier.fillMaxSize().testTag(ProjectsListScreenTag),
    ) {
        val isWide = maxWidth >= ProjectsWideMinimumWidth
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = ttSpacing.xxl)
                .padding(top = ttSpacing.screenGutterLarge),
            verticalArrangement = Arrangement.spacedBy(ttSpacing.lg),
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                // Same arrangement as the backlog header: a wide window puts the segment on the
                // title's row, centred in the space the title and the add action leave; below
                // this width the title keeps the row and the segment gets its own full-width
                // row, where the picker's own cap lets it span the padded column.
                val wideHeader = maxWidth >= ProjectsHeaderWideMinimumWidth
                val segmentPicker: @Composable (Modifier) -> Unit = { pickerModifier ->
                    TTGlassSegmentedPicker(
                        options = ProjectsSegment.entries,
                        selection = segment,
                        onSelect = onSegmentChanged,
                        modifier = pickerModifier.testTag(ProjectsSegmentTag),
                        testTag = { ProjectsSegmentOptionTag(it) },
                        title = {
                            stringResource(
                                when (it) {
                                    ProjectsSegment.Projects -> Res.string.projects_segment_projects
                                    ProjectsSegment.Epics -> Res.string.projects_segment_epics
                                },
                            )
                        },
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(ttSpacing.lg)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(ttSpacing.md),
                    ) {
                        // The glyph and the title are one statement about which half is on
                        // screen, so they swap as a unit: an "Epics" glyph over a "Projects"
                        // title, even for a frame, is the page disagreeing with itself.
                        AnimatedContent(
                            targetState = segment,
                            // On a wide header the group keeps its natural width; on a compact
                            // one it takes the row's spare space so the controls stay trailing.
                            modifier = if (wideHeader) Modifier else Modifier.weight(1f),
                            transitionSpec = {
                                if (reducedMotion) {
                                    ContentTransform(
                                        targetContentEnter = EnterTransition.None,
                                        initialContentExit = ExitTransition.None,
                                        sizeTransform = SizeTransform(clip = true) { _, _ -> snap() },
                                    )
                                } else {
                                    ContentTransform(
                                        targetContentEnter =
                                            fadeIn(
                                                animationSpec = tween(SEGMENT_SWAP_FADE_MILLIS),
                                            ) + slideInVertically(
                                                animationSpec = tween(SEGMENT_SWAP_TRAVEL_MILLIS),
                                            ) { it / 4 },
                                        initialContentExit =
                                            fadeOut(
                                                animationSpec = tween(SEGMENT_SWAP_FADE_MILLIS),
                                            ) + slideOutVertically(
                                                animationSpec = tween(SEGMENT_SWAP_TRAVEL_MILLIS),
                                            ) { -it / 4 },
                                        sizeTransform = SizeTransform(clip = true) { _, _ ->
                                            tween(SEGMENT_SWAP_TRAVEL_MILLIS)
                                        },
                                    )
                                }
                            },
                            label = "projects-segment-header",
                        ) { shown ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(ttSpacing.md),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                // The glyph names the half on screen, so the header says which
                                // entity the list below is made of before the words are read.
                                ttIcon(
                                    image = when (shown) {
                                        ProjectsSegment.Projects -> ttEntityIcons.project
                                        ProjectsSegment.Epics -> ttEntityIcons.epic
                                    },
                                    contentDescription = null,
                                    tint = ttAccent(),
                                    modifier = Modifier.size(ttIconSize.tab),
                                )
                                ttText(
                                    // The header names the half on screen, not just the tab: an
                                    // "Epics" list under a "Projects" title would be the page
                                    // disagreeing with its own segment.
                                    text = stringResource(
                                        when (shown) {
                                            ProjectsSegment.Projects -> Res.string.tab_projects
                                            ProjectsSegment.Epics -> Res.string.projects_segment_epics
                                        },
                                    ),
                                    style = ttTypography.largeTitle,
                                    color = ttLabel(),
                                )
                            }
                        }
                        if (wideHeader) {
                            Box(
                                modifier = Modifier.weight(1f),
                                contentAlignment = Alignment.Center,
                            ) {
                                segmentPicker(Modifier)
                            }
                        }
                        if (segment == ProjectsSegment.Epics) {
                            ttGlassIconButton(
                                image = ttActionIcons.search,
                                contentDescription = stringResource(
                                    if (epicSearchOpen) {
                                        Res.string.epic_search_close
                                    } else {
                                        Res.string.epic_search_open
                                    },
                                ),
                                onClick = {
                                    epicSearchOpen = !epicSearchOpen
                                    if (!epicSearchOpen) {
                                        epicQuery = ""
                                        focusManager.clearFocus()
                                    }
                                },
                                tint = if (epicSearchOpen) ttAccent() else ttLabel(),
                                modifier = Modifier.testTag(ProjectsEpicSearchToggleTag),
                            )
                        }
                        if (canCreate) {
                            ttGlassIconButton(
                                image = ttActionIcons.add,
                                contentDescription = stringResource(
                                    when (segment) {
                                        ProjectsSegment.Projects -> Res.string.project_new
                                        ProjectsSegment.Epics -> Res.string.epic_new
                                    },
                                ),
                                onClick = {
                                    when (segment) {
                                        ProjectsSegment.Projects -> onNewProject?.invoke()
                                        ProjectsSegment.Epics -> onNewEpic?.invoke()
                                    }
                                },
                                modifier = Modifier.testTag(ProjectsListNewTag),
                            )
                        }
                    }
                    if (!wideHeader) {
                        segmentPicker(Modifier)
                    }
                    if (segment == ProjectsSegment.Epics) {
                        SearchReveal(visible = epicSearchOpen) {
                            ttSearchField(
                                value = epicQuery,
                                onValueChange = { epicQuery = it },
                                hint = stringResource(Res.string.epic_search_placeholder),
                                // Full width at every size, unlike the backlog's capped field:
                                // there the field shares the header row with the sort controls,
                                // while here it has the whole content column and a short field
                                // reads as a stray control beside the epic list.
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag(ProjectsEpicSearchFieldTag),
                                fieldModifier = Modifier.testTag(ProjectsEpicSearchInputTag),
                                keyboardActions = KeyboardActions(
                                    onSearch = { focusManager.clearFocus() },
                                ),
                            )
                        }
                    }
                }
            }
            errorMessage?.let { message ->
                ttBanner(message = message, onDismiss = onDismissError)
            }
            notice?.let { outcome ->
                ttBanner(
                    tone = ttBannerTone.Accent,
                    message = when (outcome) {
                        is RemovalNotice.ArchivedInstead -> stringResource(
                            Res.string.project_archived_notice_counted,
                            outcome.projectName,
                            outcome.outstandingCount,
                        )
                        is RemovalNotice.Deleted -> stringResource(
                            Res.string.project_deleted_notice,
                            outcome.projectName,
                        )
                        is RemovalNotice.AlreadyArchived -> stringResource(
                            Res.string.project_already_archived,
                            outcome.projectName,
                        )
                    },
                    onDismiss = onDismissNotice,
                )
            }
            AnimatedContent(
                targetState = segment,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                transitionSpec = {
                    if (reducedMotion) {
                        ContentTransform(
                            targetContentEnter = EnterTransition.None,
                            initialContentExit = ExitTransition.None,
                            sizeTransform = SizeTransform(clip = true) { _, _ -> snap() },
                        )
                    } else {
                        // Forward is Projects -> Epics; the outgoing half leaves the way the
                        // incoming one arrived, so a return trip reads as a step back.
                        val forward = targetState == ProjectsSegment.Epics
                        ContentTransform(
                            targetContentEnter =
                                fadeIn(animationSpec = tween(SEGMENT_SWAP_FADE_MILLIS)) +
                                    slideInHorizontally(
                                        animationSpec = tween(SEGMENT_SWAP_TRAVEL_MILLIS),
                                    ) { width -> if (forward) width / 4 else -width / 4 },
                            initialContentExit =
                                fadeOut(animationSpec = tween(SEGMENT_SWAP_FADE_MILLIS)) +
                                    slideOutHorizontally(
                                        animationSpec = tween(SEGMENT_SWAP_TRAVEL_MILLIS),
                                    ) { width -> if (forward) -width / 4 else width / 4 },
                            sizeTransform = SizeTransform(clip = true) { _, _ ->
                                tween(SEGMENT_SWAP_TRAVEL_MILLIS)
                            },
                        )
                    }
                },
                label = "projects-segment-content",
            ) { shown ->
                // The outgoing half stays composed for the swap, so every branch below reads
                // [shown] rather than the live segment: the old list must not turn into the
                // new list's data halfway out.
                Box(modifier = Modifier.fillMaxSize()) {
                    if (shown == ProjectsSegment.Epics && shownEpicRows.isEmpty() && !epicsLoading) {
                        val searchActive = appliedEpicQuery.isNotBlank()
                        ProjectsEmptyState(
                            title = stringResource(
                                if (searchActive) {
                                    Res.string.epic_search_empty_title
                                } else {
                                    Res.string.epic_empty_title
                                },
                            ),
                            body = stringResource(
                                if (searchActive) {
                                    Res.string.epic_search_empty_body
                                } else {
                                    Res.string.epic_empty_body
                                },
                            ),
                            // The epic list below is capped and centred; the empty state takes
                            // the same column so switching between "no epics" and a page of
                            // them does not move the content sideways on a desktop window.
                            modifier = Modifier
                                .widthIn(max = BacklogMaxContentWidth)
                                .fillMaxSize()
                                .align(Alignment.Center),
                            image = if (searchActive) ttActionIcons.search else ttEntityIcons.epic,
                            titleModifier = if (searchActive) {
                                Modifier.testTag(ProjectsEpicEmptySearchTag)
                            } else {
                                Modifier
                            },
                            action = if (!searchActive && onNewEpic != null) {
                                {
                                    ttGlassButton(
                                        label = stringResource(Res.string.epic_new),
                                        onClick = onNewEpic,
                                        style = TTGlassButtonStyle(prominent = true),
                                    )
                                }
                            } else {
                                null
                            },
                        )
                    } else if (shown == ProjectsSegment.Epics) {
                        LazyColumn(
                            modifier = Modifier
                                .widthIn(max = BacklogMaxContentWidth)
                                .fillMaxSize()
                                .align(Alignment.Center)
                                .testTag(ProjectsEpicListTag),
                            contentPadding = PaddingValues(bottom = ttSpacing.xl + tabBarInset),
                            verticalArrangement = Arrangement.spacedBy(ttSpacing.md),
                        ) {
                            items(shownEpicRows, key = { it.epic.id.stringValue }) { row ->
                                EpicRowView(
                                    row = row,
                                    onOpen = { onOpenEpic(row.epic.id) },
                                    onEdit = { onEditEpic(row.epic.id) },
                                    onToggleArchived = {
                                        if (row.epic.isArchived) {
                                            onUnarchiveEpic(row.epic.id)
                                        } else {
                                            pendingArchiveID = row.epic.id
                                        }
                                    },
                                )
                            }
                        }
                    } else if (rows.isEmpty() && !isLoading) {
                        ProjectsEmptyState(
                            title = stringResource(Res.string.projects_empty_title),
                            body = stringResource(Res.string.projects_empty_body),
                            // The same reading column as the epic half, so the two empty
                            // states do not jump sideways when the segment changes.
                            modifier = Modifier
                                .widthIn(max = BacklogMaxContentWidth)
                                .fillMaxSize()
                                .align(Alignment.Center),
                            image = ttTabIcons.projects,
                            action = if (onNewProject != null) {
                                {
                                    ttGlassButton(
                                        label = stringResource(Res.string.project_new),
                                        onClick = onNewProject,
                                        style = TTGlassButtonStyle(prominent = true),
                                    )
                                }
                            } else {
                                null
                            },
                        )
                    } else if (isWide) {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = ProjectCardMinimumWidth),
                            modifier = Modifier.fillMaxSize().testTag(ProjectsListTag),
                            contentPadding = PaddingValues(
                                bottom = ttSpacing.xl + tabBarInset,
                            ),
                            horizontalArrangement = Arrangement.spacedBy(ttSpacing.md),
                            verticalArrangement = Arrangement.spacedBy(ttSpacing.md),
                        ) {
                            items(rows, key = { it.summary.id.stringValue }) { row ->
                                TTSwipeToRevealRow(
                                    revealLabel = stringResource(Res.string.common_delete),
                                    isRevealed = pendingRemovalID == row.summary.id,
                                    onRevealed = { pendingRemovalID = row.summary.id },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    ProjectRowView(
                                        project = row.summary,
                                        onOpen = { onOpen(row.summary.id) },
                                        onRemove = { pendingRemovalID = row.summary.id },
                                        onEdit = { onEditProject(row.summary.id) },
                                    )
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize().testTag(ProjectsListTag),
                            contentPadding = PaddingValues(
                                bottom = ttSpacing.xl + tabBarInset,
                            ),
                            verticalArrangement = Arrangement.spacedBy(ttSpacing.md),
                        ) {
                            items(rows, key = { it.summary.id.stringValue }) { row ->
                                TTSwipeToRevealRow(
                                    revealLabel = stringResource(Res.string.common_delete),
                                    isRevealed = pendingRemovalID == row.summary.id,
                                    onRevealed = { pendingRemovalID = row.summary.id },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    ProjectRowView(
                                        project = row.summary,
                                        onOpen = { onOpen(row.summary.id) },
                                        onRemove = { pendingRemovalID = row.summary.id },
                                        onEdit = { onEditProject(row.summary.id) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val pendingRow = pendingRemovalID?.let { wanted -> rows.firstOrNull { it.summary.id == wanted } }
    val pendingProjectVanished = pendingRemovalID != null && pendingRow == null

    // Reset pending state in an effect if the target project disappeared mid-confirmation
    if (pendingProjectVanished) {
        LaunchedEffect(pendingRemovalID, pendingProjectVanished) {
            if (pendingProjectVanished) {
                pendingRemovalID = null
                onDismissRemove()
            }
        }
    }

    pendingArchiveID?.let { id ->
        epicRows.firstOrNull { it.epic.id == id }?.let { row ->
            EpicArchiveSheet(
                epicTitle = row.epic.title,
                unfinishedCount = row.unfinishedCount,
                visible = true,
                onArchiveOnly = {
                    pendingArchiveID = null
                    onArchiveEpic(id, false)
                },
                onArchiveAll = {
                    pendingArchiveID = null
                    onArchiveEpic(id, true)
                },
                onDismissRequest = { pendingArchiveID = null },
                onDismissed = { pendingArchiveID = null },
            )
        }
    }

    pendingRow?.let { row ->
        RemoveProjectConfirmation(
            row = row,
            onConfirm = {
                onConfirmRemove(row.summary.id)
                pendingRemovalID = null
            },
            onDismiss = {
                pendingRemovalID = null
                onDismissRemove()
            },
        )
    }
}

/** Rendered inline rather than via platform Dialog to ensure testability across all multiplatform hosts. */
@Composable
private fun RemoveProjectConfirmation(
    row: ProjectRow,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(ttRadius.surface)
    val hasOutstanding = row.outstandingCount > 0

    Column(
        modifier = modifier
            .fillMaxWidth()
            .ttGlassSurface(shape = shape)
            .padding(ttSpacing.formStack)
            .testTag(RemoveProjectConfirmationTag),
        verticalArrangement = Arrangement.spacedBy(ttSpacing.lg),
    ) {
        ttText(
            text = stringResource(Res.string.project_remove_title, row.summary.name),
            style = ttTypography.titleSmall,
            color = ttLabel(),
        )
        ttText(
            text = if (hasOutstanding) {
                stringResource(Res.string.project_remove_body_outstanding, row.outstandingCount)
            } else {
                stringResource(Res.string.project_remove_body_clear)
            },
            style = ttTypography.callout,
            color = ttLabelSecondary(),
            textAlign = TextAlign.Start,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ttSpacing.sm, Alignment.End),
        ) {
            ttGlassButton(
                label = stringResource(Res.string.common_cancel),
                onClick = onDismiss,
                modifier = Modifier.testTag(RemoveProjectCancelTag),
            )
            ttGlassButton(
                label = stringResource(
                    if (hasOutstanding) Res.string.project_remove_archive else Res.string.common_delete,
                ),
                onClick = onConfirm,
                modifier = Modifier.testTag(RemoveProjectConfirmTag),
                style = TTGlassButtonStyle(tint = ttError(), prominent = true),
            )
        }
    }
}

/** The width at which the segment joins the title row instead of taking its own row. */
private val ProjectsHeaderWideMinimumWidth = 700.dp

/** The segment swap settles quickly: it is a change of reading, not a reveal. */
private const val SEGMENT_SWAP_FADE_MILLIS = 120
private const val SEGMENT_SWAP_TRAVEL_MILLIS = 180

const val ProjectsListScreenTag = "projects.list"
const val ProjectsListTag = "projects.list.rows"
const val ProjectsEpicListTag = "projects.list.epics"
const val ProjectsListNewTag = "projects.list.new"
const val ProjectsSegmentTag = "projects.segment"
const val ProjectsEpicSearchFieldTag = "projects.epic.search"
const val ProjectsEpicSearchToggleTag = "projects.epic.search.toggle"
const val ProjectsEpicSearchInputTag = "projects.epic.search.input"
const val ProjectsEpicEmptySearchTag = "projects.epic.emptySearch"

/**
 * Whether an epic matches [query].
 *
 * Title and project names, because both are text the card draws. Details are not searched for the
 * reason [WorkItemSearch] gives: a row whose visible text does not contain the query reads as a
 * broken filter rather than a thorough one.
 */
private fun EpicListRow.matchesSearch(query: String): Boolean {
    val needle = query.trim()
    if (needle.isEmpty()) return true
    return epic.title.contains(needle, ignoreCase = true) ||
        projectNames.any { it.contains(needle, ignoreCase = true) }
}

fun ProjectsSegmentOptionTag(segment: ProjectsSegment): String =
    "projects.segment.${segment.wireValue}"
const val RemoveProjectConfirmationTag = "projects.remove.confirmation"
const val RemoveProjectConfirmTag = "projects.remove.confirm"
const val RemoveProjectCancelTag = "projects.remove.cancel"

/**
 * Threshold width at which the project list switches from a single-column
 * swipe-to-reveal list to an adaptive multi-column card grid.
 * Aligns with the shell's expanded breakpoint (840.dp).
 */
val ProjectsWideMinimumWidth: Dp = 840.dp

/** Minimum card width in the adaptive grid. Yields 2 columns at 840dp and 3 columns at 1200dp+. */
val ProjectCardMinimumWidth: Dp = 340.dp
