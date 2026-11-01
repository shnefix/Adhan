package com.example.prayeralarm.ui.alarms

import com.example.prayeralarm.domain.alarm.AlarmPlanConfig
import com.example.prayeralarm.domain.alarm.FajrAlarmMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AlarmPlanViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        kotlinx.coroutines.Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        kotlinx.coroutines.Dispatchers.resetMain()
    }

    private class FakeAlarmPlanPreferences : AlarmPlanPreferences {
        override val config = MutableStateFlow(AlarmPlanConfig.DEFAULT)
        override suspend fun setConfig(config: AlarmPlanConfig) {
            this.config.value = config
        }
    }

    @Test
    fun turningOneModeOn_turnsTheOtherOff() = runTest {
        val store = FakeAlarmPlanPreferences()
        val viewModel = AlarmPlanViewModel(store)
        advanceUntilIdle()

        viewModel.setModeEnabled(FajrAlarmMode.QIYAM_AL_LAYL, true)
        advanceUntilIdle()
        assertEquals(true, viewModel.config.value.qiyamEnabled)
        assertEquals(false, viewModel.config.value.beforeFajrEnabled)

        viewModel.setModeEnabled(FajrAlarmMode.BEFORE_FAJR, true)
        advanceUntilIdle()
        assertEquals(true, viewModel.config.value.beforeFajrEnabled)
        assertEquals(false, viewModel.config.value.qiyamEnabled)
    }

    @Test
    fun invalidMinutes_showsError_andIsNotSaved() = runTest {
        val store = FakeAlarmPlanPreferences()
        val viewModel = AlarmPlanViewModel(store)
        advanceUntilIdle()

        val before = store.config.value
        viewModel.setMinutes(FajrAlarmMode.BEFORE_FAJR, 0)
        advanceUntilIdle()

        assertNotNull(viewModel.errorMessage.value)
        assertEquals(before, store.config.value)
    }

    @Test
    fun invalidCount_showsError_andIsNotSaved() = runTest {
        val store = FakeAlarmPlanPreferences()
        val viewModel = AlarmPlanViewModel(store)
        advanceUntilIdle()

        val before = store.config.value
        viewModel.setCount(FajrAlarmMode.BEFORE_FAJR, 0)
        advanceUntilIdle()

        assertNotNull(viewModel.errorMessage.value)
        assertEquals(before, store.config.value)
    }

    @Test
    fun validChanges_areSaved() = runTest {
        val store = FakeAlarmPlanPreferences()
        val viewModel = AlarmPlanViewModel(store)
        advanceUntilIdle()

        viewModel.setEnabled(true)
        advanceUntilIdle()
        viewModel.setMinutes(FajrAlarmMode.BEFORE_FAJR, 30)
        advanceUntilIdle()
        viewModel.setCount(FajrAlarmMode.BEFORE_FAJR, 3)
        advanceUntilIdle()
        viewModel.setSound("content://alarm", "My Alarm")
        advanceUntilIdle()

        assertNull(viewModel.errorMessage.value)
        assertEquals(true, viewModel.config.value.enabled)
        assertEquals(30, viewModel.config.value.beforeFajrMinutes)
        assertEquals(3, viewModel.config.value.beforeFajrCount)
        assertEquals("content://alarm", viewModel.config.value.soundUri)
        assertEquals("My Alarm", viewModel.config.value.soundName)
        assertEquals(viewModel.config.value, store.config.value)
    }
}
