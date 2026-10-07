package com.example.cubesolver

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import androidx.compose.runtime.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.util.UUID

@SuppressLint("MissingPermission")
class RobotLink {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var socket: BluetoothSocket? = null
    private var reader: Job? = null
    private val lines = Channel<String>(Channel.UNLIMITED)
    private val commandLock = Mutex()
    var connected by mutableStateOf(false); private set
    var state by mutableStateOf("Disconnected"); private set
    var lastLine by mutableStateOf(""); private set
    var firmwareReady by mutableStateOf(false); private set
    var onLost: (() -> Unit)? = null

    suspend fun connect(device: BluetoothDevice) {
        disconnect()
        state = "Connecting..."
        val candidate = device.createRfcommSocketToServiceRecord(
            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"))
        socket = candidate
        val watchdog = scope.launch { delay(15000); runCatching { candidate.close() } }
        try {
            withContext(Dispatchers.IO) { candidate.connect() }
            watchdog.cancel()
            connected = true
            reader = scope.launch(Dispatchers.IO) {
                try {
                    val input = candidate.inputStream.bufferedReader(Charsets.US_ASCII)
                    while (isActive) {
                        val line = input.readLine()?.trim() ?: throw IOException("Bluetooth disconnected")
                        withContext(Dispatchers.Main) {
                            lastLine = line
                            if (line == "BUSY" || line == "IDLE") state = line
                            lines.trySend(line)
                        }
                    }
                } catch (e: Exception) {
                    withContext(NonCancellable + Dispatchers.Main) {
                        if (socket === candidate) {
                            connected = false; firmwareReady = false; state = "Disconnected"
                            lines.trySend("ERR disconnected")
                            onLost?.invoke()
                        }
                    }
                }
            }
            commandLock.withLock {
                drain()
                write("VERSION")
                withTimeout(5000) {
                    while (lines.receive() != "AUTOSCAN_HOLD_1") { /* wait for exact version */ }
                }
                firmwareReady = true
                drain(); write("STATUS")
                withTimeout(5000) { while (lines.receive() != "IDLE") { } }
            }
        } catch (e: Exception) {
            watchdog.cancel(); disconnect(); throw e
        }
    }

    private fun drain() { while (lines.tryReceive().isSuccess) { } }
    private suspend fun write(command: String) = withContext(Dispatchers.IO) {
        val active = socket ?: throw IOException("Not connected")
        active.outputStream.write((command.trim() + "\n").toByteArray(Charsets.US_ASCII))
        active.outputStream.flush()
    }

    // One command at a time. An old IDLE cannot complete a new command:
    // require its BUSY and OK before accepting its IDLE.
    suspend fun move(command: String) = commandLock.withLock {
        check(connected && firmwareReady) { "Connect the updated firmware first" }
        require(command.startsWith("SEQ ") || command.startsWith("MOVE "))
        require(command.length < 255 && '\n' !in command)
        drain(); state = "Sending..."
        write(command)
        withTimeout(180000) {
            val receipt = CommandReceipt()
            while (!receipt.receive(lines.receive())) { }
        }
        delay(500) // allow physical settling after the final software stage
    }

    // Bypass the command mutex so Stop does not wait for a running sequence.
    fun abort() { scope.launch { runCatching { write("SEQ C") } } }
    fun disconnect() {
        val old = socket; socket = null
        reader?.cancel(); reader = null
        runCatching { old?.close() }
        connected = false; firmwareReady = false; state = "Disconnected"
        drain()
    }
    fun close() { disconnect(); scope.cancel() }
}
