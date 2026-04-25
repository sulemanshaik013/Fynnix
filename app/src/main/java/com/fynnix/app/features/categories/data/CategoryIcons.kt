package com.fynnix.app.features.categories.data

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

object CategoryIcons {

    private val map = mapOf(
        "restaurant" to Icons.Default.Restaurant,
        "car" to Icons.Default.DirectionsCar,
        "directions_car" to Icons.Default.DirectionsCar,
        "home" to Icons.Default.Home,
        "shopping" to Icons.Default.ShoppingBag,
        "shopping_bag" to Icons.Default.ShoppingBag,
        "receipt" to Icons.Default.Receipt,
        "movie" to Icons.Default.Movie,
        "medical_services" to Icons.Default.MedicalServices,
        "payments" to Icons.Default.Payments,
        "help" to Icons.Default.Help,
    )

    fun get(iconKey: String): ImageVector = map[iconKey] ?: Icons.Default.Category
}
