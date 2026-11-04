package com.v2ray.ang.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextDirection
import com.v2ray.ang.dto.GroupMapItem
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.R
import com.v2ray.ang.dto.LocateTarget
import com.v2ray.ang.dto.entities.ProfileItem
import com.v2ray.ang.dto.entities.SubscriptionUsageInfo
import com.v2ray.ang.ui.compose.ItemDivider
import com.v2ray.ang.ui.compose.ReorderableGridItem
import com.v2ray.ang.ui.compose.ReorderableListItem
import com.v2ray.ang.ui.compose.colorConfigType
import com.v2ray.ang.ui.compose.verticalScrollbar
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState
import sh.calvin.reorderable.rememberReorderableLazyListState
import kotlin.math.abs

@Composable
fun GroupPagerPage(
    groupId: String,
    mainViewModel: MainViewModel,
    selectedGuid: String?,
    locateTarget: LocateTarget?,
    doubleColumnDisplay: Boolean,
    searchQuery: String,
    lazyListStates: MutableMap<String, LazyListState>,
    lazyGridStates: MutableMap<String, LazyGridState>,
    onSelectServer: (String) -> Unit,
    onEditServer: (String, ProfileItem) -> Unit,
    onShareServer: (String, ProfileItem) -> Unit,
    onMoreServer: (String, ProfileItem) -> Unit,
    onRemoveServer: (String, String) -> Unit,
    usageInfo: SubscriptionUsageInfo?,
    groupTitle: String,
    onOpenLink: (String) -> Unit,
    contentPadding: PaddingValues,
    group: GroupMapItem? = null,
    sort: MainSort = MainSort.SUBSCRIPTION,
    newSubFetch: NewSubFetch? = null,
    onCycleSort: () -> Unit = {},
    onEditGroup: () -> Unit = {},
    onGroupSpeedTest: () -> Unit = {},
    onRetryFetch: () -> Unit = {},
    onPingGroup: () -> Unit = {},
    isTesting: Boolean = false,
) {
    val groupStateFlow = remember(groupId) {
        mainViewModel.serverGroupState(groupId)
    }
    val groupState by groupStateFlow.collectAsStateWithLifecycle()
    val canReorder = groupId.isNotEmpty() && searchQuery.isEmpty() && sort == MainSort.SUBSCRIPTION
    val actions = remember(
        onSelectServer,
        onEditServer,
        onShareServer,
        onMoreServer,
        onRemoveServer,
    ) {
        ServerRowActions(
            select = onSelectServer,
            edit = onEditServer,
            share = onShareServer,
            more = onMoreServer,
            remove = onRemoveServer,
        )
    }
    // The box floats over the list (the list scrolls underneath it), so the list is padded by the box height.
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    var infoHeightPx by remember { mutableIntStateOf(0) }
    val isSubscription = group != null && group.url.isNotEmpty()
    val showCard = usageInfo != null || isSubscription
    val listPadding = if (!showCard) {
        contentPadding
    } else {
        PaddingValues(
            start = contentPadding.calculateStartPadding(layoutDirection),
            top = contentPadding.calculateTopPadding() + with(density) { infoHeightPx.toDp() },
            end = contentPadding.calculateEndPadding(layoutDirection),
            bottom = contentPadding.calculateBottomPadding()
        )
    }

    Column(Modifier.fillMaxSize()) {
    GroupHeaderBar(
        // Subscriptions have speed test, ping and edit on their card; local groups keep them here.
        group = group,
        showGroupActions = !(group != null && group.url.isNotEmpty()),
        rowCount = groupState.rows.size,
        sort = sort,
        onCycleSort = onCycleSort,
        onEdit = onEditGroup,
        onSpeedTest = onGroupSpeedTest,
        onPing = onPingGroup,
        isTesting = isTesting,
    )
    if (newSubFetch != null && groupState.rows.isEmpty()) {
        NewSubscriptionLoader(newSubFetch, onRetryFetch)
        return@Column
    }
    Box(Modifier.weight(1f)) {
        ServerListPage(
            rows = groupState.rows,
            selectedGuid = selectedGuid,
            locateTarget = locateTarget?.takeIf { it.groupId == groupId },
            canReorder = canReorder,
            doubleColumnDisplay = doubleColumnDisplay,
            groupId = groupId,
            lazyListStates = lazyListStates,
            lazyGridStates = lazyGridStates,
            actions = actions,
            onLocateHandled = { mainViewModel.onAction(MainAction.LocateHandled) },
            onMoveServer = { fromIndex, toIndex ->
                mainViewModel.moveServer(groupId, fromIndex, toIndex)
            },
            contentPadding = listPadding
        )
        if (showCard) {
            SubscriptionInfoCard(
                info = usageInfo,
                fallbackTitle = groupTitle,
                onOpenLink = onOpenLink,
                onRefresh = { mainViewModel.onAction(MainAction.UpdateGroupSubscription(groupId)) },
                onEdit = if (isSubscription) onEditGroup else null,
                onSpeedTest = if (groupState.rows.isNotEmpty()) onGroupSpeedTest else null,
                onPing = if (groupState.rows.isNotEmpty()) onPingGroup else null,
                isTesting = isTesting,
                autoLine = group?.let { groupAutoLine(it) }.orEmpty(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .onSizeChanged { infoHeightPx = it.height }
            )
        }
    }
    }
}

private class ServerRowActions(
    val select: (String) -> Unit,
    val edit: (String, ProfileItem) -> Unit,
    val share: (String, ProfileItem) -> Unit,
    val more: (String, ProfileItem) -> Unit,
    val remove: (String, String) -> Unit,
)

@Composable
private fun ServerListPage(
    rows: List<ServerRowUiModel>,
    selectedGuid: String?,
    locateTarget: LocateTarget?,
    canReorder: Boolean,
    doubleColumnDisplay: Boolean,
    groupId: String,
    lazyListStates: MutableMap<String, LazyListState>,
    lazyGridStates: MutableMap<String, LazyGridState>,
    actions: ServerRowActions,
    onLocateHandled: () -> Unit,
    onMoveServer: (Int, Int) -> Unit,
    contentPadding: PaddingValues
) {
    if (doubleColumnDisplay) {
        val gridState = remember(groupId) {
            lazyGridStates.getOrPut(groupId) { LazyGridState() }
        }
        val reorderableGridState = if (canReorder) {
            rememberReorderableLazyGridState(gridState) { from, to ->
                onMoveServer(from.index, to.index)
            }
        } else null

        LocateTargetEffect(locateTarget, rows, gridState, onLocateHandled)

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            state = gridState,
            modifier = Modifier
                .fillMaxSize()
                .verticalScrollbar(gridState),
            contentPadding = contentPadding
        ) {
            itemsIndexed(items = rows, key = { _, item -> item.guid }) { _, row ->
                val content: @Composable () -> Unit = {
                    ServerItemColumn(
                        row = row,
                        isSelected = row.guid == selectedGuid,
                        doubleColumnDisplay = true,
                        actions = actions
                    )
                }
                if (canReorder && reorderableGridState != null) {
                    ReorderableItem(
                        reorderableGridState,
                        key = row.guid
                    ) { isDragging ->
                        ReorderableGridItem(
                            scope = this,
                            isDragging = isDragging
                        ) { content() }
                    }
                } else {
                    content()
                }
            }
        }
    } else {
        val listState = remember(groupId) {
            lazyListStates.getOrPut(groupId) { LazyListState() }
        }
        val reorderableState = if (canReorder) {
            rememberReorderableLazyListState(listState) { from, to ->
                onMoveServer(from.index, to.index)
            }
        } else null

        LocateTargetEffect(locateTarget, rows, listState, onLocateHandled)

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .verticalScrollbar(listState),
            contentPadding = contentPadding
        ) {
            itemsIndexed(items = rows, key = { _, item -> item.guid }) { _, row ->
                if (canReorder && reorderableState != null) {
                    ReorderableItem(
                        reorderableState,
                        key = row.guid
                    ) { isDragging ->
                        ReorderableListItem(
                            scope = this,
                            isDragging = isDragging
                        ) {
                            ServerItemRow(
                                row = row,
                                isSelected = row.guid == selectedGuid,
                                actions = actions
                            )
                        }
                        ItemDivider()
                    }
                } else {
                    ServerItemRow(
                        row = row,
                        isSelected = row.guid == selectedGuid,
                        actions = actions
                    )
                    ItemDivider()
                }
            }
        }
    }
}

