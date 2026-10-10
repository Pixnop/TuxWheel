package io.github.tuxwheel.di

import io.github.tuxwheel.AppConfig
import org.koin.android.ext.koin.androidApplication
import org.koin.dsl.module

val settingModule = module {
    single { AppConfig(androidApplication()) }
}