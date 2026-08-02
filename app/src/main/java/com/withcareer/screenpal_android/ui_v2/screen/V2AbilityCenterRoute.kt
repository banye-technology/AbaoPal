package com.withcareer.screenpal_android.ui_v2.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.border
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.withcareer.screenpal_android.ui_v2.component.V2EmptyState
import com.withcareer.screenpal_android.ui_v2.component.V2ScreenTopBar
import com.withcareer.screenpal_android.ui_v2.component.V2SearchBar
import com.withcareer.screenpal_android.ui_v2.util.rememberV2CardColor
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.withcareer.screenpal_android.ui_v2.viewmodel.V2AbilityCenterViewModel
import com.withcareer.screenpal_android.ui_v2.viewmodel.V2AbilityFilter
import com.withcareer.screenpal_android.ui_v2.viewmodel.V2AbilityItem
import com.withcareer.screenpal_android.ui_v2.viewmodel.V2AbilityItemKind
import com.withcareer.screenpal_android.ui_v2.util.rememberV2StatusBarTopPadding

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun V2AbilityCenterRoute(
    onBack: () -> Unit,
    onCreateSkill: () -> Unit,
    onEditSkill: (String) -> Unit,
    onOpenDetail: (String) -> Unit,
    viewModel: V2AbilityCenterViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val statusBarPadding = rememberV2StatusBarTopPadding()
    val lightCardColor = Color(0xFFEEF6FF)
    val cardColor = if (isSystemInDarkTheme()) {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f)
    } else {
        lightCardColor
    }
    val sidebarItems = listOf(
        AbilitySideBarModel(label = "我的", icon = Icons.Default.AccountCircle, filter = V2AbilityFilter.MY_SKILLS),
        AbilitySideBarModel(label = "内置", icon = Icons.Default.Star, filter = V2AbilityFilter.BUILTIN_SKILLS),
        AbilitySideBarModel(label = "技能", icon = Icons.Default.Bolt, filter = V2AbilityFilter.SKILL),
        AbilitySideBarModel(label = "工具", icon = Icons.Default.Apps, filter = V2AbilityFilter.MCP)
    )

    Scaffold(
        topBar = {
            V2ScreenTopBar(
                title = "能力中心",
                onBack = onBack,
                actions = {
                    if (uiState.filter != V2AbilityFilter.MCP) {
                        IconButton(onClick = onCreateSkill) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "创建技能"
                            )
                        }
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(24.dp),
                color = cardColor,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f)),
                tonalElevation = 0.dp
            ) {
                Row(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .width(78.dp)
                            .fillMaxHeight()
                            .background(
                                if (isSystemInDarkTheme()) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.20f)
                                else lightCardColor
                            )
                            .padding(horizontal = 8.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        sidebarItems.forEach { item ->
                            AbilitySideBarItem(
                                label = item.label,
                                icon = item.icon,
                                selected = uiState.filter == item.filter,
                                onClick = { viewModel.updateFilter(item.filter) }
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(1.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f))
                    )

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        V2SearchBar(
                            query = uiState.query,
                            onQueryChange = viewModel::updateQuery,
                            onClear = { viewModel.updateQuery("") },
                            placeholder = "搜索能力..."
                        )

                        if (uiState.items.isEmpty()) {
                            V2EmptyState(text = "暂无可用内容")
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(uiState.items, key = { it.key }) { item ->
                                    AbilityItemCard(
                                        item = item,
                                        onClick = { onOpenDetail(item.key) },
                                        containerColor = cardColor
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class AbilitySideBarModel(
    val label: String,
    val icon: ImageVector,
    val filter: V2AbilityFilter
)

@Composable
private fun AbilitySideBarItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    val background = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else Color.Transparent
    val contentColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    val weight = if (selected) FontWeight.SemiBold else FontWeight.Medium

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = contentColor,
            modifier = Modifier.size(22.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = weight,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun AbilityItemCard(
    item: V2AbilityItem,
    onClick: () -> Unit,
    containerColor: Color
) {
    val kindLabel = when (item.kind) {
        V2AbilityItemKind.SKILL -> "技能"
        V2AbilityItemKind.MCP -> "MCP"
    }

    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .alpha(if (item.isEnabled) 1f else 0.68f),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = containerColor),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.26f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (!item.isEnabled) {
                    Text(
                        text = "已停用",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            val label = buildString {
                append(kindLabel)
                val meta = item.meta
                if (!meta.isNullOrBlank()) {
                    append(" · ")
                    append(meta)
                }
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (item.description.isNotBlank()) {
                Text(
                    text = item.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun V2AbilityDetailRoute(
    key: String,
    onBack: () -> Unit,
    onUse: (String) -> Unit,
    onEditSkill: (String) -> Unit,
    viewModel: V2AbilityCenterViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val statusBarPadding = rememberV2StatusBarTopPadding()
    val item = uiState.items.firstOrNull { it.key == key }
    val lightCardColor = Color(0xFFEEF6FF)
    val cardColor = if (isSystemInDarkTheme()) {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f)
    } else {
        lightCardColor
    }

    Scaffold(
        topBar = {
            V2ScreenTopBar(
                title = item?.title ?: "详情",
                onBack = onBack
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        if (item == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "内容不存在",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@Scaffold
        }

        val kindLabel = when (item.kind) {
            V2AbilityItemKind.SKILL -> "技能"
            V2AbilityItemKind.MCP -> "MCP"
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = cardColor,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f)),
                tonalElevation = 0.dp
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = buildString {
                            append(kindLabel)
                            val meta = item.meta
                            if (!meta.isNullOrBlank()) {
                                append(" · ")
                                append(meta)
                            }
                            if (!item.isEnabled) {
                                append(" · 已停用")
                            }
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (item.description.isNotBlank()) {
                        Text(
                            text = item.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.alpha(0.92f)
                        )
                    }
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = cardColor,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f)),
                tonalElevation = 0.dp
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "使用方式",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = item.useText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (item.isUserSkill && item.userSkillId != null) {
                    OutlinedButton(
                        onClick = { onEditSkill(item.userSkillId) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("编辑")
                    }
                } else {
                    OutlinedButton(
                        onClick = {
                            when (item.kind) {
                                V2AbilityItemKind.SKILL -> viewModel.setSkillEnabled(item.id, !item.isEnabled)
                                V2AbilityItemKind.MCP -> viewModel.setMcpEnabled(item.id, !item.isEnabled)
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (item.isEnabled) "停用" else "启用")
                    }
                }

                Button(
                    onClick = { onUse(item.useText) },
                    enabled = item.isEnabled,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("使用")
                }
            }
        }
    }
}