@Composable
private fun LocateTargetEffect(
    target: LocateTarget?,
    rows: List<ServerRowUiModel>,
    state: LazyListState,
    onHandled: () -> Unit,
) {
    if (target == null) return
    LaunchedEffect(target, rows) {
        val index = rows.indexOfFirst { it.guid == target.serverGuid }
        if (index < 0) return@LaunchedEffect
        state.scrollToItem(index, -state.layoutInfo.viewportSize.height / 3)
        onHandled()
    }
}

@Composable
private fun LocateTargetEffect(
    target: LocateTarget?,
    rows: List<ServerRowUiModel>,
    state: LazyGridState,
    onHandled: () -> Unit,
) {
    if (target == null) return
    LaunchedEffect(target, rows) {
        val index = rows.indexOfFirst { it.guid == target.serverGuid }
        if (index < 0) return@LaunchedEffect
        state.scrollToItem(index, -state.layoutInfo.viewportSize.height / 3)
        onHandled()
    }
}

@Composable
private fun ServerItemRow(
    row: ServerRowUiModel,
    isSelected: Boolean,
    actions: ServerRowActions
) {
    ServerListItem(
        row = row,
        isSelected = isSelected,
        doubleColumnDisplay = false,
        actions = actions
    )
}

@Composable
private fun ServerItemColumn(
    row: ServerRowUiModel,
    isSelected: Boolean,
    doubleColumnDisplay: Boolean,
    actions: ServerRowActions
) {
    Column {
        ServerListItem(
            row = row,
            isSelected = isSelected,
            doubleColumnDisplay = doubleColumnDisplay,
            actions = actions
        )
        ItemDivider()
    }
}

