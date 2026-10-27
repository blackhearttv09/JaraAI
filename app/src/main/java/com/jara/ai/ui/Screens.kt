package com.jara.ai.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.jara.ai.R
import com.jara.ai.live.DeviceActions
import com.jara.ai.live.LiveState
import com.jara.ai.vm.*
import kotlinx.coroutines.delay

private fun hasMic(c: Context) =
    ContextCompat.checkSelfPermission(
        c,
        Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED

private fun openAppDetails(c: Context) {
    c.startActivity(
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${c.packageName}")
        )
    )
}

@Composable
fun SplashScreen(onDone: () -> Unit) {
    var p by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        repeat(20) {
            p = (it + 1) / 20f
            delay(90)
        }
        onDone()
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Navy, Color(0xFF0B1450))
                )
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painterResource(R.drawable.ic_logo),
            null,
            Modifier.size(150.dp)
        )

        Text(
            T.app,
            fontSize = 40.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        Text(T.tagline, color = Muted, fontSize = 14.sp)

        Spacer(Modifier.height(80.dp))

        LinearProgressIndicator(
            progress = { p },
            modifier = Modifier
                .width(150.dp)
                .clip(CircleShape),
            color = Purple,
            trackColor = Line
        )

        Spacer(Modifier.height(12.dp))
        Text(T.splashSub, color = Cyan, fontSize = 13.sp)
    }
}

@Composable
fun HomeScreen(
    chat: ChatViewModel,
    onMenu: () -> Unit,
    onAbout: () -> Unit
) {
    val ctx = LocalContext.current
    var typing by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }

    val permLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { ok ->
            if (ok) {
                chat.toggleVoice()
            } else {
                chat.error = T.micDenied
            }
        }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(56.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onMenu) {
                Icon(Icons.Default.Menu, null, tint = Color.White)
            }

            Text(
                T.app,
                Modifier.weight(1f),
                textAlign = TextAlign.Center,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(Modifier.size(48.dp))
        }

        LazyColumn(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (chat.messages.isEmpty()) {
                item {
                    JCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                painterResource(R.drawable.ic_logo),
                                null,
                                Modifier.size(64.dp)
                            )

                            Column {
                                Text(
                                    T.hello,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                                Text(
                                    T.helloBody,
                                    color = Muted,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }

                item {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            horizontalArrangement =
                                Arrangement.spacedBy(10.dp)
                        ) {
                            QuickAction(
                                T.qaAsk,
                                Icons.Default.QuestionAnswer,
                                Modifier.weight(1f)
                            ) {
                                typing = true
                            }

                            QuickAction(
                                T.qaOpen,
                                Icons.Default.Apps,
                                Modifier.weight(1f)
                            ) {
                                typing = true
                                input = "খুলুন: "
                            }
                        }

                        Row(
                            horizontalArrangement =
                                Arrangement.spacedBy(10.dp)
                        ) {
                            QuickAction(
                                T.qaPhone,
                                Icons.Default.Settings,
                                Modifier.weight(1f)
                            ) {
                                ctx.startActivity(
                                    Intent(Settings.ACTION_SETTINGS)
                                )
                            }

                            QuickAction(
                                T.qaMore,
                                Icons.Default.Info,
                                Modifier.weight(1f),
                                onAbout
                            )
                        }
                    }
                }
            }

            items(chat.messages.toList()) { m ->
                Box(
                    Modifier.fillMaxWidth(),
                    contentAlignment = if (m.fromUser) {
                        Alignment.CenterEnd
                    } else {
                        Alignment.CenterStart
                    }
                ) {
                    Text(
                        m.text,
                        Modifier
                            .widthIn(max = 300.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (m.fromUser) Blue else Card
                            )
                            .padding(12.dp),
                        color = Color.White,
                        fontSize = 15.sp
                    )
                }
            }

            if (chat.busy) {
                item {
                    Text("…", color = Muted)
                }
            }
        }

        chat.error?.let {
            Text(
                it,
                color = Bad,
                fontSize = 13.sp,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }

        if (typing) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 6.dp)
            ) {
                OutlinedTextField(
                    input,
                    { input = it },
                    Modifier.weight(1f),
                    placeholder = { Text(T.typeHint) },
                    shape = RoundedCornerShape(14.dp),
                    maxLines = 3
                )

                IconButton(
                    onClick = {
                        chat.send(input)
                        input = ""
                    },
                    enabled = !chat.busy
                ) {
                    Icon(Icons.Default.Send, null, tint = Cyan)
                }
            }
        }

        val status = when (chat.liveState) {
            LiveState.Connecting -> T.connecting
            LiveState.Listening -> T.listening
            else -> T.tapToTalk
        }

        Text(
            status,
            Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            color = Muted,
            fontSize = 12.sp
        )

        Row(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp, top = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { typing = !typing },
                Modifier
                    .clip(CircleShape)
                    .background(Card)
            ) {
                Icon(Icons.Default.Keyboard, null, tint = Cyan)
            }

            val active = chat.liveState == LiveState.Listening

            Box(
                Modifier
                    .size(78.dp)
                    .clip(CircleShape)
                    .background(
                        if (active) {
                            Brush.linearGradient(listOf(Bad, Purple))
                        } else {
                            Grad
                        }
                    )
                    .clickable {
                        if (
                            chat.liveState == LiveState.Idle ||
                            chat.liveState == LiveState.Error
                        ) {
                            if (hasMic(ctx)) {
                                chat.toggleVoice()
                            } else {
                                permLauncher.launch(
                                    Manifest.permission.RECORD_AUDIO
                                )
                            }
                        } else {
                            chat.toggleVoice()
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                if (chat.liveState == LiveState.Connecting) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                } else {
                    Icon(
                        if (active) Icons.Default.Stop
                        else Icons.Default.Mic,
                        null,
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            Spacer(Modifier.size(48.dp))
        }
    }
}

@Composable
private fun QuickAction(
    text: String,
    icon: ImageVector,
    modifier: Modifier,
    onClick: () -> Unit
) {
    JCard(modifier, onClick) {
        Icon(icon, null, tint = Cyan)
        Spacer(Modifier.height(6.dp))
        Text(text, fontSize = 13.sp)
    }
}

@Composable
fun DrawerContent(
    go: (String) -> Unit,
    newChat: () -> Unit
) {
    ModalDrawerSheet(drawerContainerColor = Card) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painterResource(R.drawable.ic_logo),
                null,
                Modifier.size(52.dp)
            )

            Column {
                Text(T.app, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(T.tagline, color = Muted, fontSize = 11.sp)
            }
        }

        val items = listOf(
            Triple(T.newChat, Icons.Default.AddComment, "new"),
            Triple(T.history, Icons.Default.History, "history"),
            Triple(T.share, Icons.Default.Share, "share"),
            Triple(T.voiceAssist, Icons.Default.Mic, "voice"),
            Triple(T.accPerm, Icons.Default.Accessibility, "accessibility"),
            Triple(T.permCenter, Icons.Default.Security, "permissions"),
            Triple(T.apiKey, Icons.Default.Key, "api"),
            Triple(T.voiceModel, Icons.Default.GraphicEq, "voicemodel"),
            Triple(T.appSettings, Icons.Default.Settings, "settings"),
            Triple(T.about, Icons.Default.Info, "about")
        )

        items.forEach { (label, icon, route) ->
            NavigationDrawerItem(
                label = { Text(label) },
                icon = { Icon(icon, null) },
                selected = false,
                onClick = {
                    if (route == "new") newChat()
                    else go(route)
                },
                colors = NavigationDrawerItemDefaults.colors(
                    unselectedContainerColor = Color.Transparent,
                    unselectedTextColor = Color.White,
                    unselectedIconColor = Cyan
                )
            )
        }
    }
}

