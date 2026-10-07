package com.example.cubesolver

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Path
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent { MaterialTheme(colorScheme = lightColorScheme(primary=Color(0xFF165D83))) { CubeApp() } }
    }
}

@SuppressLint("MissingPermission")
@Composable
fun CubeApp(model: RobotModel = viewModel()) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val feed = remember { CameraFeed() }
    var permission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    var devices by remember { mutableStateOf<List<BluetoothDevice>?>(null) }
    var uiMessage by remember { mutableStateOf("") }
    val cameraPermission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            permission = it; if (!it) uiMessage = "Allow Camera in Android app settings to scan."
        }

    fun pairedDevices() {
        try {
            val adapter =
                (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
            check(adapter != null && adapter.isEnabled) { "Enable Bluetooth in Android Settings first" }
            devices = adapter.bondedDevices.toList()
            if (devices!!.isEmpty()) uiMessage =
                "No paired device. Pair HC-06 in Android Settings first."
        } catch (e: Exception) {
            uiMessage = e.message ?: "Bluetooth unavailable"
        }
    }

    val btPermission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            if (it) pairedDevices() else uiMessage = "Nearby devices permission is required."
        }
    DisposableEffect(owner) {
        val observer =
            LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) model.onBackground() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    BackHandler(enabled = model.running || model.stage == "REVIEW" || model.stage == "READY") { model.stop() }
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(12.dp)) {
            Text("Cube AutoScan", style = MaterialTheme.typography.headlineSmall)
            Text(
                "${model.link.state} • ${model.stage}",
                style = MaterialTheme.typography.labelLarge
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = !model.connecting && !model.running, onClick = {
                    if (model.link.connected) model.disconnect()
                    else if (Build.VERSION.SDK_INT >= 31) btPermission.launch(Manifest.permission.BLUETOOTH_CONNECT)
                    else pairedDevices()
                }) { Text(if (model.link.connected) "Disconnect" else if (model.connecting) "Connecting..." else "Connect") }
                Button(
                    enabled = model.link.connected,
                    onClick = { model.stop() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB3261E))
                ) { Text("STOP") }
            }
            Text(model.message, Modifier.padding(vertical = 6.dp))
            if (uiMessage.isNotBlank()) Text(uiMessage, color = MaterialTheme.colorScheme.error)
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (permission) CameraPanel(feed, model) { uiMessage = it }
                else Button(onClick = { cameraPermission.launch(Manifest.permission.CAMERA) }) {
                    Text(
                        "Allow camera"
                    )
                }
                if (model.stage == "HOME" || model.stage == "DONE") {
                    Text("Align the yellow corners with the four face corners (TL, TR, BR, BL). Drag handles to correct perspective. Small boxes must sit inside stickers.")
                    Text("Grid size: ${(model.roi * 100).toInt()}%")
                    Slider(
                        value = model.roi,
                        onValueChange = { model.resetCrop(it) },
                        valueRange = 0.40f..0.95f,
                        enabled = !model.running
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = model.normalPose,
                            onCheckedChange = { model.normalPose = it },
                            enabled = !model.running
                        )
                        Text("Cube and arms are in the normal starting pose.", Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { model.open() },
                            enabled = model.link.firmwareReady && !model.running
                        ) { Text("Open") }
                        Button(
                            onClick = { model.closeGrips() },
                            enabled = model.link.firmwareReady && model.normalPose && !model.running
                        ) { Text("Close") }
                    }
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = permission && model.link.firmwareReady && model.normalPose && !model.running,
                        onClick = {
                            model.startScan { tick ->
                                feed.captureTenSeconds(
                                    model.corners.toList(),
                                    tick
                                )
                            }
                        }) { Text("Start automatic 6-face scan") }
                }
                if (model.stage == "PAUSED") {
                    Text("Quality check paused at ${faceNames[model.face]}. Adjust lighting or drag the corner handles. Do not move the cube by hand.")
                    Button(onClick = { model.retryFace() }) { Text("Retry this face (10 seconds)") }
                }
                if (model.stage == "SCANNING") {
                    Text(
                        "Face ${model.face + 1}/6: ${faceNames[model.face]}",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text("${model.seconds} seconds", style = MaterialTheme.typography.headlineLarge)
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text("Sampling automatically. Do not touch the cube or leave this app.")
                }
                if (model.stage == "REVIEW" && model.captures.size == 6) {
                    Text(
                        "Review ${model.face + 1}/6: ${faceNames[model.face]}",
                        style = MaterialTheme.typography.titleLarge
                    )
                    ReviewFace(model, model.face)
                    Button(
                        enabled = !model.running,
                        onClick = { model.confirmFace() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (model.face == 5) "Confirm UP & finish review" else "Confirm face & show next")
                    }
                }
                if (model.stage == "READY") {
                    Text("All six faces confirmed. Press Solve when you are ready, or Recheck faces.")
                    Text("Solve stage delay: ${model.moveDelay.toInt()} ms (higher is slower)")
                    Slider(
                        value = model.moveDelay,
                        onValueChange = { model.moveDelay = it },
                        valueRange = 250f..600f,
                        steps = 6,
                        enabled = !model.running
                    )
                    Button(
                        enabled = !model.running,
                        onClick = { model.solve() },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Solve") }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            enabled = !model.running,
                            onClick = { model.recheck() }) { Text("Recheck faces") }
                        OutlinedButton(
                            enabled = !model.running,
                            onClick = { model.newScan() }) { Text("Discard & rescan") }
                    }
                    val counts = (0..5).joinToString(" • ") {
                        "${colorNames[it]} ${
                            model.faces.flatten().count { c -> c == it }
                        }"
                    }
                    Text(counts)
                    for (f in 0..5) {
                        Text(faceNames[f]); ReviewFace(model, f)
                    }
                }
                if (model.stage == "CALCULATING" || model.stage == "SOLVING") LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth()
                )
                if (model.cubeState.isNotEmpty()) {
                    Text("Cube state: ${model.cubeState}"); Text("Solution: ${model.solution.ifEmpty { "(empty or calculating)" }}")
                }
                if (model.stage == "DONE") OutlinedButton(onClick = { model.newScan() }) { Text("New cube") }
                Text(
                    "STOP disables motor pulses; the cube may need support. Use the physical power switch for a jam.",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "Last robot response: ${model.link.lastLine}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
    devices?.let { list ->
        AlertDialog(
            onDismissRequest = { devices = null },
            title = { Text("Paired Bluetooth devices") },
            text = {
                Column(
                    Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    list.forEach { device ->
                        TextButton(
                            onClick = {
                                devices = null
                                uiMessage = ""
                                model.connect(device)
                            }
                        ) {
                            Text("${device.name ?: "Device"}\n${device.address}")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { devices = null }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}
@Composable
private fun CameraPanel(feed: CameraFeed, model: RobotModel, onError:(String)->Unit) {
    val context=LocalContext.current
    val owner=LocalLifecycleOwner.current
    val view=remember { PreviewView(context).apply {
        implementationMode=PreviewView.ImplementationMode.COMPATIBLE
        scaleType=PreviewView.ScaleType.FILL_CENTER
    } }
    DisposableEffect(owner,view) {
        var disposed=false
        var provider: ProcessCameraProvider?=null
        val future=ProcessCameraProvider.getInstance(context)
        feed.preview=view
        val observer=androidx.lifecycle.Observer<PreviewView.StreamState> { feed.streaming=it==PreviewView.StreamState.STREAMING }
        view.previewStreamState.observe(owner,observer)
        future.addListener({
            if(!disposed) try {
                provider=future.get()
                val preview=Preview.Builder().build().also { it.setSurfaceProvider(view.surfaceProvider) }
                val analysis=ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                analysis.setAnalyzer(ContextCompat.getMainExecutor(context)) { image -> feed.frameTime=SystemClock.elapsedRealtime(); image.close() }
                provider!!.unbindAll()
                feed.camera=provider!!.bindToLifecycle(owner,CameraSelector.DEFAULT_BACK_CAMERA,preview,analysis)
            } catch(e:Exception) { onError("Camera: ${e.message}") }
        },ContextCompat.getMainExecutor(context))
        onDispose { disposed=true; provider?.unbindAll(); view.previewStreamState.removeObserver(observer); feed.preview=null; feed.camera=null; feed.streaming=false }
    }
    Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
        AndroidView(factory={view},modifier=Modifier.fillMaxSize())
        val editable=(!model.running && model.stage in listOf("HOME","DONE")) || model.stage=="PAUSED"
        Canvas(Modifier.fillMaxSize().pointerInput(editable) {
            var selected=-1
            detectDragGestures(onDragStart={ start ->
                if(editable) selected=model.corners.indices.minBy { i ->
                    (Offset(model.corners[i].x*size.width,model.corners[i].y*size.height)-start).getDistance()
                }
            },onDragEnd={selected=-1},onDragCancel={selected=-1}) { change, amount ->
                if(editable && selected>=0) {
                    change.consume()
                    val old=model.corners[selected]
                    model.corners[selected]=Offset(
                        (old.x+amount.x/size.width).coerceIn(0.02f,0.98f),
                        (old.y+amount.y/size.height).coerceIn(0.02f,0.98f)
                    )
                }
            }
        }) {
            val matrix=android.graphics.Matrix()
            val dest=model.corners.flatMap { listOf(it.x*size.width,it.y*size.height) }.toFloatArray()
            matrix.setPolyToPoly(floatArrayOf(0f,0f,1f,0f,1f,1f,0f,1f),0,dest,0,4)
            fun point(x:Float,y:Float):Offset { val p=floatArrayOf(x,y); matrix.mapPoints(p); return Offset(p[0],p[1]) }
            fun quad(x:Float,y:Float,w:Float,h:Float) {
                val p=listOf(point(x,y),point(x+w,y),point(x+w,y+h),point(x,y+h))
                val path=Path().apply { moveTo(p[0].x,p[0].y); for(v in p.drop(1)) lineTo(v.x,v.y); close() }
                drawPath(path,Color.Yellow,style=Stroke(2.dp.toPx()))
            }
            quad(0f,0f,1f,1f)
            for(i in 1..2) {
                drawLine(Color.White,point(i/3f,0f),point(i/3f,1f),2f)
                drawLine(Color.White,point(0f,i/3f),point(1f,i/3f),2f)
            }
            for(r in 0..2) for(c in 0..2)
                quad(
                    (c + 0.3f) / 3f,
                    (r + 0.3f) / 3f,
                    0.4f / 3f,
                    0.4f / 3f
                )
            model.corners.forEach { drawCircle(Color.Cyan,8.dp.toPx(),Offset(it.x*size.width,it.y*size.height)) }
        }
    }
}

@Composable
private fun ReviewFace(model:RobotModel, f:Int) {
    val capture=model.captures[f]
    Image(capture.photo.asImageBitmap(),contentDescription="Saved ${faceNames[f]} photo, rotated to grid order",modifier=Modifier.size(210.dp))
    if (capture.spread > 0.12) Text("Lighting or movement varied during this capture. Check every sticker; rescan if blurred.",color=MaterialTheme.colorScheme.error)
    val uncertain=model.uncertain.count { it/9==f }
    if(uncertain>0) Text("$uncertain uncertain colour(s), marked ?. Check these carefully.",color=MaterialTheme.colorScheme.error)
    Column {
        for(r in 0..2) Row {
            for(c in 0..2) {
                val i=r*3+c; val color=model.faces[f][i]
                Box(Modifier.size(68.dp).background(Color(0xFF000000L or colorRgb[color].toLong())).border(1.dp,Color.Black)
                    .clickable(enabled=!model.running) { model.changeColor(f,i) },contentAlignment=Alignment.Center) {
                    Text("${scanLetters[f]}${stickerMaps[f].indexOf(i)+1}${if(f*9+i in model.uncertain) " ?" else ""}\n${colorNames[color]}",color=if(color in listOf(0,1,3)) Color.Black else Color.White,style=MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
    Text("Tap a square to cycle its colour. The saved photo is rotated 90° to match this grid.",style=MaterialTheme.typography.bodySmall)
}
