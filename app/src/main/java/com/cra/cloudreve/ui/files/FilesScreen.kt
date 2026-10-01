package com.cra.cloudreve.ui.files

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cra.cloudreve.data.remote.dto.FileObject

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FilesScreen(
    viewModel: FilesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showCreateDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<FileObject?>(null) }
    var deleteTarget by remember { mutableStateOf<FileObject?>(null) }
    var shareTarget by remember { mutableStateOf<FileObject?>(null) }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var showDeleteSelectionDialog by remember { mutableStateOf(false) }

    val pickFiles = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        uris.forEach { viewModel.upload(it) }
    }

    LaunchedEffect(uiState.errorMessage, uiState.infoMessage) {
        val message = uiState.errorMessage ?: uiState.infoMessage
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.consumeMessage()
        }
    }

    BackHandler(enabled = uiState.selectionMode || uiState.crumbs.size > 1) {
        if (uiState.selectionMode) viewModel.clearSelection() else viewModel.navigateUp()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = { pickFiles.launch(arrayOf("*/*")) }) {
                Icon(Icons.Filled.Upload, contentDescription = "上传文件")
            }
        },
        topBar = {
            if (uiState.selectionMode) {
                TopAppBar(
                    title = { Text("已选 ${uiState.selection.size} 项") },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.clearSelection() }) {
                            Icon(Icons.Filled.Close, contentDescription = "退出多选")
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.downloadSelection() }) {
                            Icon(Icons.Filled.Download, contentDescription = "下载所选")
                        }
                        IconButton(onClick = { viewModel.startMove() }) {
                            Icon(Icons.Filled.DriveFileMove, contentDescription = "移动到其它位置")
                        }
                        IconButton(onClick = { showDeleteSelectionDialog = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "删除所选")
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = {
                        Text(
                            text = uiState.crumbs.lastOrNull()?.name ?: "我的文件",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        if (uiState.crumbs.size > 1) {
                            IconButton(onClick = { viewModel.navigateUp() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回上级")
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.refresh() }) {
                            Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                        }
                        IconButton(onClick = { showCreateDialog = true }) {
                            Icon(Icons.Filled.CreateNewFolder, contentDescription = "新建文件夹")
                        }
                        Box {
                            IconButton(onClick = { sortMenuExpanded = true }) {
                                Icon(Icons.Filled.Sort, contentDescription = "排序")
                            }
                            DropdownMenu(
                                expanded = sortMenuExpanded,
                                onDismissRequest = { sortMenuExpanded = false }
                            ) {
                                FileSortOption.entries.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option.label) },
                                        onClick = {
                                            viewModel.changeSort(option)
                                            sortMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            BreadcrumbBar(
                crumbs = uiState.crumbs,
                onCrumbClick = viewModel::navigateToCrumb
            )
            HorizontalDivider()
            PullToRefreshBox(
                isRefreshing = uiState.refreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxSize()
            ) {
                when {
                    uiState.loading -> LoadingState()
                    uiState.errorMessage != null && uiState.files.isEmpty() -> ErrorState(
                        message = uiState.errorMessage ?: "加载失败",
                        onRetry = viewModel::retry
                    )
                    uiState.isEmpty -> EmptyState()
                    else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(uiState.files, key = { it.id.ifBlank { it.path } }) { file ->
                            FileRow(
                                file = file,
                                selectionMode = uiState.selectionMode,
                                selected = file.uriKey() in uiState.selection,
                                onClick = {
                                    when {
                                        uiState.selectionMode -> viewModel.toggleSelection(file)
                                        file.isFolder -> viewModel.openFolder(file)
                                    }
                                },
                                onLongClick = { viewModel.toggleSelection(file) },
                                onDownload = { viewModel.download(file) },
                                onDirectLink = { viewModel.getDirectLink(file) },
                                onShare = { shareTarget = file },
                                onRename = { renameTarget = file },
                                onDelete = { deleteTarget = file }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        NameInputDialog(
            title = "新建文件夹",
            label = "文件夹名称",
            confirmText = "创建",
            onDismiss = { showCreateDialog = false },
            onConfirm = { name ->
                viewModel.createFolder(name)
                showCreateDialog = false
            }
        )
    }

    renameTarget?.let { target ->
        NameInputDialog(
            title = "重命名",
            label = "新名称",
            confirmText = "确定",
            initialValue = target.name,
            onDismiss = { renameTarget = null },
            onConfirm = { name ->
                viewModel.rename(target, name)
                renameTarget = null
            }
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除") },
            text = { Text("确定要删除“${target.name}”吗？此操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(target)
                    deleteTarget = null
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("取消") }
            }
        )
    }

    if (showDeleteSelectionDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteSelectionDialog = false },
            title = { Text("删除") },
            text = { Text("确定要删除选中的 ${uiState.selection.size} 项吗？此操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteSelection()
                    showDeleteSelectionDialog = false
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteSelectionDialog = false }) { Text("取消") }
            }
        )
    }

    uiState.linkResult?.let { result ->
        LinkResultDialog(
            result = result,
            onDismiss = viewModel::dismissLinkResult
        )
    }

    shareTarget?.let { target ->
        ShareOptionsDialog(
            targetName = target.name,
            onDismiss = { shareTarget = null },
            onConfirm = { options ->
                viewModel.share(target, options)
                shareTarget = null
            }
        )
    }

    uiState.moveDialog?.let { dialog ->
        MoveTargetDialog(
            state = dialog,
            canMoveHere = uiState.canMoveHere,
            onEnter = viewModel::enterMoveFolder,
            onUp = viewModel::navigateMoveUp,
            onCrumb = viewModel::jumpMoveCrumb,
            onConfirm = viewModel::confirmMove,
            onDismiss = viewModel::dismissMove
        )
    }
}

/** 有效期单位，用于把用户输入的数值换算成秒 */
private enum class ExpireUnit(val label: String, val seconds: Int) {
    MINUTE("分钟", 60),
    HOUR("小时", 60 * 60),
    DAY("天", 24 * 60 * 60)
}

/** 创建分享前配置密码 / 有效期 / 下载次数，三项均可单独开关 */
@Composable
private fun ShareOptionsDialog(
    targetName: String,
    onDismiss: () -> Unit,
    onConfirm: (ShareOptions) -> Unit
) {
    var passwordEnabled by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var expireEnabled by remember { mutableStateOf(false) }
    var expireValue by remember { mutableStateOf("7") }
    var expireUnit by remember { mutableStateOf(ExpireUnit.DAY) }
    var expireUnitExpanded by remember { mutableStateOf(false) }
    var downloadEnabled by remember { mutableStateOf(false) }
    var downloadValue by remember { mutableStateOf("1") }

    // 服务端对密码的约束是 max=32 且仅字母数字
    val passwordValid = !passwordEnabled || (password.length in 1..32 && PASSWORD_REGEX.matches(password))
    val expireAmount = expireValue.toIntOrNull() ?: 0
    val expireValid = !expireEnabled || expireAmount >= 1
    val downloadAmount = downloadValue.toIntOrNull() ?: 0
    val downloadValid = !downloadEnabled || downloadAmount >= 1
    val valid = passwordValid && expireValid && downloadValid

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("分享“$targetName”") },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("密码保护", modifier = Modifier.weight(1f))
                    Switch(checked = passwordEnabled, onCheckedChange = { passwordEnabled = it })
                }
                if (passwordEnabled) {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        singleLine = true,
                        isError = !passwordValid,
                        supportingText = {
                            Text(
                                if (password.isEmpty()) {
                                    "仅支持字母和数字，最长 32 位"
                                } else if (!passwordValid) {
                                    "密码需为 1-32 位字母或数字"
                                } else {
                                    "访问分享时需要输入该密码"
                                }
                            )
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("超时自动过期", modifier = Modifier.weight(1f))
                    Switch(checked = expireEnabled, onCheckedChange = { expireEnabled = it })
                }
                if (expireEnabled) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = expireValue,
                            onValueChange = { expireValue = it.filter(Char::isDigit).take(4) },
                            singleLine = true,
                            isError = !expireValid,
                            label = { Text("时长") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.width(120.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Box {
                            TextButton(onClick = { expireUnitExpanded = true }) {
                                Text("${expireUnit.label} ▾")
                            }
                            DropdownMenu(
                                expanded = expireUnitExpanded,
                                onDismissRequest = { expireUnitExpanded = false }
                            ) {
                                ExpireUnit.entries.forEach { unit ->
                                    DropdownMenuItem(
                                        text = { Text(unit.label) },
                                        onClick = {
                                            expireUnit = unit
                                            expireUnitExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                    if (!expireValid) {
                        Text(
                            text = "请输入大于 0 的时长",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("下载后自动过期", modifier = Modifier.weight(1f))
                    Switch(checked = downloadEnabled, onCheckedChange = { downloadEnabled = it })
                }
                if (downloadEnabled) {
                    OutlinedTextField(
                        value = downloadValue,
                        onValueChange = { downloadValue = it.filter(Char::isDigit).take(4) },
                        singleLine = true,
                        isError = !downloadValid,
                        label = { Text("可下载次数") },
                        supportingText = {
                            Text(
                                if (downloadValid) {
                                    "下载满 $downloadAmount 次后链接自动失效"
                                } else {
                                    "请输入大于 0 的次数"
                                }
                            )
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    onConfirm(
                        ShareOptions(
                            password = if (passwordEnabled) password else "",
                            expireSeconds = if (expireEnabled) {
                                expireAmount * expireUnit.seconds
                            } else {
                                0
                            },
                            downloadLimit = if (downloadEnabled) downloadAmount else 0
                        )
                    )
                }
            ) { Text("创建") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

private val PASSWORD_REGEX = Regex("[A-Za-z0-9]+")

/** 批量移动时的目标目录浏览器：可逐层进入、返回上级，确认后移动到当前目录 */
@Composable
private fun MoveTargetDialog(
    state: MoveDialogState,
    canMoveHere: Boolean,
    onEnter: (FileObject) -> Unit,
    onUp: () -> Unit,
    onCrumb: (Int) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("移动到") },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onUp, enabled = state.crumbs.size > 1) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "上一级"
                        )
                    }
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        itemsIndexed(state.crumbs) { index, crumb ->
                            AssistChip(
                                onClick = { onCrumb(index) },
                                label = { Text(crumb.name) }
                            )
                        }
                    }
                }
                HorizontalDivider()
                when {
                    state.loading -> Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator() }

                    state.error != null -> Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = state.error,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    state.folders.isEmpty() -> Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "此目录下没有子文件夹，可直接移动到此处",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    else -> LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                        items(state.folders, key = { it.uriKey() }) { folder ->
                            ListItem(
                                headlineContent = {
                                    Text(
                                        text = folder.name,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                leadingContent = {
                                    Icon(
                                        imageVector = Icons.Filled.Folder,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                },
                                modifier = Modifier.clickable { onEnter(folder) }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "目标位置：${state.crumbs.lastOrNull()?.name ?: "我的文件"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = canMoveHere && !state.loading
            ) { Text("移动到此处") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun BreadcrumbBar(
    crumbs: List<Crumb>,
    onCrumbClick: (Int) -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        itemsIndexed(crumbs) { index, crumb ->
            AssistChip(
                onClick = { onCrumbClick(index) },
                label = { Text(crumb.name) }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileRow(
    file: FileObject,
    selectionMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDownload: () -> Unit,
    onDirectLink: () -> Unit,
    onShare: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    ListItem(
        headlineContent = {
            Text(
                text = file.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            Text(if (file.isFolder) "文件夹" else formatSize(file.size))
        },
        leadingContent = {
            if (selectionMode) {
                Checkbox(checked = selected, onCheckedChange = null)
            } else {
                Icon(
                    imageVector = if (file.isFolder) {
                        Icons.Filled.Folder
                    } else {
                        Icons.AutoMirrored.Filled.InsertDriveFile
                    },
                    contentDescription = null,
                    tint = if (file.isFolder) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        },
        trailingContent = {
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "更多")
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("下载") },
                        leadingIcon = { Icon(Icons.Filled.Download, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onDownload()
                        }
                    )
                    if (!file.isFolder) {
                        DropdownMenuItem(
                            text = { Text("获取直链") },
                            leadingIcon = { Icon(Icons.Filled.Link, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onDirectLink()
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("分享") },
                        leadingIcon = { Icon(Icons.Filled.Share, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onShare()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("重命名") },
                        leadingIcon = {
                            Icon(Icons.Filled.DriveFileRenameOutline, contentDescription = null)
                        },
                        onClick = {
                            menuExpanded = false
                            onRename()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("删除") },
                        leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        }
                    )
                }
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    )
}

/** 展示分享链接 / 直链，支持一键复制 */
@Composable
private fun LinkResultDialog(
    result: LinkResult,
    onDismiss: () -> Unit
) {
    val clipboard = LocalClipboardManager.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(result.title) },
        text = {
            SelectionContainer {
                Text(
                    text = result.content,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    clipboard.setText(AnnotatedString(result.content))
                    onDismiss()
                }
            ) { Text("复制") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}

@Composable
private fun NameInputDialog(
    title: String,
    label: String,
    confirmText: String,
    initialValue: String = "",
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var value by remember { mutableStateOf(initialValue) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text(label) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(value) },
                enabled = value.isNotBlank()
            ) { Text(confirmText) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun LoadingState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun EmptyState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Filled.Folder,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "这里还没有文件",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "点击右上角新建文件夹",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ErrorState(
    message: String,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onRetry) { Text("重试") }
        }
    }
}

private fun formatSize(size: Long): String {
    if (size <= 0) return "0 B"
    val units = listOf("B", "KB", "MB", "GB", "TB")
    var value = size.toDouble()
    var index = 0
    while (value >= 1024 && index < units.lastIndex) {
        value /= 1024
        index++
    }
    return if (index == 0) {
        "${size} ${units[index]}"
    } else {
        "%.1f %s".format(value, units[index])
    }
}