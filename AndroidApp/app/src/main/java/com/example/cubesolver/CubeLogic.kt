package com.example.cubesolver

import org.kociemba.twophase.Search
import kotlin.math.sqrt
import kotlin.math.pow

val faceNames = listOf("RIGHT", "LEFT", "BACK", "FRONT", "DOWN", "UP")
val colorNames = listOf("White", "Yellow", "Red", "Orange", "Blue", "Green")
val colorRgb = listOf(0xFFFFFF, 0xFFEB3B, 0xE53935, 0xFF8C00, 0x1565C0, 0x16A34A)
val stickerMaps = listOf(
    listOf(8,7,6,5,4,3,2,1,0), // RIGHT
    listOf(0,1,2,3,4,5,6,7,8), // LEFT
    listOf(2,5,8,1,4,7,0,3,6), // BACK
    listOf(6,3,0,7,4,1,8,5,2), // FRONT
    listOf(2,5,8,1,4,7,0,3,6), // DOWN
    listOf(6,3,0,7,4,1,8,5,2)  // UP
)
val scanLetters = listOf('R','L','B','F','D','U')
// Original repository's sampling orientation is 90 degrees clockwise (not 45).
val cameraRotation = listOf(6,3,0,7,4,1,8,5,2)

data class Rgb(val r: Double, val g: Double, val b: Double) {
    fun distance(v: Rgb) = sqrt((r-v.r)*(r-v.r)+(g-v.g)*(g-v.g)+(b-v.b)*(b-v.b))
}
fun medianSamples(frames: List<List<Rgb>>): List<Rgb> {
    require(frames.size >= 10 && frames.all { it.size == 9 }) { "Too few valid camera samples" }
    fun mid(v: List<Double>) = v.sorted()[v.size/2]
    return (0..8).map { i -> Rgb(mid(frames.map { it[i].r }), mid(frames.map { it[i].g }), mid(frames.map { it[i].b })) }
}
// CIE Lab separates lightness from colour; reduce lightness influence to
// tolerate moderate shadows without pretending to correct arbitrary lighting.
fun Rgb.lab(): Rgb {
    fun linear(v: Double) = if(v <= .04045) v/12.92 else ((v+.055)/1.055).pow(2.4)
    val red=linear(r); val green=linear(g); val blue=linear(b)
    fun f(v: Double) = if(v>.008856) v.pow(1.0/3.0) else 7.787*v+16.0/116.0
    val x=f((.4124*red+.3576*green+.1805*blue)/.95047)
    val y=f(.2126*red+.7152*green+.0722*blue)
    val z=f((.0193*red+.1192*green+.9505*blue)/1.08883)
    return Rgb(116*y-16,500*(x-y),200*(y-z))
}
fun labDistance(a: Rgb,b: Rgb): Double = Rgb(a.r*.35,a.g,a.b).distance(Rgb(b.r*.35,b.g,b.b))
data class Detection(val faces: List<List<Int>>, val uncertain: Set<Int>)
fun detectColors(raw: List<List<Rgb>>): Detection {
    require(raw.size == 6 && raw.all { it.size == 9 })
    val centers = raw.map { it[4].lab() }
    val reference = colorRgb.map { p -> Rgb((p shr 16 and 255)/255.0,(p shr 8 and 255)/255.0,(p and 255)/255.0).lab() }
    // Six centres only: evaluate all 720 label permutations instead of a
    // greedy assignment that can give a later centre the wrong leftover label.
    var best=Double.POSITIVE_INFINITY; var labels=emptyList<Int>()
    fun visit(order: List<Int>, cost: Double) {
        if(cost>=best) return
        if(order.size==6) { best=cost; labels=order; return }
        for(i in 0..5) if(i !in order) visit(order+i,cost+labDistance(centers[order.size],reference[i]))
    }
    visit(emptyList(),0.0)
    val uncertain=mutableSetOf<Int>()
    val assigned=raw.mapIndexed { f,face -> face.mapIndexed { t,p ->
        if(t==4) {
            if(centers.indices.any { it!=f && labDistance(centers[f],centers[it])<14 }) uncertain.add(f*9+t)
            labels[f]
        } else {
            val distances=centers.indices.map { it to labDistance(p.lab(),centers[it]) }.sortedBy { it.second }
            val first=distances[0]; val second=distances[1]
            if(first.second>25 || second.second-first.second<6 || first.second/(second.second+.001)>.75) uncertain.add(f*9+t)
            labels[first.first]
        }
    } }
    return Detection(assigned,uncertain)
}
fun classify(raw: List<List<Rgb>>) = detectColors(raw).faces
fun cubeString(faces: List<List<Int>>): String {
    require(faces.size == 6 && faces.all { it.size == 9 && it.all { c -> c in 0..5 } })
    val centers = faces.map { it[4] }
    require(centers.toSet().size == 6) { "The six centre colours must be different" }
    require((0..5).all { c -> faces.flatten().count { it == c } == 9 }) { "Each colour must occur exactly 9 times. Edit the review grids." }
    val labels = centers.zip(scanLetters).toMap()
    return listOf(5,0,3,4,1,2).joinToString("") { f -> stickerMaps[f].map { labels.getValue(faces[f][it]) }.joinToString("") }
}
fun notationToMoves(solution: String): String {
    if (solution.isBlank()) return ""
    val tokens = solution.trim().split(Regex("\\s+"))
    require(tokens.all { it.matches(Regex("[URFDLB][2']?")) }) { "Solver rejected the scan: $solution" }
    return tokens.joinToString("") { when (it.getOrNull(1)) { '2' -> "${it[0]}${it[0]}"; '\'' -> it[0].lowercase(); else -> it } }
}

fun warmUpSolver() {
    Search.solution(
        "UUUUUUUUURRRRRRRRRFFFFFFFFFDDDDDDDDDLLLLLLLLLBBBBBBBBB",
        0,
        1,
        false
    )
}
fun calculateSolution(cube: String): Pair<String,String> {
    if (cube == "UUUUUUUUURRRRRRRRRFFFFFFFFFDDDDDDDDDLLLLLLLLLBBBBBBBBB") return "" to ""
    val solution = Search.solution(cube, 21, 10, false).trim() // timeout is seconds
    val moves = notationToMoves(solution)
    require(moves.length <= 61) { "Solution exceeds Nano command capacity" }
    // Identity Rr restores the mechanism's normal orientation even if the
    // solver ended with U/D. It changes no net cube facelets.
    return solution to if (moves.isEmpty()) "" else moves + "Rr"
}

class CommandReceipt {
 private var busy = false
 private var accepted = false
 fun receive(line: String): Boolean {
  if (line.startsWith("ERR") || line.startsWith("SEQ ERR")) error(line)
  if (line == "BUSY") busy = true
  if (line == "OK") accepted = true
  return line == "IDLE" && busy && accepted
 }
}
