package com.example.cargomanifestapp

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ==========================================
// ENUM STATE NAVIGASI HALAMAN
// ==========================================
enum class Screen {
    MAIN_MENU,
    MANIFEST_CARGO,
    STOWING_PALLET
}

// ==========================================
// TAMPILAN MAIN MENU SCREEN
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainMenuScreen(
    onNavigateToManifest: () -> Unit,
    onNavigateToStowing: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Manifest Cargo App",
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF673AB7) // Warna Ungu Utama
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color(0xFFF5F5F5))
                .padding(20.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Pilih Menu Utama",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF333333),
                modifier = Modifier.padding(bottom = 24.dp)
            )

            // Button Card 1: Data Manifest Cargo
            MenuCard(
                title = "Data Manifest Cargo",
                subtitle = "Kelola data cargo, PTI, Pcs/Qty, & Weight",
                icon = Icons.Default.Inventory,
                iconBackgroundColor = Color(0xFFE8DEF8),
                iconTintColor = Color(0xFF673AB7),
                onClick = onNavigateToManifest
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Button Card 2: Data Stowingan Palet
            MenuCard(
                title = "Data Stowingan Palet",
                subtitle = "Kelola daftar NO PAG, Stowing Checklist, & Tare",
                icon = Icons.Default.LocalShipping,
                iconBackgroundColor = Color(0xFFD0BCFF),
                iconTintColor = Color(0xFF381E72),
                onClick = onNavigateToStowing
            )
        }
    }
}

// ==========================================
// KOMPONEN CARD MENU REUSABLE
// ==========================================
@Composable
fun MenuCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconBackgroundColor: Color,
    iconTintColor: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(iconBackgroundColor, shape = RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = iconTintColor,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1D1B20)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    fontSize = 13.sp,
                    color = Color.Gray
                )
            }
        }
    }
}
