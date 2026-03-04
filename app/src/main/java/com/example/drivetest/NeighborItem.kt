package com.example.drivetest

data class NeighborItem(
    val index: Int,
    val tech: String,    // เช่น L900, L1800, N2600 (ถ้าเดาไม่ได้ใช้ LTE/NR)
    val arfcn: String,   // EARFCN/NRARFCN หรือ "—"
    val rsrp: Int?,      // LTE: rsrp, NR: ssRsrp
    val idText: String   // "PCI: xxx" หรือ "CID: xxx" ตามที่มี
)