@Composable
fun ApiKeyScreen(
    vm: SettingsViewModel,
    onBack: () -> Unit
) {
    var show by remember { mutableStateOf(false) }
    val ctx = LocalContext.current

    Screen(T.apiKey, onBack) {
        JCard {
            Text("Gemini API Key", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                vm.apiKey,
                { vm.apiKey = it },
                Modifier.fillMaxWidth(),
                placeholder = { Text("API Key লিখুন") },
                singleLine = true,
                visualTransformation = if (show) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailingIcon = {
                    IconButton({ show = !show }) {
                        Icon(
                            if (show) Icons.Default.VisibilityOff
                            else Icons.Default.Visibility,
                            null
                        )
                    }
                }
            )

            Spacer(Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryBtn(
                    T.save,
                    Modifier.weight(1f),
                    enabled = vm.keyStatus != KeyStatus.Checking
                ) {
                    vm.saveKey()
                }

                OutlineBtn(T.clear, Modifier.weight(1f)) {
                    vm.clearKey()
                }
            }

            Spacer(Modifier.height(8.dp))

            when (val s = vm.keyStatus) {
                KeyStatus.Checking ->
                    Text("যাচাই হচ্ছে…", color = Muted)

                KeyStatus.Ok ->
                    Text("কী যাচাই হয়েছে ও সংরক্ষিত", color = Good)

                is KeyStatus.Fail ->
                    Text(s.msg, color = Bad)

                else -> {}
            }
        }

        JCard(
            onClick = {
                ctx.startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://aistudio.google.com/apikey")
                    )
                )
            }
        ) {
            Text(
                "Gemini API কী কোথায় পাবেন?",
                color = Cyan,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "Google AI Studio থেকে API Key তৈরি করে এখানে পেস্ট করুন। (চাপ দিলে ব্রাউজারে খুলবে)",
                color = Muted,
                fontSize = 13.sp
            )
        }

        Text(
            "কী এনক্রিপ্টেড স্টোরেজে ফোনেই থাকে; শুধু Google-এর API-তে পাঠানো হয়।",
            color = Muted,
            fontSize = 12.sp
        )
    }
}

