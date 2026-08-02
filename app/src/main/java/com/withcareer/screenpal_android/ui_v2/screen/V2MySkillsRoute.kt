package com.withcareer.screenpal_android.ui_v2.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.withcareer.screenpal_android.ScreenPalApplication
import com.withcareer.screenpal_android.util.ToastUtils
import android.widget.Toast
import com.withcareer.screenpal_android.data.repository.UserSkillRepository
import com.withcareer.screenpal_android.ui.screen.EmptyLibraryContent
import com.withcareer.screenpal_android.ui.screen.UserSkillCard
import com.withcareer.screenpal_android.ui.viewmodel.UserSkillViewModel
import com.withcareer.screenpal_android.ui.viewmodel.UserSkillViewModelFactory
import com.withcareer.screenpal_android.ui_v2.component.V2LoadingContent
import com.withcareer.screenpal_android.ui_v2.component.V2ScreenTopBar
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun V2MySkillsRoute(
    onBack: () -> Unit,
    onCreateSkill: () -> Unit,
    onEditSkill: (String) -> Unit
) {
    val context = LocalContext.current
    val application = context.applicationContext as ScreenPalApplication

    val userSkillRepository = remember {
        UserSkillRepository(
            dao = application.database.userSkillDao(),
            context = context
        )
    }

    val userSkillViewModel: UserSkillViewModel = viewModel(
        factory = UserSkillViewModelFactory(
            repository = userSkillRepository,
            skillRegistry = application.skillRegistry,
            instructionRepository = application.instructionRepository
        )
    )

    Scaffold(
        topBar = {
            V2ScreenTopBar(
                title = "我的技能",
                onBack = onBack,
                actions = {
                    IconButton(onClick = onCreateSkill) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "新增技能"
                        )
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
        ) {
            V2UserSkillListContent(
                viewModel = userSkillViewModel,
                onNavigateToCreate = onCreateSkill,
                onNavigateToEdit = onEditSkill
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun V2UserSkillListContent(
    viewModel: UserSkillViewModel,
    onNavigateToCreate: () -> Unit,
    onNavigateToEdit: (String) -> Unit
) {
    val skills by viewModel.skills.collectAsState()
    var isInitialLoading by remember { mutableStateOf(true) }

    // 首次加载时，如果数据很快到达（本地数据库查询很快），可以立即隐藏加载动画
    LaunchedEffect(skills) {
        if (isInitialLoading && skills.isNotEmpty()) {
            isInitialLoading = false
        } else if (isInitialLoading && skills.isEmpty()) {
            delay(300)
            isInitialLoading = false
        }
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isRefreshing by remember { mutableStateOf(false) }

    // 删除成功提示
    var showDeleteSuccess by remember { mutableStateOf(false) }
    LaunchedEffect(showDeleteSuccess) {
        if (showDeleteSuccess) {
            ToastUtils.show(context, "删除成功", Toast.LENGTH_SHORT)
            showDeleteSuccess = false
        }
    }

    // 下拉刷新处理（技能列表由 Room Flow 驱动，这里仅刷新动画）
    fun handleRefresh() {
        isRefreshing = true
        scope.launch {
            delay(300)
            isRefreshing = false
        }
    }

    val pullToRefreshState = rememberPullToRefreshState()

    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            pullToRefreshState.startRefresh()
        } else {
            pullToRefreshState.endRefresh()
        }
    }

    LaunchedEffect(pullToRefreshState.isRefreshing) {
        if (pullToRefreshState.isRefreshing && !isRefreshing) {
            handleRefresh()
        }
    }

    Box(modifier = Modifier.fillMaxSize().clipToBounds()) {
        if (isInitialLoading) {
            V2LoadingContent()
        } else if (skills.isEmpty()) {
            EmptyLibraryContent(
                title = "暂无技能",
                description = "您可以点击下方按钮创建自己的技能",
                buttonText = "创建技能",
                onButtonClick = onNavigateToCreate
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(pullToRefreshState.nestedScrollConnection),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(skills) { skill ->
                    UserSkillCard(
                        skill = skill,
                        onEdit = { onNavigateToEdit(skill.id) },
                        onDelete = {
                            viewModel.deleteSkill(skill.id) {
                                showDeleteSuccess = true
                            }
                        }
                    )
                }
            }

            // 下拉刷新指示器
            PullToRefreshContainer(
                state = pullToRefreshState,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .graphicsLayer {
                        val scale = if (pullToRefreshState.isRefreshing) 1f else pullToRefreshState.progress.coerceIn(0f, 1f)
                        scaleX = scale
                        scaleY = scale
                        alpha = scale
                    }
            )
        }
    }
}
