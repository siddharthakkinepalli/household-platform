package com.household.app.ui.v2.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.household.app.ui.compose.navigation.Screen
import com.household.app.ui.compose.theme.EliteGlassBorder
import com.household.app.ui.compose.theme.LumeEmerald
import com.household.app.ui.compose.theme.SurfaceNavy
import com.household.app.ui.compose.theme.TextMain

@Composable
fun EliteBottomNav(
    navController: NavHostController,
    currentRoute: String?
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 20.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            modifier = Modifier
                .height(72.dp)
                .fillMaxWidth()
                .blur(0.4.dp),
            color = SurfaceNavy.copy(alpha = 0.85f),
            shape = RoundedCornerShape(40.dp),
            border = BorderStroke(1.2.dp, EliteGlassBorder),
            tonalElevation = 12.dp
        ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Screen.all.forEach { screen ->
                val selected = currentRoute == screen.route || currentRoute?.startsWith(screen.route) == true
                NavigationBarItem(
                    selected = selected,
                    onClick = {
                        navController.navigate(screen.route) {
                            popUpTo(Screen.Home.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = {
                        Box(
                            modifier = Modifier
                                .size(56.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (selected) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .blur(10.dp)
                                        .background(LumeEmerald.copy(alpha = 0.62f), CircleShape)
                                )
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .background(LumeEmerald.copy(alpha = 0.25f), CircleShape)
                                )
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .border(1.4.dp, LumeEmerald.copy(alpha = 0.95f), CircleShape)
                                )
                            }
                            Icon(
                                imageVector = screen.icon,
                                contentDescription = screen.label,
                                tint = if (selected) LumeEmerald else TextMain.copy(alpha = 0.58f),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = LumeEmerald,
                        unselectedIconColor = TextMain.copy(alpha = 0.58f),
                        indicatorColor = Color.Transparent
                    )
                )
            }
        }
    }
    }
}