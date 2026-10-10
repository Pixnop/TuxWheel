package io.github.tuxwheel.di

import io.github.tuxwheel.utils.VolumeKeyController
import org.koin.android.ext.koin.androidApplication
import org.koin.dsl.module

val volumeKeyModule = module {
    single { VolumeKeyController(androidApplication()) }
}