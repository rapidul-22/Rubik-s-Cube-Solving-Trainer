package com.example.cubesolver

import org.junit.Assert.*
import org.junit.Test

class CubeLogicTest {
 @Test fun labWhiteAndAmbiguousCenters() {
  val white=Rgb(1.0,1.0,1.0).lab()
  assertEquals(100.0,white.r,.02)
  assertEquals(0.0,white.g,.02)
  assertEquals(0.0,white.b,.02)
  val detection=detectColors(List(6) { List(9) { Rgb(.4,.4,.4) } })
  assertTrue(detection.uncertain.isNotEmpty())
 }

 @Test fun staleIdleDoesNotCompleteMovement() {
  val receipt=CommandReceipt()
  assertFalse(receipt.receive("IDLE"))
  assertFalse(receipt.receive("BUSY"))
  assertFalse(receipt.receive("IDLE"))
  assertFalse(receipt.receive("OK"))
  assertTrue(receipt.receive("IDLE"))
  assertThrows(IllegalStateException::class.java) { CommandReceipt().receive("ERR busy") }
 }

 @Test fun scanOrderAndSolvedState() {
  val faces=(0..5).map { f -> List(9) { f } }
  assertEquals("UUUUUUUUURRRRRRRRRFFFFFFFFFDDDDDDDDDLLLLLLLLLBBBBBBBBB",cubeString(faces))
  stickerMaps.forEach { assertEquals((0..8).toSet(),it.toSet()) }
  assertEquals("",calculateSolution(cubeString(faces)).second)
 }
 @Test fun notationAndInvalidInput() {
  assertEquals("RuuFF",notationToMoves("R U' U' F2"))
  assertThrows(IllegalArgumentException::class.java) { notationToMoves("Error 1") }
  assertThrows(IllegalArgumentException::class.java) { cubeString(List(6) { List(9) { 0 } }) }
 }
 @Test fun scanCommandsFitNanoAndEndWithSettling() {
  val grammar=Regex("(?:[RLFBrflb][CLRrl]|[0-9]+)+")
  for(i in 1..8) {
   val c=faceCommand(i)
   assertTrue(c.startsWith("SEQ "))
   assertTrue(c.length<255)
   assertTrue(c.endsWith("350"))
   assertTrue(grammar.matches(c.removePrefix("SEQ ")))
  }
 }
 @Test fun medianRejectsOneFlashAndClassifierDoesNotHideBadCounts() {
  val frames=List(19) { List(9) { Rgb(.2,.3,.4) } } + listOf(List(9) { Rgb(1.0,1.0,1.0) })
  assertEquals(Rgb(.2,.3,.4),medianSamples(frames)[0])
  val colors=listOf(Rgb(1.0,1.0,1.0),Rgb(1.0,1.0,0.0),Rgb(1.0,0.0,0.0),Rgb(1.0,.5,0.0),Rgb(0.0,0.0,1.0),Rgb(0.0,1.0,0.0))
  val raw=colors.map { c -> MutableList(9) { c } }
  raw[0][0]=colors[1]
  val assigned=classify(raw)
  assertTrue((0..5).any { c -> assigned.flatten().count { it==c } != 9 })
 }
}
