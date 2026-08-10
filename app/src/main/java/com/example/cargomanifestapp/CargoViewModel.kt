// ==========================================
// LOGIKA EXPORT DATA KE EXCEL (REVISI BARIS & SUB TOTAL)
// ==========================================
fun exportToExcel(context: Context, awbNo: String, flightNo: String) {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            val currentList = cargoList.value
            if (currentList.isEmpty()) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Tidak ada data untuk di-export", Toast.LENGTH_SHORT).show()
                }
                return@launch
            }

            val inputStream = context.assets.open("template_manifest.xlsx")
            val workbook: Workbook = WorkbookFactory.create(inputStream)
            val sheet = workbook.getSheet("Manifest") ?: workbook.getSheetAt(0)
            inputStream.close()

            val startRow = 13 // Baris data pertama (Row 14 di Excel)
            val templateRows = 25 // Jumlah baris kosong bawaan di template (Row 14 - Row 38)
            val dataSize = currentList.size

            // Header Penerbangan
            sheet.getRow(2)?.getCell(6)?.setCellValue(awbNo)     // Row 3 Col G
            sheet.getRow(8)?.getCell(6)?.setCellValue(flightNo)  // Row 9 Col G

            // 1. JIKA DATA LEBIH BANYAK DARI TEMPLATE, SISIPKAN BARIS BARU DAHULU
            if (dataSize > templateRows) {
                val extraRowsNeeded = dataSize - templateRows
                val insertAt = startRow + templateRows
                // Geser baris di bawahnya (termasuk Total Weight & TTD) ke bawah
                sheet.shiftRows(insertAt, sheet.lastRowNum, extraRowsNeeded, true, false)
            }

            val sampleRow = sheet.getRow(startRow)

            // 2. ISI TABEL MANIFEST (KIRI: KOLOM A - G)
            currentList.forEachIndexed { index, item ->
                val targetRowIdx = startRow + index
                val row = sheet.getRow(targetRowIdx) ?: sheet.createRow(targetRowIdx)

                // Salin style border & font dari baris template
                if (sampleRow != null) {
                    for (c in 0..6) {
                        val sampleCell = sampleRow.getCell(c)
                        val cell = row.getCell(c) ?: row.createCell(c)
                        if (sampleCell?.cellStyle != null) {
                            cell.cellStyle = sampleCell.cellStyle
                        }
                    }
                }

                val pcs = parseDoubleOrZero(item.pcsQty)
                val wt = parseDoubleOrZero(item.weight)
                
                // Hitung Sub Total otomatis jika kosong
                val subTotalVal = if (item.subTotal.isNotBlank()) {
                    parseDoubleOrZero(item.subTotal)
                } else if (pcs > 0 && wt > 0) {
                    pcs * wt
                } else {
                    0.0
                }

                setNumericCell(row, 0, (index + 1).toDouble())  // Col A: No
                setTextCell(row, 1, item.pti)                    // Col B: PTI
                setNumericCell(row, 2, pcs)                      // Col C: Pcs/Cly
                
                if (wt > 0) setNumericCell(row, 3, wt) else setTextCell(row, 3, "") // Col D: Pcs/Qty Wt
                
                // Col E: Sub Total Weight (Jika ada rumus/nilai, tulis nilai hasil kalkulasinya)
                if (subTotalVal > 0) setNumericCell(row, 4, subTotalVal) else setTextCell(row, 4, "")
                
                setTextCell(row, 5, item.description)            // Col F: Description
                setTextCell(row, 6, item.customer)               // Col G: Customer
            }

            // 3. SET BARIS TOTAL WEIGHT DENGAN FORMULA DYNAMIC EXCEL
            val lastDataRowExcel = startRow + dataSize // 1-based index row terakhir
            val totalRowIdx = startRow + dataSize
            val totalRow = sheet.getRow(totalRowIdx) ?: sheet.createRow(totalRowIdx)

            setTextCell(totalRow, 1, "TOTAL WEIGHT")
            
            // Formula SUM otomatis sesuai range data (misal: =SUM(C14:C50) dan =SUM(E14:E50))
            val sumPcsFormula = "SUM(C14:C$lastDataRowExcel)"
            val sumWeightFormula = "SUM(E14:E$lastDataRowExcel)"
            
            setFormulaCell(totalRow, 2, sumPcsFormula)     // Total Pcs
            setFormulaCell(totalRow, 4, sumWeightFormula)  // Total Weight

            // 4. ISI TABEL STOWING CHECKLIST (KANAN: KOLOM H - M)
            val stowingList = currentList.filter { it.noPag.isNotBlank() }
            stowingList.forEachIndexed { index, item ->
                val targetRowIdx = startRow + index
                val row = sheet.getRow(targetRowIdx) ?: sheet.createRow(targetRowIdx)

                val pcs = parseDoubleOrZero(item.pcsQty)
                val wt = parseDoubleOrZero(item.weight)
                val subTotalVal = if (item.subTotal.isNotBlank()) parseDoubleOrZero(item.subTotal) else (pcs * wt)

                setNumericCell(row, 7, (index + 1).toDouble())            // Col H: No
                setTextCell(row, 8, item.noPag)                            // Col I: NO PAG
                setTextCell(row, 9, item.description)                      // Col J: Description
                setNumericCell(row, 10, subTotalVal)                        // Col K: Net Weight
                setFormulaCell(row, 11, "K${targetRowIdx + 1}+125")        // Col L: Gross Weight (Formula Net + Tare 125)
                setTextCell(row, 12, item.customer)                        // Col M: Customer
            }

            // Evaluasi ulang semua rumus di spreadsheet
            workbook.creationHelper.createFormulaEvaluator().evaluateAll()

            // Simpan File Output
            val file = File(context.cacheDir, "Manifest_Cargo_Output.xlsx")
            val outputStream = FileOutputStream(file)
            workbook.write(outputStream)
            outputStream.close()
            workbook.close()

            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
            withContext(Dispatchers.Main) {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(intent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Gagal Export: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }
}

// Helper Function Tambahan untuk Set Formula
private fun setFormulaCell(row: Row, col: Int, formula: String) {
    val cell = row.getCell(col) ?: row.createCell(col)
    cell.cellFormula = formula
}
