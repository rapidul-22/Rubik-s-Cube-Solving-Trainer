package com.example.cubesolver

private fun rotateCube(newOrientation: String, movesDelayMs: Int): String {
    val sb = StringBuilder()

    // RIGHT and LEFT grab
    sb.append("RLLL${movesDelayMs}")

    // FRONT and BACK release
    sb.append("FRBR${movesDelayMs}")

    // Rotate depending on orientation
    if (newOrientation == "INVERT") {
        // front spin right, back spin left
        sb.append("fRbL${movesDelayMs}")
    } else {
        // front spin left, back spin right
        sb.append("fLbR${movesDelayMs}")
    }

    // FRONT and BACK grab
    sb.append("FLBL${movesDelayMs}")

    // RIGHT and LEFT release
    sb.append("RRLR${movesDelayMs}")

    // front and back spin to true center
    sb.append("fCfCbCbC${movesDelayMs}")

    // RIGHT and LEFT sliders go back
    sb.append("RCLC${movesDelayMs}")

    // Relax RIGHT and LEFT sliders, they don't need to grab anymore
    sb.append("FCBC")

    return sb.toString()
}

fun faceCommand(face: Int): String {
    val delay = 350
    var cmd = "SEQ "

    if (face == 1) {    // Scan RIGHT side
        cmd += "rrrr"   // Prepare RIGHT side to be scanned (totate it 45 deg)
        cmd += "FLBL"   // Grab onto the cube from FRONT and BACK
        cmd += "${delay}"   // Wait
        cmd += "RRLR${delay}"   // Release RIGHT and LEFT side and wait
        cmd += "fLbR"   // Flip the entire cube (This will make it slide out)
        cmd += "FCBC"   // Relax the
        cmd += "lrlr" // This arm is in the way of scanning, just move it :)
    } else if (face == 2) {
        cmd += "lCFLBLRRLR${delay}fCfCbCbC${delay}FCBCFCBCRCLC${delay}rC"  // RECOVER
        cmd += "lrlr"
        cmd += "FLBL"
        cmd += "${delay}"
        cmd += "RRLR${delay}"
        cmd += "fRbL"
        cmd += "FCBC"
        cmd += "rrrr"
    } else if (face == 3) {
        cmd += "lrrCFLBLRRLR${delay}fCfCbCbC${delay}FCBCFCBCRCLC${delay}lC"  // RECOVER
        cmd += "brbr"
        cmd += "RLLL"
        cmd += "${delay}"
        cmd += "BRFR${delay}"
        cmd += "rLlR"
        cmd += "RCLC"
        cmd += "frfr"
    } else if (face == 4) {
        cmd += "brfCRLLLBRFR${delay}rCrClClC${delay}RCLCRCLCBCFC${delay}bC"  // Recover
        cmd += "frfr"
        cmd += "RLLL"
        cmd += "${delay}"
        cmd += "BRFR${delay}"
        cmd += "rRlL"
        cmd += "RCLC"
        cmd += "brbr"
    } else if (face == 5) {
        cmd += "frbCfrbCRLLLBRFR${delay}rCrClClC${delay}RCLCRCLCBCFC${delay}fC${delay}"  // Recover
        cmd += rotateCube("INVERT", delay)
        cmd += "rrrr"
        cmd += "FLBL"
        cmd += "${delay}"
        cmd += "RRLR${delay}"
        cmd += "fLbR"
        cmd += "FCBC"
        cmd += "lrlr"
    } else if (face == 6) {
        cmd += "rrlCFLBLRRLR${delay}fCfCbCbC${delay}FCBCFCBCRCLC${delay}rC"  // Recover
        cmd += "lrlr"
        cmd += "FLBL"
        cmd += "${delay}"
        cmd += "RRLR${delay}"
        cmd += "fRbL"
        cmd += "FCBC"
        cmd += "rrrr"
    } else if (face == 7 || face == 8) { // Start or End of review phase
        cmd += "lrrCFLBLRRLR${delay}fCfCbCbC${delay}FCBCFCBCRCLC${delay}lC${delay}"  // Recover from face 6
        cmd += rotateCube("NORMAL", delay)
        if (face == 7) {    // If start review, setup first face
            cmd += "rrrr"   // Prepare RIGHT side to be scanned (totate it 45 deg)
            cmd += "FLBL"   // Grab onto the cube from FRONT and BACK
            cmd += "${delay}"   // Wait
            cmd += "RRLR${delay}"   // Release RIGHT and LEFT side and wait
            cmd += "fLbR"   // Flip the entire cube (This will make it slide out)
            cmd += "FCBC"   // Relax the
            cmd += "lrlr" // This arm is in the way of scanning, just move it :)
        }
    }
    else {
        error("Invalid scan face")
    }

    return cmd + delay
}