@Composable
private fun ServerListItem(
    row: ServerRowUiModel,
    isSelected: Boolean,
    doubleColumnDisplay: Boolean,
    actions: ServerRowActions
) {
    val pingDescription = when {
        row.testDelayMillis > 0L -> stringResource(R.string.server_test_delay_value, row.testDelayMillis)
        row.testDelayMillis < 0L -> stringResource(R.string.main_ping_failed)
        else -> ""
    }
    val selectedStateDescription = if (isSelected) {
        stringResource(R.string.acc_selected_server)
    } else {
        null
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .semantics {
                if (selectedStateDescription != null) {
                    stateDescription = selectedStateDescription
                }
            }
            .clickable { actions.select(row.guid) }
    ) {
        Box(
            Modifier
                .width(10.dp)
                .fillMaxHeight()
        ) {
            if (isSelected) {
                Row {
                    Spacer(Modifier.width(6.dp))
                    Box(
                        Modifier
                            .width(4.dp)
                            .fillMaxHeight()
                            .padding(vertical = 10.dp)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
            }
        }

        Column(
            Modifier
                .weight(1f)
                .padding(start = 8.dp, end = 12.dp, top = 8.dp, bottom = 8.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(row.remarks, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge.copy(lineBreak = LineBreak.Paragraph), maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (doubleColumnDisplay) {
                    IconButton(onClick = { actions.more(row.guid, row.profile) }, Modifier.size(36.dp)) {
                        Icon(
                            painterResource(R.drawable.ic_more_vert_24dp),
                            stringResource(R.string.acc_more),
                            Modifier.size(24.dp)
                        )
                    }
                } else {
                    IconButton(onClick = { actions.share(row.guid, row.profile) }, Modifier.size(36.dp)) {
                        Icon(
                            painterResource(R.drawable.ic_share_24dp),
                            stringResource(R.string.title_configuration_share),
                            Modifier.size(24.dp)
                        )
                    }
                    IconButton(onClick = { actions.edit(row.guid, row.profile) }, Modifier.size(36.dp)) {
                        Icon(
                            painterResource(R.drawable.ic_edit_24dp),
                            stringResource(R.string.acc_edit),
                            Modifier.size(24.dp)
                        )
                    }
                    IconButton(onClick = { actions.remove(row.guid, row.remarks) }, Modifier.size(36.dp)) {
                        Icon(
                            painterResource(R.drawable.ic_delete_24dp),
                            stringResource(R.string.acc_delete),
                            Modifier.size(24.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (row.subscriptionBadge.isNotBlank()) {
                    Box(
                        Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)), Alignment.Center
                    ) {
                        Text(row.subscriptionBadge.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                }
                Text(
                    row.statistics,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    row.typeDescription,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = colorConfigType,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.width(6.dp))
                if (!doubleColumnDisplay) {
                    row.downloadMbps?.let { SpeedBox("\u2193", it, stringResource(R.string.main_last_download, it)) }
                    row.uploadMbps?.let {
                        Spacer(modifier = Modifier.width(4.dp))
                        SpeedBox("\u2191", it, stringResource(R.string.main_last_upload, it))
                    }
                    if (row.downloadMbps != null || row.uploadMbps != null) Spacer(modifier = Modifier.width(4.dp))
                }
                if (row.testDelayMillis != 0L) PingBox(row.testDelayMillis, pingDescription)
            }
        }
    }
}

internal suspend fun PagerState.navigateToPageOptimized(
    targetPage: Int,
    animateAdjacentPage: Boolean = true
) {
    if (pageCount <= 0) return
    val target = targetPage.coerceIn(0, pageCount - 1)
    val current = settledPage.coerceIn(0, pageCount - 1)
    if (target == current) return

    if (abs(target - current) == 1 && animateAdjacentPage) {
        animateScrollToPage(target)
    } else {
        scrollToPage(target)
    }
}

private val pingGood = Color(0xFF00875A)
private val pingPoorBg = Color(0xFFF5C518)
private val pingPoorText = Color(0xFF3A2E00)
private val pingDead = Color(0xFFC62828)

/** Ping as a bare number in a solid box: green = good, yellow = poor, red = no response. */
@Composable
private fun PingBox(millis: Long, description: String) {
    val (bg, fg, text) = when {
        millis < 0L -> Triple(pingDead, Color.White, "\u00D7")
        millis < PING_GOOD_MS -> Triple(pingGood, Color.White, millis.toString())
        else -> Triple(pingPoorBg, pingPoorText, millis.toString())
    }
    Box(
        modifier = Modifier
            .defaultMinSize(minWidth = 40.dp)
            .height(20.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(bg)
            .padding(horizontal = 6.dp)
            .clearAndSetSemantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = fg, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, style = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr))
    }
}

/** Last speed test result in Mbps as a tinted box, coloured like the speed test screen. */
@Composable
private fun SpeedBox(arrow: String, mbps: Double, description: String) {
    val color = when {
        mbps >= 20 -> Color(0xFF00875A)
        mbps >= 8 -> Color(0xFF2E7D32)
        mbps >= 3 -> Color(0xFF8A6A00)
        mbps >= 1 -> Color(0xFFB5530A)
        else -> Color(0xFFC62828)
    }
    Box(
        modifier = Modifier
            .defaultMinSize(minWidth = 52.dp)
            .height(20.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 6.dp)
            .clearAndSetSemantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Text(
            "$arrow ${"%.1f".format(java.util.Locale.US, mbps)}",
            color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1,
            style = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr)
        )
    }
}

private const val PING_GOOD_MS = 300L
