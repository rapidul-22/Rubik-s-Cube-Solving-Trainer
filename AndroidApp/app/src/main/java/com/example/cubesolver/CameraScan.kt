package com.example.cubesolver

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.os.SystemClock
import androidx.camera.core.Camera
import androidx.camera.core.FocusMeteringAction
import androidx.camera.view.PreviewView
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.delay
import kotlin.math.max

class CaptureQualityError(message: String): Exception(message)
class CameraFeed {
    var preview: PreviewView? = null
    var camera: Camera? = null
    @Volatile var frameTime = 0L
    @Volatile var streaming = false
    suspend fun captureTenSeconds(corners: List<Offset>, tick: (Int) -> Unit): FaceCapture {
        // Corner order is TL, TR, BR, BL. Reject crossed/collapsed quadrilaterals.
        val edges=corners.indices.map { i -> corners[(i+1)%4]-corners[i] }
        if(corners.size!=4 || edges.indices.any { i ->
            val a=edges[i]; val b=edges[(i+1)%4]; a.x*b.y-a.y*b.x<.015f
        }) throw CaptureQualityError("Crop corners are crossed or too close")
        preview?.let { v -> runCatching {
            val center=v.meteringPointFactory.createPoint(v.width*.5f,v.height*.5f)
            camera?.cameraControl?.startFocusAndMetering(FocusMeteringAction.Builder(center).build())
        } }
        val samples = mutableListOf<List<Rgb>>()
        val spatialNoise=mutableListOf<Double>()
        var photo: Bitmap? = null
        val start = SystemClock.elapsedRealtime()
        for (step in 0 until 20) {
            val target = start + (step+1)*500L
            delay(max(0L, target-SystemClock.elapsedRealtime()))
            tick(max(0, 10-((SystemClock.elapsedRealtime()-start)/1000).toInt()))
            check(streaming && SystemClock.elapsedRealtime()-frameTime < 1500) { "Camera stopped delivering frames" }
            val bitmap = preview?.bitmap ?: error("Camera preview unavailable")
            val from=corners.flatMap { listOf(it.x*bitmap.width,it.y*bitmap.height) }.toFloatArray()
            val matrix=Matrix()
            check(matrix.setPolyToPoly(from,0,floatArrayOf(0f,0f,300f,0f,300f,300f,0f,300f),0,4)) { "Cannot map face corners" }
            val square=Bitmap.createBitmap(300,300,Bitmap.Config.ARGB_8888)
            Canvas(square).drawBitmap(bitmap,matrix,Paint(Paint.FILTER_BITMAP_FLAG))
            val rotated=Bitmap.createBitmap(square,0,0,300,300,Matrix().apply { postRotate(90f) },true)
            val colors = ArrayList<Rgb>(9)
            var worstNoise=0.0
            for (row in 0..2) for (col in 0..2) {
                val pixels=ArrayList<Rgb>(100)
                for (y in row*100+30 until row*100+70 step 4) for (x in col*100+30 until col*100+70 step 4) {
                    val p=rotated.getPixel(x,y)
                    pixels.add(Rgb((p shr 16 and 255)/255.0,(p shr 8 and 255)/255.0,(p and 255)/255.0))
                }
                fun med(values:List<Double>)=values.sorted()[values.size/2]
                val median=Rgb(med(pixels.map { it.r }),med(pixels.map { it.g }),med(pixels.map { it.b }))
                colors.add(median)
                val distances=pixels.map { it.distance(median) }.sorted()
                worstNoise=max(worstNoise,distances[distances.size*3/4])
            }
            samples.add(colors); spatialNoise.add(worstNoise)
            photo?.recycle(); photo=rotated
            if(square !== rotated) square.recycle()
            bitmap.recycle()
        }
        val median = medianSamples(samples)
        val spread = samples.map { frame -> frame.indices.maxOf { frame[it].distance(median[it]) } }.sorted()[17]
        val noise=spatialNoise.sorted()[spatialNoise.size/2]
        // Tunable engineering thresholds, not a trained or measured accuracy claim.
        val problem=when {
            median.any { maxOf(it.r,it.g,it.b)<.12 } -> "A sticker area is too dark or obstructed"
            spread>.16 -> "Colours changed too much: steady the phone/cube and lighting"
            noise>.22 -> "Sample boxes contain mixed edges, glare or an obstruction"
            else -> null
        }
        if(problem!=null) { photo?.recycle(); throw CaptureQualityError(problem) }
        return FaceCapture(median, checkNotNull(photo), spread)
    }
}
