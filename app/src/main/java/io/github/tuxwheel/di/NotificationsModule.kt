package io.github.tuxwheel.di

import io.github.tuxwheel.utils.NotificationUtil
import org.koin.android.ext.koin.androidApplication
import org.koin.dsl.module

val notificationsModule = module {
    single { NotificationUtil(androidApplication()) }
}