@Composable
fun AccessibilityScreen(
    settings: SettingsViewModel,
    onBack: () -> Unit
) {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        tick++
    }

    val on = remember(tick) {
        DeviceActions.isAccessibilityEnabled(ctx)
    }

    Screen(T.accPerm, onBack) {
        JCard {
            Icon(
                Icons.Default.Accessibility,
                null,
                tint = Cyan,
                modifier = Modifier
                    .size(48.dp)
                    .align(Alignment.CenterHorizontally)
            )

            Text(
                "Accessibility Service",
                Modifier.align(Alignment.CenterHorizontally),
                fontWeight = FontWeight.SemiBold
            )

            Text(
                "অবস্থা: " + if (on) T.enabled else T.disabled,
                Modifier.align(Alignment.CenterHorizontally),
                color = if (on) Good else Bad
            )

            Spacer(Modifier.height(10.dp))

            Text(
                "এই পারমিশন দিলে আপনার নির্দেশে যারা এআই করতে পারবে:",
                color = Muted,
                fontSize = 13.sp
            )

            listOf(
                "অ্যাপ খোলা ও সিস্টেম বাটন (হোম/ব্যাক)",
                "স্ক্রিনের লেখায় ট্যাপ করা",
                "টেক্সট ইনপুট করা",
                "স্ক্রিনের তথ্য পড়া (পাসওয়ার্ড ফিল্ড বাদে)"
            ).forEach {
                Text("• $it", fontSize = 13.sp)
            }

            Spacer(Modifier.height(10.dp))

            Text(
                "এটি আপনি Android সেটিংসে নিজে চালু করবেন। যেকোনো সময় বন্ধ করা যায়। স্ক্রিন-কন্ট্রোল টুল আলাদাভাবে অ্যাপ সেটিংসের সুইচে চালু করতে হয় (ডিফল্টে বন্ধ)।",
                color = Muted,
                fontSize = 12.sp
            )

            Spacer(Modifier.height(10.dp))

            PrimaryBtn("সেটিংসে যান", Modifier.fillMaxWidth()) {
                ctx.startActivity(
                    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                )
            }
        }
    }
}

@Composable
private fun PermRow(
    icon: ImageVector,
    title: String,
    sub: String,
    on: Boolean,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = Cyan, modifier = Modifier.padding(end = 12.dp))

        Column(Modifier.weight(1f)) {
            Text(title)
            Text(sub, color = Muted, fontSize = 12.sp)
        }

        Text(
            if (on) T.enabled else T.disabled,
            color = if (on) Good else Bad,
            fontSize = 13.sp
        )
    }
}

@Composable
fun PermissionsScreen(
    onBack: () -> Unit,
    onAccessibility: () -> Unit
) {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        tick++
    }

    val micOn = remember(tick) { hasMic(ctx) }
    val accOn = remember(tick) {
        DeviceActions.isAccessibilityEnabled(ctx)
    }

    val launcher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) {
            tick++
        }

    Screen(T.permCenter, onBack) {
        JCard {
            PermRow(
                Icons.Default.Mic,
                "মাইক্রোফোন",
                "ভয়েস ইনপুটের জন্য",
                micOn
            ) {
                if (!micOn) {
                    launcher.launch(Manifest.permission.RECORD_AUDIO)
                } else {
                    openAppDetails(ctx)
                }
            }

            HorizontalDivider(color = Line)

            PermRow(
                Icons.Default.Accessibility,
                "অ্যাক্সেসিবিলিটি সার্ভিস",
                "ডিভাইস নিয়ন্ত্রণের জন্য",
                accOn,
                onAccessibility
            )
        }

        JCard(onClick = { openAppDetails(ctx) }) {
            Text("আরও পারমিশন দেখুন", color = Cyan)
        }
    }
}

