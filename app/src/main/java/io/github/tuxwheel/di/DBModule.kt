package io.github.tuxwheel.di

import io.github.tuxwheel.data.TripDatabase
import org.koin.android.ext.koin.androidApplication
import org.koin.dsl.module

val dbModule = module {
    single { TripDatabase.getDataBase(androidApplication()).tripDao() }
}