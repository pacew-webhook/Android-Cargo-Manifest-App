package com.example.cargomanifestapp

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainMenuScreen(
    onNavigateToManifest: () -> Unit,
    onNavigateToStowing: () -> Unit,
    onNavigateToBuktiTimbang: () -> Unit,
    onNavigateToManifestSearch: () -> Unit,
    onNavigateToFlightTracking: () -> Unit
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
                    containerColor = Color(0xFF673AB7)
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
            // --- LOGO APLIKASI ---
            Image(
                painter = painterResource(id = R.drawable.logo_app),
                contentDescription = "Logo Aplikasi",
                modifier = Modifier
                    .size(150.dp)
                    .padding(bottom = 12.dp)
            )

            Text(
                text = "Pilih Menu Utama",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF333333),
                modifier = Modifier.padding(bottom = 24.dp)
            )

            MenuCard(
                title = "Data Manifest Cargo",
                subtitle = "Kelola data cargo, PTI, Pcs/Qty, & Weight",
                icon = Icons.Default.List,
                iconBackgroundColor = Color(0xFFE8DEF8),
                iconTintColor = Color(0xFF673AB7),
                onClick = onNavigateToManifest
            )

            Spacer(modifier = Modifier.height(16.dp))

            MenuCard(
                title = "Data Stowingan Palet",
                subtitle = "Kelola daftar NO PAG, Stowing Checklist, & Tare",
                icon = Icons.Default.ShoppingCart,
                iconBackgroundColor = Color(0xFFD0BCFF),
                iconTintColor = Color(0xFF381E72),
                onClick = onNavigateToStowing
            )

            Spacer(modifier = Modifier.height(16.dp))

            MenuCard(
                title = "Pencarian Database Manifest",
                subtitle = "Baca semua Excel dalam folder dan cari data barang",
                icon = Icons.Default.List,
                iconBackgroundColor = Color(0xFFE8DEF8),
                iconTintColor = Color(0xFF673AB7),
                onClick = onNavigateToManifestSearch
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 2. Kartu Menu Ketiga: Bukti Timbang Barang
            MenuCard(
                title = "Flight Tracking",
                subtitle = "Lacak penerbangan melalui Flightradar24",
                icon = Icons.Default.Flight,
                iconBackgroundColor = Color(0xFFE0F2FE),
                iconTintColor = Color(0xFF0369A1),
                onClick = onNavigateToFlightTracking
            )

            Spacer(modifier = Modifier.height(16.dp))

            MenuCard(
                title = "Bukti Timbang Barang",
                subtitle = "Kelola data timbangan, customer, & export BTB",
                icon = Icons.Default.Edit,
                iconBackgroundColor = Color(0xFFE8DEF8),
                iconTintColor = Color(0xFF673AB7),
                onClick = onNavigateToBuktiTimbang
            )
        }
    }
}

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
