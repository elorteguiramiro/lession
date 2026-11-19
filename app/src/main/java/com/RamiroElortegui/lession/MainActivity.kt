package com.RamiroElortegui.lession

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.RamiroElortegui.lession.ui.navigation.MainScreen
import com.RamiroElortegui.lession.ui.theme.LessionTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LessionTheme {
                MainScreen()
            }
        }
    }
}
