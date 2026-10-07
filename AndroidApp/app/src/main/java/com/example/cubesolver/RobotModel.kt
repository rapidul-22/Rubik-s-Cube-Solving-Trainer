package com.example.cubesolver

import android.bluetooth.BluetoothDevice
import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*

data class FaceCapture(val samples: List<Rgb>, val photo: Bitmap, val spread: Double)
class RobotModel : ViewModel() {
    val link = RobotLink()
    var stage by mutableStateOf("HOME"); private set
    var message by mutableStateOf("Connect Bluetooth, then confirm the normal starting pose."); private set
    var running by mutableStateOf(false); private set
    var connecting by mutableStateOf(false); private set
    var normalPose by mutableStateOf(false)
    var face by mutableIntStateOf(0); private set
    var seconds by mutableIntStateOf(10); private set
    var roi by mutableFloatStateOf(0.70f)
    val corners=mutableStateListOf(Offset(.15f,.15f),Offset(.85f,.15f),Offset(.85f,.85f),Offset(.15f,.85f))
    var uncertain by mutableStateOf<Set<Int>>(emptySet()); private set
    private var retrySignal: CompletableDeferred<Unit>? = null
    fun resetCrop(value: Float) {
        roi=value; val a=(1-value)/2; val b=1-a
        corners.clear(); corners.addAll(listOf(Offset(a,a),Offset(b,a),Offset(b,b),Offset(a,b)))
    }
    fun retryFace() { retrySignal?.complete(Unit) }
    var moveDelay by mutableFloatStateOf(350f)
    var solution by mutableStateOf(""); private set
    var cubeState by mutableStateOf(""); private set
    val faces = mutableStateListOf<MutableList<Int>>()
    val captures = mutableStateListOf<FaceCapture>()
    private var task: Job? = null

    private val solverWarmup = viewModelScope.async(Dispatchers.Default) {
        warmUpSolver()
    }
    init { link.onLost = { if (running || stage != "HOME") stop("Bluetooth lost. Restore the starting pose and rescan.") } }

    fun connect(device: BluetoothDevice) {
        if (connecting || running) return
        connecting = true
        viewModelScope.launch {
            try { link.connect(device); message = "Connected. Updated firmware verified." }
            catch (e: Exception) { message = "Connection failed: ${e.message}. Upload AUTOSCAN_HOLD_1, pair in Android Settings, then retry." }
            finally { connecting = false }
        }
    }
    fun disconnect() { stop("Disconnected. Confirm the starting pose before another scan."); link.disconnect() }
    private fun launchWork(block: suspend () -> Unit) {
        if (running) return
        running = true
        task = viewModelScope.launch {
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { stop("${e.message}. Restore a known pose and start a fresh scan.") }
            finally { running = false }
        }
    }
    fun stop(reason: String = "Stopped. Support the cube; restore normal pose and rescan.") {
        task?.cancel(); task = null
        link.abort()
        running = false; normalPose = false; stage = "HOME"
        faces.clear(); captures.clear(); solution = ""; cubeState = ""; message = reason
    }
    fun onBackground() { if (running || stage == "REVIEW" || stage == "READY" || stage == "PAUSED") stop("App left foreground. Restore normal pose and rescan.") }
    fun open() = launchWork {
        faces.clear(); captures.clear(); stage = "HOME"; solution = ""; cubeState = ""
        normalPose = false
        link.move("SEQ RRLRFRBR350"); message = "Open. Load a cube in normal orientation, then confirm the pose."
    }
    fun closeGrips() = launchWork {
        check(normalPose) { "Confirm normal pose first" }
        link.move("SEQ RCLCFCBC350"); message = "Closed. Align the camera and start Auto Scan."
    }
    fun startScan(capture: suspend ((Int) -> Unit) -> FaceCapture) = launchWork {
        check(normalPose) { "Confirm the normal pose first" }
        faces.clear(); captures.clear(); solution = ""; cubeState = ""
        stage = "SCANNING"; normalPose = false
        link.move("SEQ RCLCFCBC350")
        for (i in 0..5) {
            face = i; message = "Moving to ${faceNames[i]}..."
            link.move(faceCommand(i+1))
            message = "Scanning ${faceNames[i]}. Keep the phone still."
            seconds = 10
            var accepted: FaceCapture? = null
            var retries=0
            while(accepted==null) {
                try { accepted=capture { seconds=it } }
                catch(e: CaptureQualityError) {
                    retries++
                    if(retries<2) { message="${e.message}. Retrying the same face automatically..."; delay(1000) }
                    else {
                        stage="PAUSED"; message="${e.message}. Adjust lighting/corners, then Retry this face. Cube stays in this pose."
                        retrySignal=CompletableDeferred()
                        retrySignal!!.await(); retrySignal=null
                        stage="SCANNING"; retries=0
                    }
                }
            }
            captures.add(checkNotNull(accepted))
        }
        val detected = detectColors(captures.map { it.samples })
        uncertain=detected.uncertain
        faces.addAll(detected.faces.map { it.toMutableStateList() })
        message = "Returning to RIGHT for review..."
        link.move(faceCommand(7)); face = 0; stage = "REVIEW"
        message = "Compare the saved photo and live face. Tap a tile to correct it, then confirm."
    }
    fun confirmFace() = launchWork {
        if (face < 5) { face++; message = "Moving to ${faceNames[face]} for review..."; link.move(faceCommand(face+1)) }
        else {
            message = "Restoring normal orientation..."; link.move(faceCommand(8))
            stage = "READY"; normalPose = true
        }
        message = if (stage == "READY") "All faces reviewed. Recheck if needed, or press Solve when ready."
                  else "Review ${faceNames[face]}; tap any wrong tile before confirming."
    }
    fun recheck() = launchWork {
        check(stage == "READY" && normalPose)
        normalPose = false; message = "Moving to RIGHT for another review..."
        link.move(faceCommand(1)); face = 0; stage = "REVIEW"
        message = "Review again. Your colour edits are kept."
    }
    fun changeColor(f: Int, tile: Int) {
        if (running) return
        faces[f][tile] = (faces[f][tile]+1)%6
    }
    fun solve() {
        if (running || stage != "READY") return
        // Validation errors keep the review data available for correction.
        try { cubeState = cubeString(faces) }
        catch (e: Exception) { message = e.message ?: "Invalid colours"; return }

        launchWork {
            message = "Calculating solution..."; stage = "CALCULATING"

            solverWarmup.await()

            val result = try {
                withContext(Dispatchers.Default) {
                    calculateSolution(cubeState)
                }
            }
            catch (e: IllegalArgumentException) { stage = "READY"; message = e.message ?: "Invalid scan"; return@launchWork }
            currentCoroutineContext().ensureActive()
            solution = result.first
            if (result.second.isEmpty()) { stage = "DONE"; message = "Scan describes an already solved cube. No movement needed." }
            else {
                message = "Solution ready. Automatic solve starts in 3 seconds. Stop remains available."
                delay(3000); stage = "SOLVING"; normalPose = false
                link.move("MOVE ${moveDelay.toInt()} 0 ${result.second}")
                stage = "DONE"; normalPose = true
                message = "Movement finished. Check that all six faces are uniform; the robot has no turn sensors."
            }
        }
    }
    fun newScan() { if (!running) { stage = "HOME"; faces.clear(); captures.clear(); solution = ""; cubeState = ""; message = "Load your next cube, confirm normal pose and start Auto Scan." } }
    override fun onCleared() { link.close() }
}
