package com.pedallog.app.modules.tracking.application.usecases

import com.pedallog.app.modules.tracking.domain.entities.RideSessionEntity
import com.pedallog.app.modules.tracking.domain.repositories.IVibrator
import com.pedallog.app.modules.tracking.domain.valueobjects.Speed
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test

class AutoPauseDetectorUseCaseTest {

    private val fakeRepository = ProcessLocationUseCaseTest.FakeSessionRepository()
    private val fakeVibrator = FakeVibrator()
    private val detector = AutoPauseDetectorUseCase(fakeRepository, fakeVibrator)

    @Test
    fun shouldTriggerAutoPauseAfterFiveSlowUpdates() = runBlocking {
        val active = RideSessionEntity.createNew(id = 55L)
        fakeRepository.activeSession = active
        detector.resetCounters()

        for (i in 1..4) {
            detector.checkAutoPause(Speed(0.3))
            assertFalse(fakeVibrator.isPauseVibrated)
        }

        detector.checkAutoPause(Speed(0.3))
        
        assertTrue(fakeVibrator.isPauseVibrated)
        assertNotNull(fakeRepository.savedSession)
        assertTrue(fakeRepository.savedSession!!.state.isPaused)
    }

    @Test
    fun shouldTriggerAutoResumeAfterUpdatesAboveThreshold() = runBlocking {
        val pausedSession = RideSessionEntity.createNew(id = 55L).pause()
        fakeRepository.activeSession = pausedSession
        detector.resetCounters()

        detector.checkAutoResume(Speed(1.5), isManuallyPaused = false)

        assertTrue(fakeVibrator.isResumeVibrated)
        assertNotNull(fakeRepository.savedSession)
        assertFalse(fakeRepository.savedSession!!.state.isPaused)
    }

    @Test
    fun shouldNotTriggerAutoResumeIfManuallyPaused() = runBlocking {
        val pausedSession = RideSessionEntity.createNew(id = 55L).pause()
        fakeRepository.activeSession = pausedSession
        detector.resetCounters()

        detector.checkAutoResume(Speed(1.5), isManuallyPaused = true)

        assertFalse(fakeVibrator.isResumeVibrated)
        assertNull(fakeRepository.savedSession)
    }

    class FakeVibrator : IVibrator {
        var isPauseVibrated = false
        var isResumeVibrated = false

        override fun vibratePause() {
            isPauseVibrated = true
        }

        override fun vibrateResume() {
            isResumeVibrated = true
        }
    }
}
