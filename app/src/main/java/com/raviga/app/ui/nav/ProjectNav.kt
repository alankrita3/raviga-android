package com.raviga.app.ui.nav

import androidx.navigation.NavController

/**
 * Review #16 (Android), after R-092 #41: a project's brief, status and delivery screens go back to each other instead
 * of stacking (Status > View delivery > Status > View delivery... used to add two screens a round for Back to walk).
 * When the screen right underneath is the one [route] opens, Back to it; otherwise open it.
 */
fun NavController.openProjectScreen(route: String) {
    val previous = previousBackStackEntry
    if (Routes.sameProjectScreen(previous?.destination?.route, previous?.arguments?.getString("projectId"), route)) popBackStack()
    else navigate(route)
}
