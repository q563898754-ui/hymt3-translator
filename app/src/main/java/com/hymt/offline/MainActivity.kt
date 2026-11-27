package com.hymt.offline

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.hymt.offline.data.Languages
import com.hymt.offline.ui.HyMtTheme
import com.hymt.offline.ui.LanguageSheet
import com.hymt.offline.ui.ModelSetupScreen
import com.hymt.offline.ui.TranslateScreen

class MainActivity : ComponentActivity() {

    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (intent?.action == Intent.ACTION_SEND && intent?.type == "text/plain") {
            intent?.getStringExtra(Intent.EXTRA_TEXT)?.let { vm.setSharedText(it) }
        }
        setContent {
            HyMtTheme {
                RootScreen(vm)
            }
        }
    }
}

@Composable
private fun RootScreen(vm: AppViewModel) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    var showModel by remember { mutableStateOf(false) }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) vm.importModel(uri)
    }

    val micLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted: Boolean ->
        val tip = if (granted) "已授权，请再点一次语音" else "未授予录音权限"
        Toast.makeText(context, tip, Toast.LENGTH_SHORT).show()
    }

    LaunchedEffect(state.message) {
        val message = state.message
        if (message != null) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            vm.consumeMessage()
        }
    }

    val speechIssue = state.speechIssue
    if (speechIssue != null) {
        AlertDialog(
            onDismissRequest = vm::dismissSpeechIssue,
            title = { Text("语音朗读不可用") },
            text = {
                Column {
                    Text(speechIssue)
                    Text(
                        text = "用英语语音近似朗读",
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clickable { vm.speakWithEnglishFallback() }
                            .padding(top = 12.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.dismissSpeechIssue()
                    openTtsSettings(context)
                }) {
                    Text("打开语音设置")
                }
            },
            dismissButton = {
                TextButton(onClick = vm::dismissSpeechIssue) {
                    Text("知道了")
                }
            }
        )
    }

    Box(Modifier.fillMaxSize()) {
        val ready = state.modelStatus is ModelStatus.Ready

        if (ready && !showModel) {
            TranslateScreen(
                state = state,
                onSourceChange = vm::onSourceTextChange,
                onTranslate = vm::translate,
                onCancel = vm::cancelTranslate,
                onSwap = vm::swapLanguages,
                onPickSource = { vm.openPicker(PickerTarget.SOURCE) },
                onPickTarget = { vm.openPicker(PickerTarget.TARGET) },
                onMic = {
                    val locale = Languages.of(state.sourceLang)?.localeTag ?: "zh-CN"
                    val granted = ContextCompat.checkSelfPermission(
                        context, Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED
                    if (granted) {
                        vm.toggleListening(locale)
                    } else {
                        micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                onSpeak = vm::speakOutput,
                onToggleAutoSpeak = vm::toggleAutoSpeak,
                onClear = vm::clearSource,
                onHistory = vm::useHistory,
                onManageModel = { showModel = true }
            )
        } else {
            ModelSetupScreen(
                status = state.modelStatus,
                freeSpaceGb = state.freeSpaceGb,
                onDownload = vm::downloadModel,
                onImport = { importLauncher.launch(arrayOf("*/*")) },
                onDelete = vm::deleteModel,
                onClose = if (ready) {
                    { showModel = false }
                } else {
                    null
                }
            )
        }

        val picker = state.picker
        if (picker != null) {
            LanguageSheet(
                title = if (picker == PickerTarget.SOURCE) "选择原文语言" else "选择译文语言",
                selected = if (picker == PickerTarget.SOURCE) state.sourceLang else state.targetLang,
                allowAuto = picker == PickerTarget.SOURCE,
                onSelect = vm::selectLanguage,
                onDismiss = vm::closePicker
            )
        }
    }
}

/**
 * 打开系统「文字转语音」设置。
 * 各厂商 ROM 的入口不一致，按优先级逐个尝试，全部失败再退回应用设置页。
 */
private fun openTtsSettings(context: Context) {
    val candidates = listOf(
        Intent("com.android.settings.TTS_SETTINGS"),
        Intent("com.android.settings.TEXT_TO_SPEECH_SETTINGS"),
        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS),
        Intent(Settings.ACTION_SETTINGS)
    )
    for (intent in candidates) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val opened = runCatching { context.startActivity(intent) }.isSuccess
        if (opened) return
    }
    Toast.makeText(
        context,
        "打不开语音设置，请手动在「设置」里搜索“文字转语音”",
        Toast.LENGTH_LONG
    ).show()
}
