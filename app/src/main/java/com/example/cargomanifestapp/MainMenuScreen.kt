package com.example.cargomanifestapp

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * HOME baru: Retro / Neo-Brutalism.
 * Semua navigasi lama dipertahankan persis sama.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainMenuScreen(
    onNavigateToManifest: () -> Unit,
    onNavigateToStowing: () -> Unit,
    onNavigateToBuktiTimbang: () -> Unit,
    onNavigateToManifestSearch: () -> Unit,
    onNavigateToFlightTracking: () -> Unit,
    onNavigateToJarvis: () -> Unit
) {
    Scaffold(
        containerColor = CargoRetroColors.Cream,
        topBar = {
            Surface(
                color = CargoRetroColors.Blue,
                border = BorderStroke(3.dp, CargoRetroColors.Ink),
                shadowElevation = 0.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "✈ CARGO MANIFEST",
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 19.sp,
                        color = CargoRetroColors.Ink
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "OPS TERMINAL",
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = CargoRetroColors.Ink
                    )
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(CargoRetroColors.Cream)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header / identity block
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .hardShadow()
                    .border(3.dp, CargoRetroColors.Ink)
                    .background(CargoRetroColors.Paper)
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(id = R.drawable.logo_app),
                        contentDescription = "Logo Aplikasi",
                        modifier = Modifier.size(74.dp)
                    )
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(
                            text = "CARGO CONTROL",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            color = CargoRetroColors.Ink
                        )
                        Text(
                            text = "PILIH MODUL OPERASIONAL",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFF444444)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

            Text(
                text = ">> MAIN MODULES <<",
                fontSize = 15.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                color = CargoRetroColors.Ink,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
            )

            RetroMenuCard(
                title = "MANIFEST CARGO",
                subtitle = "DATA, TOTAL KG, EXPORT & CREW LOOT",
                icon = Icons.Default.List,
                color = CargoRetroColors.BlueLight,
                onClick = onNavigateToManifest
            )
            Spacer(modifier = Modifier.height(16.dp))

            RetroMenuCard(
                title = "STOWING PALET",
                subtitle = "NO PAG, CHECKLIST & DATA STOWING",
                icon = Icons.Default.ShoppingCart,
                color = CargoRetroColors.Pink,
                onClick = onNavigateToStowing
            )
            Spacer(modifier = Modifier.height(16.dp))

            RetroMenuCard(
                title = "SEARCH MANIFEST",
                subtitle = "CARI DATA BARANG DARI DATABASE EXCEL",
                icon = Icons.Default.List,
                color = CargoRetroColors.Green,
                onClick = onNavigateToManifestSearch
            )
            Spacer(modifier = Modifier.height(16.dp))

            RetroMenuCard(
                title = "BUKTI TIMBANG / BTB",
                subtitle = "TIMBANGAN, CUSTOMER & EXPORT BTB",
                icon = Icons.Default.Edit,
                color = CargoRetroColors.Cream,
                onClick = onNavigateToBuktiTimbang
            )
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = ">> SUPPORT TOOLS <<",
                fontSize = 15.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                color = CargoRetroColors.Ink,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp, bottom = 10.dp)
            )

            RetroMenuCard(
                title = "FLIGHT TRACKING",
                subtitle = "LACAK PENERBANGAN (OPSIONAL)",
                icon = Icons.Default.FlightTakeoff,
                color = CargoRetroColors.Cyan,
                onClick = onNavigateToFlightTracking
            )
            Spacer(modifier = Modifier.height(16.dp))

            RetroMenuCard(
                title = "JARVIS VOICE",
                subtitle = "KONTROL APLIKASI DENGAN SUARA",
                icon = Icons.Default.RecordVoiceOver,
                color = CargoRetroColors.Purple,
                onClick = onNavigateToJarvis
            )

            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = "CARGO MANIFEST SYSTEM • RETRO OPS UI",
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF555555)
            )
        }
    }
}

@Composable
fun RetroMenuCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .hardShadow()
            .border(3.dp, CargoRetroColors.Ink)
            .background(color)
            .clickable { onClick() }
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .border(3.dp, CargoRetroColors.Ink)
                    .background(CargoRetroColors.Paper),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = CargoRetroColors.Ink,
                    modifier = Modifier.size(29.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    color = CargoRetroColors.Ink
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = subtitle,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFF2A2A2A)
                )
            }

            Text(
                text = ">>",
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                color = CargoRetroColors.Ink
            )
        }
    }
}

/**
 * Hard shadow khas Neo-Brutalism.
 * Shadow sengaja tidak blur agar mirip referensi UI yang dikirim pengguna.
 */
fun Modifier.hardShadow(
    shadowColor: Color = CargoRetroColors.Ink,
    offset: Float = 7f
): Modifier = this.drawBehind {
    val px = offset.dp.toPx()
    drawRect(
        color = shadowColor,
        topLeft = Offset(px, px),
        size = Size(size.width, size.height)
    )
}
