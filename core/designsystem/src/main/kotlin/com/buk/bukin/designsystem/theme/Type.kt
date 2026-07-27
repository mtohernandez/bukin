package com.buk.bukin.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.buk.bukin.designsystem.R

/*
 * Inter for body and UI, Merriweather for display only — ticket times, the success line,
 * onboarding headlines. Nothing else gets a serif; a serif on a button label is the single
 * fastest way to make this look like a template.
 *
 * Both are bundled and subset to latin + latin-ext. Not downloadable: variable fonts do not
 * work through the Google Fonts provider (issue 223262013), and the downloadable fallback
 * chain flashes on first launch — precisely the seam this design exists to remove.
 *
 * **Every style the app uses is declared here.** Before this file was rewritten, four of the
 * thirteen referenced styles were declared nowhere and silently fell back to Material
 * defaults in Roboto: `bodySmall` (12 call sites, the most-used style in the app),
 * `titleSmall` (4), `labelLarge` (4), `headlineSmall` (3). Those 23 call sites sat almost
 * entirely in the four screens the tracker described as "plain Material" — they were plain
 * Material because half their type literally was. If a style is referenced, it belongs here.
 */

/**
 * Inter, one variable file. The weight axis is instanced per entry.
 *
 * `FontVariation.Settings` is still experimental; the variable-font path itself needs API
 * 26 and minSdk is 26, so no runtime guard is required.
 */
@OptIn(ExperimentalTextApi::class)
private fun interWeight(weight: FontWeight) = Font(
    R.font.inter_variable,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

private val Inter = FontFamily(
    interWeight(FontWeight.Normal),
    interWeight(FontWeight.Medium),
    interWeight(FontWeight.SemiBold),
    interWeight(FontWeight.Bold),
)

/**
 * Merriweather ships as a static Bold rather than as a variable font.
 *
 * Deviation from spec 04, taken on measurement: the variable file subset to the same glyph
 * set is 462 KB against 187 KB for the single instance, and the type scale below never asks
 * Merriweather for a weight other than Bold. 275 KB of unreachable weights is not a
 * trade-off, it is dead payload.
 */
private val Merriweather = FontFamily(Font(R.font.merriweather_bold, FontWeight.Bold))

val BukTypography: Typography = Typography(
    // ---- Merriweather. Display only. ------------------------------------------------
    // Ticket start and end times — the largest text in the app.
    displayLarge = TextStyle(
        fontFamily = Merriweather,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.5).sp,
    ),
    // Ticket stub figures. Folds in the loose `TicketStubTime` this file used to export.
    displayMedium = TextStyle(
        fontFamily = Merriweather,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.2).sp,
    ),
    // The success line. The payoff sentence, and the only body-shaped serif in the app.
    displaySmall = TextStyle(
        fontFamily = Merriweather,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp,
    ),
    // Onboarding headlines.
    headlineLarge = TextStyle(
        fontFamily = Merriweather,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.3).sp,
    ),

    // ---- Inter. Everything else. -----------------------------------------------------
    // Screen titles.
    headlineMedium = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.4).sp,
    ),
    // Secondary screen titles, the host's live code, the Check In button label.
    headlineSmall = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.2).sp,
    ),
    // State messages: "Estamos localizando a tu anfitrión…"
    titleLarge = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Medium,
        fontSize = 20.sp,
        lineHeight = 27.sp,
        letterSpacing = (-0.1).sp,
    ),
    // Ticket course name, card titles.
    titleMedium = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 22.sp,
        letterSpacing = (-0.1).sp,
    ),
    // Names in a list row.
    titleSmall = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 23.sp,
        letterSpacing = 0.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp,
    ),
    // Subtitles and captions. The most-used style in the app.
    bodySmall = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.1.sp,
    ),
    // Button labels and row actions.
    labelLarge = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.1.sp,
    ),
    // Ticket labels: "Inicio", "Fin", the state pill.
    labelMedium = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.3.sp,
    ),
    // Footer microcopy — smallest on screen.
    labelSmall = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.3.sp,
    ),
)
