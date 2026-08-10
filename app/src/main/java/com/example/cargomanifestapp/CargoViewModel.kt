// ==========================================
// LOGIKA EXPORT DATA KE EXCEL (REVISI PERMANEN FIX DOUBLE TOTAL & SHIFT)
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

            val startRow = 13 // Indeks 13 = Row 14 di Excel
            val defaultTemplateRows = 25 // Jumlah baris kosong standar di template
            val dataSize = currentList.size

            // 1. Header Penerbangan
            sheet.getRow(2)?.getCell(6)?.setCellValue(awbNo)     // Row 3 Col G
            sheet.getRow(8)?.getCell(6)?.setCellValue(flightNo)  // Row 9 Col G

            // 2. PENANGANAN JUMLAH BARIS MANIFEST (KIRI)
            // Jika data lebih banyak dari template bawaan (25 baris), sisipkan baris HANYA jika diperlukan
            if (dataSize > defaultTemplateRows) {
                val extraRows = dataSize - defaultTemplateRows
                // Geser area footer (Total & TTD) ke bawah sesuai jumlah data ekstra
                sheet.shiftRows(startRow + defaultTemplateRows, sheet.lastRowNum, extraRows, true, false)
            } else {
                // Jika data sedikit (misal cuma 1 data), BERSIHKAN baris total lama bawaan template di Row 39 (indeks 38)
                val defaultTotalRow = sheet.getRow(38)
                if (defaultTotalRow != null && dataSize < defaultTemplateRows) {
                    // Hapus teks "TOTAL WEIGHT" lama di template agar tidak double
                    for (c in 0..6) {
                        defaultTotalRow.getCell(c)?.setCellValue("")
                    }
                }
            }

            val sampleRow = sheet.getRow(startRow)

            // 3. TULIS DATA MANIFEST CARGO (KOLOM A - G)
            currentList.forEachIndexed { index, item ->
                val targetRowIdx = startRow + index
                val row = sheet.getRow(targetRowIdx) ?: sheet.createRow(targetRowIdx)

                // Salin Style/Border dari template
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
                if (subTotalVal > 0) setNumericCell(row, 4, subTotalVal) else setTextCell(row, 4, "") // Col E: Sub Total
                
                setTextCell(row, 5, item.description)            // Col F: Description
                setTextCell(row, 6, item.customer)               // Col G: Customer
            }

            // 4. BERSIHKAN SISA BARIS KOSONG TEMPLATE DI BANTARAN MANIFEST (JIKA DATA < TEMPLATE)
            if (dataSize < defaultTemplateRows) {
                for (i in dataSize until defaultTemplateRows) {
                    val emptyRowIdx = startRow + i
                    val row = sheet.getRow(emptyRowIdx)
                    if (row != null) {
                        for (c in 0..6) {
                            row.getCell(c)?.setCellValue("")
                        }
                    }
                }
            }

            // 5. TULIS BARIS TOTAL WEIGHT MANIFEST KIRI (PERSIS DI BAWAH DATA TERAKHIR)
            val manifestTotalRowIdx = if (dataSize >= defaultTemplateRows) (startRow + dataSize) else (startRow + defaultTemplateRows)
            val manifestTotalRow = sheet.getRow(manifestTotalRowIdx) ?: sheet.createRow(manifestTotalRowIdx)
            val lastDataRowExcel = startRow + dataSize // Index 1-based row data terakhir

            setTextCell(manifestTotalRow, 1, "TOTAL WEIGHT")
            setFormulaCell(manifestTotalRow, 2, "SUM(C14:C$lastDataRowExcel)")
            setFormulaCell(manifestTotalRow, 4, "SUM(E14:E$lastDataRowExcel)")

            // 6. TULIS TABEL STOWING CHECKLIST (KANAN: KOLOM H - M) - POSISI STATIS (25 BARIS)
            val stowingList = currentList.filter { it.noPag.isNotBlank() }

            for (i in 0 until defaultTemplateRows) {
                val targetRowIdx = startRow + i
                val row = sheet.getRow(targetRowIdx) ?: sheet.createRow(targetRowIdx)

                if (i < stowingList.size) {
                    val item = stowingList[i]
                    val pcs = parseDoubleOrZero(item.pcsQty)
                    val wt = parseDoubleOrZero(item.weight)
                    val subTotalVal = if (item.subTotal.isNotBlank()) parseDoubleOrZero(item.subTotal) else (pcs * wt)

                    setNumericCell(row, 7, (i + 1).toDouble())            // Col H: No
                    setTextCell(row, 8, item.noPag)                        // Col I: NO PAG
                    setTextCell(row, 9, item.description)                  // Col J: Description
                    setNumericCell(row, 10, subTotalVal)                    // Col K: Net
                    setFormulaCell(row, 11, "K${targetRowIdx + 1}+125")    // Col L: Gross
                    setTextCell(row, 12, item.customer)                    // Col M: Customer
                } else {
                    // Bersihkan baris kosong stowing agar tidak ada angka 125 siluman
                    setTextCell(row, 7, "")
                    setTextCell(row, 8, "")
                    setTextCell(row, 9, "")
                    setTextCell(row, 10, "")
                    setTextCell(row, 11, "")
                    setTextCell(row, 12, "")
                }
            }

            // 7. TOTAL WEIGHT STOWING CHECKLIST (STATIS DI ROW 39 / INDEKS 38)
            val stowingTotalRowIdx = 38 
            val stowingTotalRow = sheet.getRow(stowingTotalRowIdx) ?: sheet.createRow(stowingTotalRowIdx)
            setFormulaCell(stowingTotalRow, 10, "SUM(K14:K38)") // Net Weight
            setFormulaCell(stowingTotalRow, 11, "SUM(L14:L38)") // Gross Weight

            // Kalkulasi ulang semua rumus Excel
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
    