@Composable
fun VoiceModelScreen(
    vm: SettingsViewModel,
    onBack: () -> Unit
) {
    Screen(T.voiceModel, onBack) {
        JCard {
            Text("Gemini Live Voice Model", fontWeight = FontWeight.SemiBold)

            OutlinedTextField(
                vm.liveModel,
                {
                    vm.liveModel = it
                    vm.saved = false
                },
                Modifier.fillMaxWidth(),
                singleLine = true
            )

            Text(
                "অফিসিয়াল ডকে যাচাইকৃত ডিফল্ট: gemini-3.1-flash-live-preview। নতুন মডেল এলে এখানে ID বদলান।",
                color = Muted,
                fontSize = 12.sp
            )

            Spacer(Modifier.height(10.dp))

            Text("টেক্সট চ্যাট মডেল", fontWeight = FontWeight.SemiBold)

            OutlinedTextField(
                vm.textModel,
                {
                    vm.textModel = it
                    vm.saved = false
                },
                Modifier.fillMaxWidth(),
                singleLine = true
            )
        }

        JCard {
            Text("ভয়েস সেটিংস", fontWeight = FontWeight.SemiBold)

            Text("ভয়েসের গতি ${(vm.speed * 100).toInt()}%")

            Slider(
                vm.speed,
                {
                    vm.speed = it
                    vm.saved = false
                },
                valueRange = 0.5f..1.5f
            )

            Text("ভয়েসের টোন ${(vm.pitch * 100).toInt()}%")

            Slider(
                vm.pitch,
                {
                    vm.pitch = it
                    vm.saved = false
                },
                valueRange = 0.5f..1.5f
            )

            Text(
                "গতি ও টোন ফোনে প্লেব্যাকের সময় প্রয়োগ হয় (পরের ভয়েস সেশন থেকে)।",
                color = Muted,
                fontSize = 12.sp
            )
        }

        PrimaryBtn(T.save, Modifier.fillMaxWidth()) {
            vm.saveVoice()
        }

        if (vm.saved) {
            Text("সংরক্ষিত", color = Good)
        }
    }
}

@Composable
fun HistoryScreen(
    chat: ChatViewModel,
    onBack: () -> Unit,
    onOpen: () -> Unit
) {
    Screen(T.history, onBack) {
        if (chat.sessions.isEmpty()) {
            Text("কোনো চ্যাট নেই", color = Muted)
        }

        chat.sessions.toList().forEach { s ->
            JCard(
                Modifier.fillMaxWidth(),
                {
                    chat.open(s)
                    onOpen()
                }
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(s.title, maxLines = 1)
                        Text(
                            "${s.messages.size} বার্তা",
                            color = Muted,
                            fontSize = 12.sp
                        )
                    }

                    IconButton({ chat.delete(s) }) {
                        Icon(Icons.Default.Delete, null, tint = Bad)
                    }
                }
            }
        }

        if (chat.sessions.isNotEmpty()) {
            OutlineBtn("সব মুছে ফেলুন", Modifier.fillMaxWidth()) {
                chat.clearAll()
            }
        }
    }
}

@Composable
fun SettingsScreen(
    vm: SettingsViewModel,
    onBack: () -> Unit
) {
    val ctx = LocalContext.current

    Screen(T.appSettings, onBack) {
        JCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("AI-কে স্ক্রিন নিয়ন্ত্রণের অনুমতি")
                    Text(
                        "অ্যাপ খোলা, ট্যাপ, টাইপ, স্ক্রল। অ্যাক্সেসিবিলিটি সার্ভিসও চালু থাকতে হবে।",
                        color = Muted,
                        fontSize = 12.sp
                    )
                }

                Switch(
                    checked = vm.screenControl,
                    onCheckedChange = { vm.updateScreenControl(it) }
                )
            }
        }

        JCard {
            Text("শেয়ার টেক্সট", fontWeight = FontWeight.SemiBold)

            var t by remember {
                mutableStateOf(vm.prefs.shareText)
            }

            OutlinedTextField(
                t,
                {
                    t = it
                    vm.prefs.shareText = it
                },
                Modifier.fillMaxWidth()
            )

            Text(
                "শেয়ারের সময় এই লেখা যাবে। ডাউনলোড লিংক থাকলে এখানে যোগ করুন।",
                color = Muted,
                fontSize = 12.sp
            )
        }

        JCard(onClick = { openAppDetails(ctx) }) {
            Text("ফোনের অ্যাপ সেটিংস খুলুন", color = Cyan)
        }
    }
}

@Composable
fun AboutScreen(onBack: () -> Unit) {
    Screen(T.about, onBack) {
        Image(
            painterResource(R.drawable.ic_logo),
            null,
            Modifier
                .size(100.dp)
                .align(Alignment.CenterHorizontally)
        )

        Text(
            T.app + " v1.0",
            Modifier.align(Alignment.CenterHorizontally),
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp
        )

        JCard {
            Text(
                "Gemini API ও Live API ব্যবহার করে বানানো ভয়েস ও টেক্সট সহকারী। আপনার নিজের API Key লাগে। ফোন-নিয়ন্ত্রণ শুধু আপনার অনুমতি ও নির্দেশে চলে।",
                color = Muted
            )
        }
    }
}

fun shareApp(ctx: Context, text: String) {
    ctx.startActivity(
        Intent.createChooser(
            Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, text),
            T.share
        )
    )
}
