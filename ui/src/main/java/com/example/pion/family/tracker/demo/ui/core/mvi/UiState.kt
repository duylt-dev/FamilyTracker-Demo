package com.example.pion.family.tracker.demo.ui.core.mvi

/** Everything a screen renders, in one immutable snapshot. */
interface UiState

/** Something the user did. */
interface UiIntent

/** A one-shot instruction to the UI: navigate, show a snackbar, request a permission. */
interface UiEffect
