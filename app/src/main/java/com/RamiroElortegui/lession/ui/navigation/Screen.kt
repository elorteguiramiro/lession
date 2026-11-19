package com.RamiroElortegui.lession.ui.navigation

sealed class Screen(val route: String) {
    data object Measurement : Screen("measurement")
    data object Devices : Screen("devices")
    data object DeviceDetail : Screen("device_detail")
    data object History : Screen("history")
    data object Analysis : Screen("analysis")
    data object Detail : Screen("detail/{sessionId}") {
        fun createRoute(sessionId: Long) = "detail/$sessionId"
    }
}
